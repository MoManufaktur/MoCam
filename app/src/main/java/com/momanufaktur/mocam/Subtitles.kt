package com.momanufaktur.mocam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Ein Untertitel-Block mit Start/Ende in Sekunden. */
data class Caption(val start: Double, val end: Double, val text: String)

/**
 * #9 Automatische Untertitel: Offline-Spracherkennung mit Vosk (deutsches Modell, einmaliger Download).
 */
object Subtitles {

    /** Sprachen: "de" Deutsch, "en-gb" Englisch (UK), "en-us" Englisch (US). */
    val LANGUAGES = listOf("de" to "Deutsch", "en-gb" to "English (UK)", "en-us" to "English (US)")

    /** Wird beim Abbrechen gesetzt; die Erkennung bricht dann beim nächsten Audio-Stück ab. */
    @Volatile var cancelRequested = false

    /** Erkennungs-Engines: Whisper (genauer, mit Satzzeichen) oder das alte Vosk. */
    val ENGINES = listOf(
        "whisper-base" to "⚡ Whisper Schnell",
        "whisper-small" to "🎯 Whisper Genau",
        "vosk" to "Vosk (alt)"
    )

    private fun isWhisper(engine: String) = engine.startsWith("whisper")

    private fun whisperFile(ctx: Context, engine: String) =
        File(ctx.filesDir, if (engine == "whisper-base") "ggml-base-q5_1.bin" else "ggml-small-q5_1.bin")

    private fun whisperUrl(engine: String) = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" +
        if (engine == "whisper-base") "ggml-base-q5_1.bin" else "ggml-small-q5_1.bin"

    /** Silero-VAD (ca. 1 MB): erkennt Sprechpausen, damit Whisper Stille überspringt. */
    private const val VAD_URL = "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v6.2.0.bin"
    fun vadFile(ctx: Context) = File(ctx.filesDir, "ggml-silero-v6.2.0.bin")

    /** Pausen-Modell laden, falls es fehlt (still, ohne Fehlermeldung – ohne Modell geht es auch). */
    fun ensureVad(ctx: Context): Boolean {
        val f = vadFile(ctx)
        if (f.exists()) return true
        return try { download(VAD_URL, f, 200_000L) {}; true } catch (e: Exception) { false }
    }

