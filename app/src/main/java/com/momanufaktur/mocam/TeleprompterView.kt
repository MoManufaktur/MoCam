package com.momanufaktur.mocam

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Teleprompter: scrollender Text, der nur auf dem Bildschirm erscheint.
 * Er liegt außerhalb der OpenGL-Pipeline und wird daher NICHT aufgezeichnet.
 *
 * Tippen = Start/Pause, Ziehen = manuell vor/zurück scrollen.
 */
class TeleprompterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var text: String = ""
        set(v) { field = v; relayout() }

    var textSizeSp: Float = 28f
        set(v) { field = v.coerceIn(14f, 72f); relayout() }

    /** Scrollgeschwindigkeit in dp pro Sekunde. */
    var speedDp: Float = 40f

    var playing: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            lastFrame = 0L
            onPlayingChanged?.invoke(v)
            invalidate()
        }

    var onPlayingChanged: ((Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val pad = 14f * density

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        textSize = 15f * density
        textAlign = Paint.Align.CENTER
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 213, 79)
        strokeWidth = 2f * density
    }

    private var layout: StaticLayout? = null
    private var offset = 0f
    private var lastFrame = 0L

    private var downY = 0f
    private var lastY = 0f
    private var moved = false

    fun reset() {
        offset = 0f
        invalidate()
    }

    private fun relayout() {
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, textSizeSp, resources.displayMetrics
        )
        val w = (width - 2 * pad).toInt()
        layout = if (w > 0 && text.isNotBlank()) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.2f)
                .build()
        } else null
        clampOffset()
        invalidate()
    }

    private fun readingLine() = height * 0.3f

    private fun maxOffset(): Float = (layout?.height ?: 0).toFloat()

    private fun clampOffset() {
        offset = offset.coerceIn(0f, maxOffset())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = layout
        if (l == null) {
            canvas.drawText("Tippe auf ✎, um deinen Text einzugeben", width / 2f, height / 2f, hintPaint)
            return
        }

        if (playing) {
            val now = SystemClock.uptimeMillis()
            if (lastFrame != 0L) {
                offset += speedDp * density * (now - lastFrame) / 1000f
            }
            lastFrame = now
            if (offset >= maxOffset()) {
                offset = maxOffset()
                playing = false
            }
        }

        val y = readingLine()
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(pad, y - offset)
        l.draw(canvas)
        canvas.restore()

        // Lesemarke
        canvas.drawLine(0f, y, 10f * density, y, guidePaint)
        canvas.drawLine(width - 10f * density, y, width.toFloat(), y, guidePaint)

        if (playing) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y
                lastY = e.y
                moved = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.y - downY) > 8f * density) moved = true
                if (moved) {
                    offset -= (e.y - lastY)
                    clampOffset()
                    invalidate()
                }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) playing = !playing
            }
        }
        return true
    }
}
