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

    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
    private const val MODEL_DIR = "vosk-de"

    fun modelDir(ctx: Context) = File(ctx.filesDir, MODEL_DIR)

    /** Pfad des entpackten Modells (Ordner mit „am“, „conf“ …) oder null. */
    fun modelPath(ctx: Context): File? {
        val root = modelDir(ctx)
        if (!root.exists()) return null
        if (File(root, "conf").exists()) return root
        return root.listFiles()?.firstOrNull { File(it, "conf").exists() }
    }

    fun isModelReady(ctx: Context) = modelPath(ctx) != null

    /** Lädt das Sprachmodell (~45 MB) herunter und entpackt es. Läuft im Hintergrund-Thread. */
    fun downloadModel(ctx: Context, onProgress: (Int) -> Unit) {
        val tmp = File(ctx.cacheDir, "vosk-de.zip")
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
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
        val root = modelDir(ctx)
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
    fun recognize(ctx: Context, video: Uri): List<Caption> {
        val path = modelPath(ctx) ?: return emptyList()
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
        return group(words)
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
