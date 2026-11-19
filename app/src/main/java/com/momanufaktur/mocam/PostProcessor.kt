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
        val outro: File?
    )

    /** Status-Meldungen für die Anzeige (läuft auf dem Hauptthread). */
    var onStatus: ((String?) -> Unit)? = null
    /** Fertig: neue Uri (oder null bei Fehler – dann bleibt das Original). */
    var onDone: ((Uri?, String?) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private val queue = ArrayDeque<Job>()
    private var busy = false

    val isBusy get() = busy

    fun enqueue(job: Job) {
        queue.addLast(job)
        if (!busy) next()
    }

    private fun next() {
        val job = queue.removeFirstOrNull()
        if (job == null) {
            busy = false
            onStatus?.invoke(null)
            return
        }
        busy = true
        if (job.subtitles) {
            onStatus?.invoke("💬 Untertitel werden erkannt …")
            bg.execute {
                val caps = try {
                    Subtitles.recognize(ctx, job.input)
                } catch (t: Throwable) {
                    Log.e(TAG, "Spracherkennung", t)
                    emptyList()
                }
                main.post { export(job, caps) }
            }
        } else {
            export(job, emptyList())
        }
    }

    private fun export(job: Job, captions: List<Caption>) {
        if (captions.isEmpty() && job.intro == null && job.outro == null) {
            if (job.subtitles) main.post { onDone?.invoke(null, "Keine Sprache erkannt – Video bleibt unverändert") }
            next()
            return
        }
        onStatus?.invoke("🎬 Video wird bearbeitet …")

        // Größe des Hauptvideos – Intro/Outro werden darauf angepasst
        val (w, h) = videoSize(job.input)
        val fit = Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)

        val items = mutableListOf<EditedMediaItem>()
        job.intro?.let { items += EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it)))
            .setEffects(Effects(emptyList(), listOf(fit))).build() }

        val mainEffects = mutableListOf<androidx.media3.common.Effect>(fit)
        if (captions.isNotEmpty()) mainEffects += OverlayEffect(listOf(CaptionOverlay(captions)))
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
                    bg.execute {
                        val uri = try { saveToGallery(outFile, job) } catch (t: Throwable) {
                            Log.e(TAG, "Speichern", t); null
                        }
                        outFile.delete()
                        if (uri != null) {
                            try { ctx.contentResolver.delete(job.input, null, null) } catch (_: Exception) { }
                        }
                        main.post {
                            onDone?.invoke(uri, if (uri != null) "Video fertig bearbeitet" else "Speichern fehlgeschlagen – Original bleibt")
                            next()
                        }
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    Log.e(TAG, "Export", exportException)
                    stopProgress()
                    outFile.delete()
                    onDone?.invoke(null, "Bearbeitung fehlgeschlagen – Original bleibt erhalten")
                    next()
                }
            })
            .build()
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