    private fun download(url: String, target: File, minBytes: Long, onProgress: (Int) -> Unit) {
        val tmp = File(target.path + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(128 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    done += r
                    if (total > 0) {
                        val pr = (done * 100 / total).toInt()
                        if (pr != last) { last = pr; onProgress(pr) }
                    }
                }
            }
        }
        if (tmp.length() < minBytes) {
            tmp.delete()
            throw IllegalStateException("Download unvollständig")
        }
        target.delete()
        tmp.renameTo(target)
    }

    fun modelSizeText(lang: String, engine: String) = when (engine) {
        "whisper-base" -> "ca. 57 MB"
        "whisper-small" -> "ca. 181 MB"
        else -> modelSizeText(lang)
    }

    fun isModelReady(ctx: Context, lang: String, engine: String) =
        if (isWhisper(engine)) whisperFile(ctx, engine).exists() && Whisper.available else isModelReady(ctx, lang)

    /** Whisper ist mehrsprachig: ein Modell für Deutsch und Englisch. */
    fun downloadModel(ctx: Context, lang: String, engine: String, onProgress: (Int) -> Unit) {
        if (!isWhisper(engine)) return downloadModel(ctx, lang, onProgress)
        download(whisperUrl(engine), whisperFile(ctx, engine), 10_000_000L, onProgress)
        ensureVad(ctx)
        onProgress(100)
    }

    /** Bekannte Whisper-„Halluzinationen“ bei Stille oder Musik (aus Untertitel-Trainingsdaten). */
    private val HALLUCINATIONS = listOf(
        "untertitel im auftrag", "untertitelung", "untertitel von", "amara.org", "copyright wdr", "swr 20",
        "vielen dank fürs zuschauen", "vielen dank für's zuschauen", "thanks for watching", "subtitles by",
        "untertitel der amara"
    )

    /**
     * Erkennt die Sprache im Video.
     * @param prompt Wortliste des Projekts (Namen, Fachbegriffe) – hilft Whisper bei der Schreibweise.
     */
    fun recognize(ctx: Context, video: Uri, lang: String, engine: String, prompt: String?,
                  skipPauses: Boolean = true, onProgress: (Int) -> Unit = {}): List<Caption> {
        if (!isWhisper(engine)) return recognize(ctx, video, lang)
        val model = whisperFile(ctx, engine)
        if (!model.exists()) return emptyList()
        // Ton als 16-kHz-Mono-PCM einsammeln und in Gleitkommazahlen umwandeln
        val raw = java.io.ByteArrayOutputStream()
        val hasAudio = AudioPcm.decode(ctx, video) { bytes, len ->
            if (cancelRequested) throw java.util.concurrent.CancellationException()
            if (cancelRequested) throw java.util.concurrent.CancellationException()
            raw.write(bytes, 0, len)
        }
        if (!hasAudio || raw.size() < 3200) return emptyList()
        val b = raw.toByteArray()
        val pcm = FloatArray(b.size / 2) { i ->
            ((b[2 * i].toInt() and 0xff) or (b[2 * i + 1].toInt() shl 8)).toShort() / 32768f
        }
        val words = prompt?.split(',', ';', '\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val initial = when {
            words.isEmpty() -> null
            lang.startsWith("en") -> "Glossary: " + words.joinToString(", ") + "."
            else -> "Begriffe: " + words.joinToString(", ") + "."
        }
        if (cancelRequested) throw java.util.concurrent.CancellationException()
        val segs = Whisper.transcribe(model.absolutePath, pcm, if (lang.startsWith("en")) "en" else "de", initial,
            threads = 4, beam = 1,
            vadPath = if (skipPauses && ensureVad(ctx)) vadFile(ctx).absolutePath else null,
            progress = { p -> onProgress(p) })
        if (cancelRequested) throw java.util.concurrent.CancellationException()
        val caps = segs
            .filter { s -> HALLUCINATIONS.none { s.text.lowercase().contains(it) } }
            .filter { s -> s.text.any { it.isLetterOrDigit() } }
            .map { Caption(it.start, maxOf(it.end, it.start + 0.6), it.text) }
        val fixed = caps.mapIndexed { i, c ->
            val next = caps.getOrNull(i + 1)?.start ?: Double.MAX_VALUE
            c.copy(end = minOf(c.end + 0.3, next))
        }
        return if (lang.startsWith("en")) fixed.map { c ->
            c.copy(text = englishFix(c.text, british = lang == "en-gb"))
        } else fixed
    }

    /** Für UK und US gibt es offline nur ein (US-)Modell – UK bekommt britische Schreibweise. */
    private fun modelKey(lang: String) = if (lang.startsWith("en")) "en" else "de"

    private fun modelUrl(lang: String) = if (modelKey(lang) == "en")
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    else "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"

    fun modelSizeText(lang: String) = if (modelKey(lang) == "en") "ca. 40 MB" else "ca. 45 MB"

    fun modelDir(ctx: Context, lang: String) = File(ctx.filesDir, "vosk-" + modelKey(lang))

    /** Pfad des entpackten Modells (Ordner mit „am“, „conf“ …) oder null. */
    fun modelPath(ctx: Context, lang: String): File? {
        val root = modelDir(ctx, lang)
        if (!root.exists()) return null
        if (File(root, "conf").exists()) return root
        return root.listFiles()?.firstOrNull { File(it, "conf").exists() }
    }

    fun isModelReady(ctx: Context, lang: String) = modelPath(ctx, lang) != null

    /** Lädt das Sprachmodell herunter und entpackt es. Läuft im Hintergrund-Thread. */
    fun downloadModel(ctx: Context, lang: String, onProgress: (Int) -> Unit) {
        val tmp = File(ctx.cacheDir, "vosk-${modelKey(lang)}.zip")
        val conn = URL(modelUrl(lang)).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    done += r
                    if (total > 0) {
                        val p = (done * 90 / total).toInt()
                        if (p != last) { last = p; onProgress(p) }
                    }
                }
            }
        }
        val root = modelDir(ctx, lang)
        root.deleteRecursively()
        root.mkdirs()
        ZipInputStream(tmp.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val f = File(root, e.name)
                if (!f.canonicalPath.startsWith(root.canonicalPath)) continue
                if (e.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        tmp.delete()
        onProgress(100)
    }

    /** Erkennt die Sprache im Video und bildet daraus Untertitel-Blöcke. */
    fun recognize(ctx: Context, video: Uri, lang: String): List<Caption> {
        val path = modelPath(ctx, lang) ?: return emptyList()
        val model = Model(path.absolutePath)
        val rec = Recognizer(model, 16000f)
        rec.setWords(true)
        val words = mutableListOf<Triple<Double, Double, String>>()

        fun collect(json: String) {
            val arr = JSONObject(json).optJSONArray("result") ?: return
            for (i in 0 until arr.length()) {
                val w = arr.getJSONObject(i)
                words += Triple(w.getDouble("start"), w.getDouble("end"), w.getString("word"))
            }
        }

        val hasAudio = AudioPcm.decode(ctx, video) { bytes, len ->
            if (rec.acceptWaveForm(bytes, len)) collect(rec.result)
        }
        if (hasAudio) collect(rec.finalResult)
        rec.close()
        model.close()
        val caps = group(words)
        return if (lang.startsWith("en")) caps.map { c ->
            c.copy(text = englishFix(c.text, british = lang == "en-gb"))
        } else caps
    }

    private val UK = mapOf(
        "color" to "colour", "colors" to "colours", "colored" to "coloured", "favorite" to "favourite",
        "favorites" to "favourites", "favor" to "favour", "honor" to "honour", "labor" to "labour",
        "neighbor" to "neighbour", "neighbors" to "neighbours", "behavior" to "behaviour", "flavor" to "flavour",
        "humor" to "humour", "rumor" to "rumour", "harbor" to "harbour", "center" to "centre", "centers" to "centres",
        "theater" to "theatre", "fiber" to "fibre", "liter" to "litre", "liters" to "litres", "meters" to "metres",
        "kilometer" to "kilometre", "kilometers" to "kilometres", "gray" to "grey", "mom" to "mum",
        "traveling" to "travelling", "traveled" to "travelled", "traveler" to "traveller", "canceled" to "cancelled",
        "canceling" to "cancelling", "modeling" to "modelling", "labeled" to "labelled", "fueled" to "fuelled",
        "jewelry" to "jewellery", "catalog" to "catalogue", "dialog" to "dialogue", "analog" to "analogue",
        "defense" to "defence", "offense" to "offence", "license" to "licence", "practiced" to "practised",
        "aluminum" to "aluminium"
    )
    private val IZE_KEEP = setOf("size", "sizes", "prize", "prizes", "seize", "seized", "capsize", "maize", "baize")

    /** Englisch: „i“ → „I“; bei UK britische Schreibweise (colour, centre, organise …). */
    private fun englishFix(text: String, british: Boolean): String =
        text.split(" ").joinToString(" ") { w ->
            var x = if (w == "i") "I" else if (w.startsWith("i'")) "I" + w.substring(1) else w
            if (british) {
                UK[x]?.let { x = it } ?: run {
                    if (x !in IZE_KEEP && x.length > 5) {
                        x = x.replace(Regex("(i)z(e|ed|es|ing|ation|ations)$"), "$1s$2")
                            .replace(Regex("(ly)z(e|ed|es|ing)$"), "$1s$2")
                    }
                }
            }
            x
        }

    /** Wörter zu gut lesbaren Blöcken zusammenfassen (max. ~6 Wörter / 3 s). */
    private fun group(words: List<Triple<Double, Double, String>>): List<Caption> {
        val out = mutableListOf<Caption>()
        var cur = mutableListOf<Triple<Double, Double, String>>()
        fun flush() {
            if (cur.isEmpty()) return
            out += Caption(cur.first().first, cur.last().second, cur.joinToString(" ") { it.third })
            cur = mutableListOf()
        }
        for (w in words) {
            val last = cur.lastOrNull()
            if (last != null && (cur.size >= 6 || w.second - cur.first().first > 3.0 || w.first - last.second > 0.7)) flush()
            cur.add(w)
        }
        flush()
        // Satzanfang groß, Anzeige etwas länger stehen lassen (aber nicht über den nächsten Block)
        return out.mapIndexed { i, c ->
            val next = out.getOrNull(i + 1)?.start ?: Double.MAX_VALUE
            c.copy(end = minOf(c.end + 0.4, next), text = c.text.replaceFirstChar { it.uppercase() })
        }
    }
}

