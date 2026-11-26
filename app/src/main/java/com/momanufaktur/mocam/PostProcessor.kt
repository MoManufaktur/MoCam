package com.momanufaktur.mocam

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.Executors

/**
 * Nachbearbeitung nach der Aufnahme:
 *  - #9 Untertitel erkennen und einbrennen
 *  - #10 Intro und Outro anhängen
 * Ergebnis ersetzt das Originalvideo im gleichen Ordner. Aufträge werden nacheinander abgearbeitet.
 */
class PostProcessor(private val ctx: Context) {

    data class Job(
        val input: Uri,
        val folder: String,
        val subtitles: Boolean,
        val intro: File?,
        val outro: File?,
        /** "burn" = einbrennen, "srt" = .srt-Datei, "both" = beides */
        val subMode: String = "burn",
        val subStyle: SubtitleStyle = SubtitleStyle(),
        val subLang: String = "de",
        val subEngine: String = "whisper-small",
        /** Wortliste des Projekts für die Erkennung */
        val subWords: String = "",
        /** Sprechpausen überspringen (Silero-VAD) */
        val skipPauses: Boolean = true
    )

    /** Status-Meldungen für die Anzeige (läuft auf dem Hauptthread). */
    var onStatus: ((String?) -> Unit)? = null
    /** Fertig: neue Uri (oder null bei Fehler – dann bleibt das Original). */
    var onDone: ((Uri?, String?) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private val queue = ArrayDeque<Job>()
    private var busy = false
    private var current: Job? = null
    private var currentTransformer: Transformer? = null
    @Volatile private var cancelled = false
    private var tRecognizeMs = 0L
    private var tStart = 0L

    val isBusy get() = busy

    fun enqueue(job: Job) {
        queue.addLast(job)
        if (!busy) next()
    }

    /**
     * Laufende Bearbeitung abbrechen und das Video komplett löschen (Original und Zwischenstände).
     * Muss auf dem Hauptthread aufgerufen werden.
     */
    fun cancelCurrent() {
        val job = current ?: return
        cancelled = true
        Subtitles.cancelRequested = true
        Whisper.abort()
        currentTransformer?.let { try { it.cancel() } catch (_: Exception) { } }
        currentTransformer = null
        stopProgress()
        bg.execute {
            try { ctx.contentResolver.delete(job.input, null, null) } catch (e: Exception) { Log.w(TAG, "Löschen", e) }
            main.post {
                onDone?.invoke(null, "Abgebrochen – Video gelöscht")
                finishJob()
            }
        }
    }

    private fun finishJob() {
        current = null
        currentTransformer = null
        next()
    }

    private fun next() {
        val job = queue.removeFirstOrNull()
        current = job
        cancelled = false
        Subtitles.cancelRequested = false
        if (job == null) {
            busy = false
            onStatus?.invoke(null)
            return
        }
        busy = true
        tStart = System.currentTimeMillis()
        tRecognizeMs = 0L
        if (job.subtitles) {
            onStatus?.invoke("💬 Untertitel werden erkannt …")
            bg.execute {
                val caps = try {
                    Subtitles.recognize(ctx, job.input, job.subLang, job.subEngine, job.subWords, job.skipPauses) { p ->
                        main.post { if (!cancelled) onStatus?.invoke("💬 Untertitel werden erkannt … $p %") }
                    }
                } catch (t: Throwable) {
                    if (!cancelled) Log.e(TAG, "Spracherkennung", t)
                    emptyList()
                }
                tRecognizeMs = System.currentTimeMillis() - tStart
                main.post { if (!cancelled && current === job) export(job, caps) }
            }
        } else {
            export(job, emptyList())
        }
    }

    /** Dauer der letzten Bearbeitung merken (für den Hinweis in den Untertitel-Einstellungen). */
    private fun saveTiming(job: Job, exportMs: Long) {
        val video = try { durationSec(job.input) } catch (_: Exception) { 0.0 }
        ctx.getSharedPreferences("mocam_stats", android.content.Context.MODE_PRIVATE).edit()
            .putFloat("videoSec", video.toFloat())
            .putLong("recognizeMs", tRecognizeMs)
            .putLong("exportMs", exportMs)
            .putString("engine", if (job.subtitles) job.subEngine else "")
            .apply()
    }

    private fun export(job: Job, captions: List<Caption>) {
        val burn = captions.isNotEmpty() && job.subMode != "srt"
        val srt = captions.isNotEmpty() && job.subMode != "burn"
        if (job.subtitles && captions.isEmpty()) {
            main.post { onDone?.invoke(null, "Keine Sprache erkannt – keine Untertitel") }
        }
        if (!burn && job.intro == null && job.outro == null) {
            // Nichts am Video zu ändern – ggf. nur die .srt-Datei neben das Original legen
            if (srt) bg.execute {
                val ok = try { saveSrt(displayName(job.input), job.folder, toSrt(captions)) } catch (t: Throwable) {
                    Log.e(TAG, "SRT", t); false
                }
                main.post { onDone?.invoke(null, if (ok) "Untertitel als .srt gespeichert (Download/MoCam)" else "SRT konnte nicht gespeichert werden") }
            }
            saveTiming(job, 0L)
            finishJob()
            return
        }
        onStatus?.invoke("🎬 Video wird bearbeitet …")
        val tExport = System.currentTimeMillis()

        // Größe des Hauptvideos – Intro/Outro werden darauf angepasst
        val (w, h) = videoSize(job.input)
        val fit = Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)

        val items = mutableListOf<EditedMediaItem>()
        job.intro?.let { items += EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it)))
            .setEffects(Effects(emptyList(), listOf(fit))).build() }

        val mainEffects = mutableListOf<androidx.media3.common.Effect>(fit)
        if (burn) mainEffects += OverlayEffect(listOf(CaptionOverlay(captions, job.subStyle)))
        items += EditedMediaItem.Builder(MediaItem.fromUri(job.input))
            .setEffects(Effects(emptyList(), mainEffects)).build()

        job.outro?.let { items += EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it)))
            .setEffects(Effects(emptyList(), listOf(fit))).build() }

        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build())
            .experimentalSetForceAudioTrack(true)
            .build()

        val outFile = File(ctx.cacheDir, "post_${System.currentTimeMillis()}.mp4")
        val transformer = Transformer.Builder(ctx)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgress()
                    if (cancelled) { outFile.delete(); return }
                    val exportMs = System.currentTimeMillis() - tExport
                    bg.execute {
                        saveTiming(job, exportMs)
                        val uri = try { saveToGallery(outFile, job) } catch (t: Throwable) {
                            Log.e(TAG, "Speichern", t); null
                        }
                        if (uri != null && srt) {
                            val offset = job.intro?.let { durationSec(Uri.fromFile(it)) } ?: 0.0
                            try { saveSrt(displayName(uri), job.folder, toSrt(captions, offset)) } catch (t: Throwable) {
                                Log.e(TAG, "SRT", t)
                            }
                        }
                        outFile.delete()
                        if (cancelled) {
                            // Während des Speicherns abgebrochen: neues Video auch wieder löschen
                            uri?.let { try { ctx.contentResolver.delete(it, null, null) } catch (_: Exception) { } }
                            return@execute
                        }
                        if (uri != null) {
                            try { ctx.contentResolver.delete(job.input, null, null) } catch (_: Exception) { }
                        }
                        main.post {
                            val took = if (tRecognizeMs > 0) " (Untertitel ${tRecognizeMs / 1000} s, Bearbeiten ${exportMs / 1000} s)" else ""
                            onDone?.invoke(uri, if (uri != null) "Video fertig bearbeitet$took" else "Speichern fehlgeschlagen – Original bleibt")
                            finishJob()
                        }
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    stopProgress()
                    outFile.delete()
                    if (cancelled) return
                    Log.e(TAG, "Export", exportException)
                    onDone?.invoke(null, "Bearbeitung fehlgeschlagen – Original bleibt erhalten")
                    finishJob()
                }
            })
            .build()
        currentTransformer = transformer
        transformer.start(composition, outFile.absolutePath)
        startProgress(transformer)
    }

    private var progressRunnable: Runnable? = null

    private fun startProgress(t: Transformer) {
        val holder = ProgressHolder()
        val r = object : Runnable {
            override fun run() {
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onStatus?.invoke("🎬 Video wird bearbeitet … ${holder.progress} %")
                }
                main.postDelayed(this, 500)
            }
        }
        progressRunnable = r
        main.post(r)
    }

    private fun stopProgress() {
        progressRunnable?.let { main.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun durationSec(uri: Uri): Double {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } catch (e: Exception) { 0.0 } finally { r.release() }
    }

    private fun displayName(uri: Uri): String {
        return try {
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: MediaStoreUtil.fileName("MoCam")
        } catch (e: Exception) { MediaStoreUtil.fileName("MoCam") }
    }

    /** .srt neben das Video: ab Android 10 unter Download/MoCam/<Ordner>, davor im Filme-Ordner. */
    private fun saveSrt(videoName: String, folder: String, content: String): Boolean {
        val name = videoName.substringBeforeLast('.') + ".srt"
        val sub = if (folder.isBlank()) MediaStoreUtil.FOLDER else MediaStoreUtil.FOLDER + "/" + folder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/" + sub)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            return true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), sub)
            dir.mkdirs()
            File(dir, name).writeText(content, Charsets.UTF_8)
            return true
        }
    }

    private fun videoSize(uri: Uri): Pair<Int, Int> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot % 180 != 0) h to w else w to h
        } catch (e: Exception) {
            1080 to 1920
        } finally {
            r.release()
        }
    }

    private fun saveToGallery(file: File, job: Job): Uri? {
        MediaStoreUtil.subFolder = job.folder
        val values: ContentValues = MediaStoreUtil.videoValues(MediaStoreUtil.fileName("MoCam"))
        val resolver = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                if (job.folder.isBlank()) MediaStoreUtil.FOLDER else MediaStoreUtil.FOLDER + "/" + job.folder
            )
            dir.mkdirs()
            val dst = File(dir, (values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) ?: "MoCam.mp4"))
            file.copyTo(dst, overwrite = true)
            @Suppress("DEPRECATION")
            values.put(MediaStore.MediaColumns.DATA, dst.absolutePath)
            return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    companion object {
        private const val TAG = "MoCamPost"
    }
}
