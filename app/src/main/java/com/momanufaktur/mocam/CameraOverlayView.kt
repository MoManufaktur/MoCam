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
    /** #6 Bauchbinde in der Vorschau (gleiche Lage wie im Video). */
    var lowerBitmap: android.graphics.Bitmap? = null
        set(v) { field = v; invalidate() }
    var lowerStartMs = 0L
        set(v) { field = v; invalidate() }
    var lowerDurationMs = 5000L
    var lowerStyle = LowerThirdStyle()
        set(v) { field = v; invalidate() }
    /** Bauchbinde dauerhaft zeigen (während sie eingestellt wird). */
    var lowerPinned = false
        set(v) { field = v; invalidate() }
    /** #9 Untertitel-Vorschau (während sie eingestellt werden). */
    var subPreviewText: String? = null
        set(v) { field = v; invalidate() }
    var subStyle = SubtitleStyle()
        set(v) { field = v; invalidate() }

    /** Logo in der Vorschau anzeigen (Logo an). */
    var showLogo = false
        set(v) { field = v; invalidate() }
    var logoAlpha = 1f
        set(v) { field = v; invalidate() }
    var logoBitmap: android.graphics.Bitmap? = null
        set(v) { field = v; invalidate() }
    private val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val lowerPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Fingerrichtung beim Verschieben (0..7), siehe Grundeinstellungen. */
    var dragFix = 0

    var logoCx = 0.8f
    var logoCy = 0.1f
    var logoW = 0.3f

    var onLogoChanged: ((cx: Float, cy: Float, w: Float) -> Unit)? = null
    var onTap: ((x: Float, y: Float) -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    /** #4 Lange drücken = AE/AF-Sperre */
    var onLongPress: ((x: Float, y: Float) -> Unit)? = null

    /** #4 Anzeige der Sperre */
    var lockText: String? = null
        set(v) { field = v; invalidate() }
    private var lockX = 0f
    private var lockY = 0f
    fun showLock(x: Float, y: Float) { lockX = x; lockY = y; lockText = "🔒 AE/AF"; }

    /** #6 Histogramm (64 Werte, 0..1) */
    var histogram: FloatArray? = null
        set(v) { field = v; invalidate() }

    /** #7 Wasserwaage: Neigung in Grad (null = aus) */
    var levelAngle: Float? = null
        set(v) { field = v; invalidate() }

    private val histoBg = Paint().apply { color = Color.argb(120, 0, 0, 0) }
    private val histoPaint = Paint().apply { color = Color.argb(220, 255, 255, 255) }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f * resources.displayMetrics.density }
    private val levelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
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
        lowerBitmap?.let { lb ->
            val a = if (lowerPinned) 1f else LowerThird.alphaNow(lowerStartMs, lowerDurationMs)
            if (a > 0f) {
                val cr = contentRect(tmpRect)
                val w = lowerStyle.width * cr.width()
                val h = w * lb.height / lb.width
                val left = cr.left + lowerStyle.x * cr.width()
                val bottom = cr.top + lowerStyle.y * cr.height()
                lowerPaint.alpha = (a * 255).toInt()
                canvas.drawBitmap(lb, null, RectF(left, bottom - h, left + w, bottom), lowerPaint)
                if (!lowerPinned) postInvalidateOnAnimation()
            }
        }
        subPreviewText?.let { t ->
            val cr = contentRect(tmpRect)
            drawCaption(canvas, t, cr.left, cr.top, cr.width(), cr.height(), subStyle)
        }
        // #6 Histogramm oben links im Bild
        histogram?.let { h ->
            val cr = contentRect(tmpRect)
            val d = resources.displayMetrics.density
            val w = 130 * d
            val hh = 60 * d
            val l = cr.left + 8 * d
            val t = cr.top + 8 * d
            canvas.drawRect(l, t, l + w, t + hh, histoBg)
            val bw = w / h.size
            for (i in h.indices) {
                val bh = h[i] * (hh - 4 * d)
                canvas.drawRect(l + i * bw, t + hh - 2 * d - bh, l + (i + 1) * bw - 0.5f, t + hh - 2 * d, histoPaint)
            }
        }
        // #7 Wasserwaage in der Bildmitte
        levelAngle?.let { a ->
            val cx = width / 2f
            val cy = height / 2f
            val len = width * 0.32f
            val ok = kotlin.math.abs(a) < 1f
            levelPaint.color = if (ok) Color.rgb(76, 175, 80) else Color.WHITE
            canvas.save()
            canvas.rotate(-a, cx, cy)
            canvas.drawLine(cx - len, cy, cx - 18 * density, cy, levelPaint)
            canvas.drawLine(cx + 18 * density, cy, cx + len, cy, levelPaint)
            canvas.restore()
            levelPaint.color = Color.argb(140, 255, 255, 255)
            canvas.drawLine(cx - len - 14 * density, cy, cx - len - 4 * density, cy, levelPaint)
            canvas.drawLine(cx + len + 4 * density, cy, cx + len + 14 * density, cy, levelPaint)
            levelText.color = if (ok) Color.rgb(76, 175, 80) else Color.WHITE
            canvas.drawText(String.format("%.1f°", a), cx, cy - 12 * density, levelText)
        }
        // #4 Sperre
        lockText?.let { t ->
            canvas.drawCircle(lockX, lockY, 36f * density, focusPaint)
            canvas.drawText(t, lockX, lockY - 44f * density, textPaint)
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

        override fun onLongPress(e: MotionEvent) {
            if (!editLogo) onLongPress?.invoke(e.x, e.y)
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
