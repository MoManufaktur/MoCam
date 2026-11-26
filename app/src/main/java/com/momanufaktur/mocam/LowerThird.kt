package com.momanufaktur.mocam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import kotlin.math.max
import kotlin.math.roundToInt

/** Gestaltung der Bauchbinde. Größen/Positionen relativ zur Bildbreite bzw. -höhe. */
data class LowerThirdStyle(
    /** linker Rand (Anteil der Bildbreite) */
    val x: Float = 0.04f,
    /** Unterkante (Anteil der Bildhöhe) */
    val y: Float = 0.86f,
    /** Breite des Balkens (Anteil der Bildbreite) */
    val width: Float = 0.80f,
    /** Schriftgrößen (Anteil der Bildbreite) */
    val nameSize: Float = 0.065f,
    val titleSize: Float = 0.042f,
    val font: Int = 0,
    val bgColor: Int = Color.rgb(17, 17, 17),
    val bgAlpha: Float = 0.82f,
    val accentColor: Int = Color.rgb(255, 213, 79),
    val nameColor: Int = Color.WHITE,
    val titleColor: Int = Color.rgb(255, 213, 79)
)

/**
 * #6 Bauchbinde: Name und Funktion als Balken, am Videoanfang für einige Sekunden eingeblendet
 * (im Video eingebrannt, in der Vorschau als Ebene).
 */
object LowerThird {

    private const val FADE_MS = 400f
    /** Referenzbreite: Bitmap-Pixel pro voller Bildbreite. */
    private const val REF = 1000f

    /** Wählbare Schriftarten (Name, Schrift). */
    val FONTS: List<Pair<String, Typeface>> = listOf(
        "Standard" to Typeface.create("sans-serif", Typeface.NORMAL),
        "Schmal" to Typeface.create("sans-serif-condensed", Typeface.NORMAL),
        "Leicht" to Typeface.create("sans-serif-light", Typeface.NORMAL),
        "Kräftig" to Typeface.create("sans-serif-black", Typeface.NORMAL),
        "Serif" to Typeface.SERIF,
        "Mono" to Typeface.MONOSPACE,
        "Locker" to Typeface.create("casual", Typeface.NORMAL),
        "Schreibschrift" to Typeface.create("cursive", Typeface.NORMAL)
    )

    /** Farbpalette für Hintergrund, Zierstreifen und Schrift. */
    val COLORS: List<Pair<String, Int>> = listOf(
        "Schwarz" to Color.rgb(17, 17, 17),
        "Anthrazit" to Color.rgb(55, 61, 66),
        "Weiß" to Color.WHITE,
        "Gelb" to Color.rgb(255, 213, 79),
        "Orange" to Color.rgb(251, 140, 0),
        "Rot" to Color.rgb(229, 57, 53),
        "Grün" to Color.rgb(67, 160, 71),
        "Petrol" to Color.rgb(0, 137, 123),
        "Blau" to Color.rgb(30, 136, 229),
        "Lila" to Color.rgb(142, 36, 170)
    )

    fun render(name: String, title: String, st: LowerThirdStyle): Bitmap? {
        if (name.isBlank() && title.isBlank()) return null
        val base = FONTS.getOrElse(st.font) { FONTS[0] }.second
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = st.nameColor
            textSize = st.nameSize * REF
            typeface = Typeface.create(base, Typeface.BOLD)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = st.titleColor
            textSize = st.titleSize * REF
            typeface = base
        }
        val w = max(120, (st.width * REF).roundToInt())
        val stripe = REF * 0.012f
        val padX = stripe + REF * 0.03f
        val padY = REF * 0.022f
        // Schrift verkleinern, falls der Text nicht in den Balken passt
        val avail = w - padX - REF * 0.03f
        fun fit(p: Paint, t: String) {
            if (t.isBlank()) return
            val tw = p.measureText(t)
            if (tw > avail) p.textSize *= avail / tw
        }
        fit(namePaint, name)
        fit(titlePaint, title)

        val nameH = if (name.isBlank()) 0f else namePaint.textSize * 1.15f
        val titleH = if (title.isBlank()) 0f else titlePaint.textSize * 1.25f
        val h = max(40, (padY * 2 + nameH + titleH).roundToInt())

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Color.argb((st.bgAlpha * 255).roundToInt().coerceIn(0, 255),
            Color.red(st.bgColor), Color.green(st.bgColor), Color.blue(st.bgColor))
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), REF * 0.02f, REF * 0.02f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        c.drawRect(0f, 0f, stripe, h.toFloat(), Paint().apply { color = st.accentColor })
        var yy = padY
        if (name.isNotBlank()) {
            yy += namePaint.textSize
            c.drawText(name, padX, yy, namePaint)
            yy += namePaint.textSize * 0.15f
        }
        if (title.isNotBlank()) {
            yy += titlePaint.textSize * 1.05f
            c.drawText(title, padX, yy, titlePaint)
        }
        return bmp
    }

    /** Deckkraft zum Zeitpunkt jetzt (0 = unsichtbar), mit Ein- und Ausblenden. */
    fun alphaNow(startMs: Long, durationMs: Long): Float {
        if (startMs <= 0L) return 0f
        val t = (SystemClock.uptimeMillis() - startMs).toFloat()
        if (t < 0f || t > durationMs) return 0f
        val fadeIn = (t / FADE_MS).coerceIn(0f, 1f)
        val fadeOut = ((durationMs - t) / FADE_MS).coerceIn(0f, 1f)
        return minOf(fadeIn, fadeOut)
    }
}


/**
 * Zusätzliche Einblendungen der Bauchbinde in der Nachbearbeitung (wenn die Videolänge erst nach
 * der Aufnahme feststeht). Zeichnet die Bauchbinde zu den angegebenen Zeiten mit Ein-/Ausblenden.
 */
class LowerThirdOverlay(
    private val bmp: Bitmap,
    private val st: LowerThirdStyle,
    private val startsSec: List<Double>,
    private val durSec: Double
) : BitmapOverlay() {
    private var width = 1080
    private var height = 1920
    private var firstUs = -1L
    private val cache = HashMap<Int, Bitmap>()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        width = videoSize.width
        height = videoSize.height
        cache.clear()
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (firstUs < 0) firstUs = presentationTimeUs
        val t = (presentationTimeUs - firstUs) / 1_000_000.0
        var a = 0.0
        val fade = 0.4
        for (s in startsSec) {
            val dt = t - s
            if (dt < 0 || dt > durSec) continue
            a = maxOf(a, minOf(1.0, dt / fade, (durSec - dt) / fade))
        }
        val level = (a * 10).roundToInt().coerceIn(0, 10)  // 11 Stufen reichen für weiches Blenden
        return cache.getOrPut(level) { render(level / 10f) }
    }

    private fun render(alpha: Float): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (alpha <= 0f) return out
        val w = st.width * width
        val h = w * bmp.height / bmp.width
        val left = st.x * width
        val bottom = st.y * height
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (alpha * 255).toInt() }
        Canvas(out).drawBitmap(bmp, null, RectF(left, bottom - h, left + w, bottom), p)
        return out
    }
}