/** Gestaltung der Untertitel: Position (Mitte, Anteil der Bildhöhe) und Schriftgröße (Anteil der Bildbreite). */
data class SubtitleStyle(val posY: Float = 0.80f, val size: Float = 0.058f, val bgAlpha: Float = 0.6f)

/** Zeichnet einen Untertitel in ein Rechteck (Bild bzw. Vorschau-Ausschnitt). Für Video und Vorschau gleich. */
fun drawCaption(c: Canvas, text: String, left: Float, top: Float, width: Float, height: Float, st: SubtitleStyle) {
    if (text.isBlank()) return
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = width * st.size
        isFakeBoldText = true
    }
    val maxW = (width * 0.86f).toInt().coerceAtLeast(10)
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, 1.1f)
        .build()
    val pad = paint.textSize * 0.35f
    val y = top + height * st.posY - layout.height / 2f
    var textW = 0f
    for (i in 0 until layout.lineCount) textW = maxOf(textW, layout.getLineWidth(i))
    val bgLeft = left + (width - textW) / 2f - pad
    c.drawRoundRect(
        RectF(bgLeft, y - pad, left + width - (bgLeft - left), y + layout.height + pad), pad, pad,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((st.bgAlpha * 255).toInt().coerceIn(0, 255), 0, 0, 0) }
    )
    c.save()
    c.translate(left + (width - maxW) / 2f, y)
    layout.draw(c)
    c.restore()
}

