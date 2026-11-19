package com.momanufaktur.mocam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import kotlin.math.max

/**
 * #6 Bauchbinde: Name und Funktion als Balken unten links, wird am Videoanfang
 * für einige Sekunden eingeblendet (im Video eingebrannt, in der Vorschau als Ebene).
 */
object LowerThird {

    /** Lage im Bild (relativ): linker Rand, Unterkante und Breite. */
    const val LEFT = 0.04f
    const val BOTTOM = 0.86f
    const val MAX_WIDTH = 0.80f

    private const val FADE_MS = 400f

    fun render(name: String, title: String, accent: Int = Color.rgb(255, 213, 79)): Bitmap? {
        if (name.isBlank() && title.isBlank()) return null
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 62f
        }
        val textW = max(namePaint.measureText(name), titlePaint.measureText(title))
        val w = max(600, (textW + 110).toInt())
        val h = if (title.isBlank() || name.isBlank()) 190 else 290
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 26f, 26f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 17, 17, 17) })
        c.drawRect(0f, 0f, 18f, h.toFloat(), Paint().apply { color = accent })
        if (name.isNotBlank() && title.isNotBlank()) {
            c.drawText(name, 60f, 125f, namePaint)
            c.drawText(title, 60f, 230f, titlePaint)
        } else if (name.isNotBlank()) {
            c.drawText(name, 60f, 128f, namePaint)
        } else {
            c.drawText(title, 60f, 118f, titlePaint)
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
