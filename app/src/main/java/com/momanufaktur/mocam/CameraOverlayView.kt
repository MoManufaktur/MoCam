package com.momanufaktur.mocam

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Transparente Ebene über der Vorschau:
 *  - Raster (Drittel-Regel)
 *  - Fokus-Ring beim Antippen
 *  - Gesten: Tippen = Fokus, Doppeltippen = Kamera wechseln, Pinch = Zoom
 *  - Im Logo-Modus: Ziehen verschiebt das Logo, Pinch skaliert es
 */
class CameraOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Seitenverhältnis (Breite/Höhe) des aufrechten Kamerabildes in der Vorschau. */
    var contentAspect = 9f / 16f
        set(v) { field = v; invalidate() }

    var showGrid = false
        set(v) { field = v; invalidate() }

    var editLogo = false
        set(v) { field = v; invalidate() }

    /** Frontkamera: Vorschau ist gespiegelt, Aufnahme nicht. */
    var mirrorX = false
    var hasLogo = false
        set(v) { field = v; invalidate() }
    var logoAspect = 1f
        set(v) { field = v; invalidate() }

    /** Logo zusätzlich in der Ebene zeichnen (wenn die Kamera-Vorschau es nicht selbst zeigt). */
    var drawLogoPreview = false
        set(v) { field = v; invalidate() }
    /** Logo in der Vorschau anzeigen (Logo an). */
    var showLogo = false
        set(v) { field = v; invalidate() }
    var logoAlpha = 1f
        set(v) { field = v; invalidate() }
    var logoBitmap: android.graphics.Bitmap? = null
        set(v) { field = v; invalidate() }
    private val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Fingerrichtung beim Verschieben (0..7), siehe Grundeinstellungen. */
    var dragFix = 0

    var logoCx = 0.8f
    var logoCy = 0.1f
    var logoW = 0.3f

    var onLogoChanged: ((cx: Float, cy: Float, w: Float) -> Unit)? = null
    var onTap: ((x: Float, y: Float) -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onZoomScale: ((factor: Float) -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        strokeWidth = 1f * density
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 213, 79)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 213, 79) }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f * density
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }

    private var focusX = 0f
    private var focusY = 0f
    private var focusUntil = 0L

    private val tmpRect = RectF()

    fun contentRect(out: RectF = RectF()): RectF {
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return out.apply { set(0f, 0f, 0f, 0f) }
        if (vw / vh > contentAspect) {
            val cw = vh * contentAspect
            out.set((vw - cw) / 2f, 0f, (vw + cw) / 2f, vh)
        } else {
            val ch = vw / contentAspect
            out.set(0f, (vh - ch) / 2f, vw, (vh + ch) / 2f)
        }
        return out
    }

    fun showFocus(x: Float, y: Float) {
        focusX = x
        focusY = y
        focusUntil = SystemClock.uptimeMillis() + 900
        invalidate()
        postInvalidateDelayed(950)
    }

    private fun logoScreenRect(out: RectF): RectF {
        val cr = contentRect(tmpRect)
        val sx = if (mirrorX) 1f - logoCx else logoCx
        val w = logoW * cr.width()
        val h = w * logoAspect
        val cx = cr.left + sx * cr.width()
        val cy = cr.top + logoCy * cr.height()
        out.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        return out
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cr = contentRect()
        if (showGrid) {
            for (i in 1..2) {
                val x = cr.left + cr.width() * i / 3f
                val y = cr.top + cr.height() * i / 3f
                canvas.drawLine(x, cr.top, x, cr.bottom, gridPaint)
                canvas.drawLine(cr.left, y, cr.right, y, gridPaint)
            }
        }
        // Logo als Bildschirm-Ebene: genau so, wie es im Foto/Video landet
        if (showLogo && hasLogo) {
            logoBitmap?.let {
                logoPaint.alpha = (logoAlpha * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(it, null, logoScreenRect(RectF()), logoPaint)
            }
        }
        if (editLogo && !hasLogo) {
            canvas.drawText("Bitte zuerst ein Logo wählen", width / 2f, height / 3f, textPaint)
        }
        if (SystemClock.uptimeMillis() < focusUntil) {
            canvas.drawCircle(focusX, focusY, 36f * density, focusPaint)
        }
    }

    // ---------------- Gesten ----------------

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (editLogo) {
                    if (!hasLogo) return true
                    logoW = (logoW * detector.scaleFactor).coerceIn(0.05f, 1f)
                    notifyLogo()
                } else {
                    onZoomScale?.invoke(detector.scaleFactor)
                }
                return true
            }
        })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (editLogo) {
                if (hasLogo) moveLogoTo(e.x, e.y)
            } else {
                onTap?.invoke(e.x, e.y)
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!editLogo) onDoubleTap?.invoke()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!editLogo || !hasLogo || scaleDetector.isInProgress || e2.pointerCount > 1) return false
            val cr = contentRect(tmpRect)
            if (cr.width() <= 0f) return false
            var vx = -distanceX / cr.width()
            var vy = -distanceY / cr.height()
            if (dragFix >= 4) vx = -vx
            repeat(dragFix % 4) { val t = vx; vx = -vy; vy = t }
            logoCx = (logoCx + vx).coerceIn(0f, 1f)
            logoCy = (logoCy + vy).coerceIn(0f, 1f)
            notifyLogo()
            return true
        }
    })

    private fun moveLogoTo(x: Float, y: Float) {
        val cr = contentRect(tmpRect)
        if (cr.width() <= 0f) return
        var nx = ((x - cr.left) / cr.width()).coerceIn(0f, 1f)
        var ny = ((y - cr.top) / cr.height()).coerceIn(0f, 1f)
        if (dragFix >= 4) nx = 1f - nx
        repeat(dragFix % 4) { val t = nx; nx = 1f - ny; ny = t }
        logoCx = nx
        logoCy = ny
        notifyLogo()
    }

    private fun notifyLogo() {
        onLogoChanged?.invoke(logoCx, logoCy, logoW)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
}