/** SRT-Text aus Untertitel-Blöcken (optional um ein Intro verschoben). */
fun toSrt(captions: List<Caption>, offsetSec: Double = 0.0): String {
    fun ts(t: Double): String {
        val ms = ((t + offsetSec) * 1000).toLong().coerceAtLeast(0)
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d,%03d",
            ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000)
    }
    val sb = StringBuilder()
    captions.forEachIndexed { i, c ->
        sb.append(i + 1).append('\n')
            .append(ts(c.start)).append(" --> ").append(ts(c.end)).append('\n')
            .append(c.text).append("\n\n")
    }
    return sb.toString()
}

/**
 * Zeichnet den jeweils aktuellen Untertitel als bildfüllende Ebene (transparent).
 * Die Zeit wird relativ zum ersten gesehenen Bild gemessen – so passt es auch, wenn ein Intro davor hängt.
 */
class CaptionOverlay(private val captions: List<Caption>, private val style: SubtitleStyle) : BitmapOverlay() {

    private var width = 1080
    private var height = 1920
    private var firstUs = -1L
    private var lastIndex = -2
    private var cached: Bitmap? = null
    private var empty: Bitmap? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        width = videoSize.width
        height = videoSize.height
        cached = null
        empty = null
        lastIndex = -2
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (firstUs < 0) firstUs = presentationTimeUs
        val t = (presentationTimeUs - firstUs) / 1_000_000.0
        val idx = captions.indexOfFirst { t >= it.start && t <= it.end }
        if (idx == lastIndex) cached?.let { return it }
        lastIndex = idx
        val bmp = if (idx < 0) emptyBitmap() else render(captions[idx].text)
        cached = bmp
        return bmp
    }

    private fun emptyBitmap(): Bitmap =
        empty ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { empty = it }

    private fun render(text: String): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawCaption(Canvas(bmp), text, 0f, 0f, width.toFloat(), height.toFloat(), style)
        return bmp
    }
}
