package com.momanufaktur.mocam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import kotlin.math.max

/**
 * Animierte Logos: GIF, animiertes WebP und APNG. Jede Animation läuft einmal durch (start → isDone),
 * draw() malt das aktuelle Bild in die Zielgröße.
 */
interface LogoAnim {
    fun start()
    fun isDone(): Boolean
    fun draw(c: Canvas, w: Int, h: Int)
    fun stop() {}

    companion object {
        /** Endungen der gespeicherten Animationen neben dem Logo-PNG. */
        val EXTENSIONS = listOf("gif", "webp", "apng")

        /** Erkennt, ob die Datei eine Animation enthält, und liefert die passende Endung (sonst null). */
        fun detect(head: ByteArray, full: () -> ByteArray): String? {
            fun s(a: Int, b: Int) = if (head.size >= b) String(head, a, b - a, Charsets.US_ASCII) else ""
            if (s(0, 4) == "GIF8") return "gif"
            if (s(0, 4) == "RIFF" && s(8, 12) == "WEBP" && s(12, 16) == "VP8X" && head.size > 20 &&
                (head[20].toInt() and 0x02) != 0) return "webp"
            if (head.size >= 8 && head[0] == 0x89.toByte() && s(1, 4) == "PNG" && Apng.isAnimated(full())) return "apng"
            return null
        }

        /** Lädt eine gespeicherte Animation (im Hintergrund aufrufen). */
        fun load(file: File): LogoAnim? = try {
            when (file.extension) {
                "gif" -> GifAnim.load(file)
                "webp" -> WebpAnim.load(file)
                "apng" -> Apng.decode(file.readBytes())?.let { FramesAnim(it.first, it.second) }
                else -> null
            }
        } catch (t: Throwable) {
            Log.w("MoCam", "Animation laden", t); null
        }
    }
}

/** GIF über android.graphics.Movie (spulbar, präzise Zeit). */
@Suppress("DEPRECATION")
class GifAnim private constructor(private val movie: android.graphics.Movie) : LogoAnim {
    private var t0 = 0L
    override fun start() { t0 = SystemClock.uptimeMillis() }
    override fun isDone() = SystemClock.uptimeMillis() - t0 >= movie.duration()
    override fun draw(c: Canvas, w: Int, h: Int) {
        c.save()
        c.scale(w.toFloat() / movie.width().coerceAtLeast(1), h.toFloat() / movie.height().coerceAtLeast(1))
        movie.setTime((SystemClock.uptimeMillis() - t0).toInt().coerceAtMost(movie.duration()))
        movie.draw(c, 0f, 0f)
        c.restore()
    }

    companion object {
        fun load(f: File): GifAnim? {
            val m = android.graphics.Movie.decodeFile(f.path) ?: return null
            return if (m.duration() > 0) GifAnim(m) else null
        }
    }
}

/**
 * Animiertes WebP über AnimatedImageDrawable (Android 9+), läuft genau einmal durch.
 * Das Drawable hält sich auf einer Software-Leinwand nicht zuverlässig an setBounds() und zeichnet dann in
 * Originalgröße. Deshalb: schon beim Dekodieren auf max. 512 px verkleinern, in ein Zwischenbild in seiner
 * eigenen Größe zeichnen und dieses selbst in die Zielgröße skalieren.
 */
class WebpAnim private constructor(private val d: Drawable) : LogoAnim {
    @Volatile private var done = false
    private val iw = d.intrinsicWidth.coerceAtLeast(1)
    private val ih = d.intrinsicHeight.coerceAtLeast(1)
    private val tmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
    private val tmpCanvas = Canvas(tmp).apply { density = Bitmap.DENSITY_NONE }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun start() {
        done = false
        if (Build.VERSION.SDK_INT >= 28 && d is AnimatedImageDrawable) {
            d.repeatCount = 0
            d.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) { done = true }
            })
            d.start()
        } else done = true
    }
    override fun isDone() = done
    override fun stop() { if (Build.VERSION.SDK_INT >= 28 && d is AnimatedImageDrawable) d.stop() }
    override fun draw(c: Canvas, w: Int, h: Int) {
        tmp.eraseColor(Color.TRANSPARENT)
        d.setBounds(0, 0, iw, ih)
        d.draw(tmpCanvas)
        c.drawBitmap(tmp, null, Rect(0, 0, w, h), paint)
    }

    companion object {
        private const val MAX_SIDE = 512

        fun load(f: File): WebpAnim? {
            if (Build.VERSION.SDK_INT < 28) return null
            val d = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(f)) { decoder, info, _ ->
                val sw = info.size.width; val sh = info.size.height
                val sc = minOf(1f, MAX_SIDE.toFloat() / max(sw, sh))
                if (sc < 1f) decoder.setTargetSize((sw * sc).toInt().coerceAtLeast(1), (sh * sc).toInt().coerceAtLeast(1))
            }
            return if (d is AnimatedImageDrawable) WebpAnim(d) else null
        }
    }
}

/** Vorab entpackte Einzelbilder mit Anzeigedauer (für APNG). */
class FramesAnim(private val frames: List<Bitmap>, private val delays: IntArray) : LogoAnim {
    private var t0 = 0L
    private val total = delays.sum()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun start() { t0 = SystemClock.uptimeMillis() }
    override fun isDone() = SystemClock.uptimeMillis() - t0 >= total
    override fun draw(c: Canvas, w: Int, h: Int) {
        var t = (SystemClock.uptimeMillis() - t0).toInt()
        var i = 0
        while (i < frames.size - 1 && t >= delays[i]) { t -= delays[i]; i++ }
        c.drawBitmap(frames[i], null, Rect(0, 0, w, h), paint)
    }
}

/**
 * Kleiner APNG-Decoder: setzt jedes Teilbild als eigenes PNG zusammen, dekodiert es mit BitmapFactory
 * und berechnet die fertigen Bilder (Überblenden/Entsorgen laut Spezifikation).
 */
object Apng {
    private const val MAX_FRAMES = 200
    private const val MAX_SIDE = 512

    private class Chunk(val type: String, val data: ByteArray)

    private fun chunks(b: ByteArray): List<Chunk> {
        val out = mutableListOf<Chunk>()
        var p = 8
        while (p + 8 <= b.size) {
            val len = ((b[p].toInt() and 0xff) shl 24) or ((b[p + 1].toInt() and 0xff) shl 16) or
                ((b[p + 2].toInt() and 0xff) shl 8) or (b[p + 3].toInt() and 0xff)
            if (len < 0 || p + 12 + len > b.size) break
            val type = String(b, p + 4, 4, Charsets.US_ASCII)
            out += Chunk(type, b.copyOfRange(p + 8, p + 8 + len))
            p += 12 + len
            if (type == "IEND") break
        }
        return out
    }

    fun isAnimated(b: ByteArray): Boolean {
        for (c in chunks(b)) {
            if (c.type == "acTL") return true
            if (c.type == "IDAT") return false
        }
        return false
    }

    private fun u32(d: ByteArray, o: Int) = ((d[o].toInt() and 0xff) shl 24) or ((d[o + 1].toInt() and 0xff) shl 16) or
        ((d[o + 2].toInt() and 0xff) shl 8) or (d[o + 3].toInt() and 0xff)
    private fun u16(d: ByteArray, o: Int) = ((d[o].toInt() and 0xff) shl 8) or (d[o + 1].toInt() and 0xff)

    private fun writeChunk(out: DataOutputStream, type: String, data: ByteArray) {
        out.writeInt(data.size)
        val t = type.toByteArray(Charsets.US_ASCII)
        out.write(t); out.write(data)
        val crc = CRC32(); crc.update(t); crc.update(data)
        out.writeInt(crc.value.toInt())
    }

    private class Frame(val w: Int, val h: Int, val x: Int, val y: Int, val delay: Int, val dispose: Int, val blend: Int) {
        val data = ByteArrayOutputStream()
    }

    /** Liefert die fertigen Einzelbilder (max. 512 px) und ihre Dauer in ms – oder null. */
    fun decode(b: ByteArray): Pair<List<Bitmap>, IntArray>? {
        val list = chunks(b)
        val ihdr = list.firstOrNull { it.type == "IHDR" }?.data ?: return null
        val fullW = u32(ihdr, 0); val fullH = u32(ihdr, 4)
        val shared = list.filter { it.type in setOf("PLTE", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT") }
        val frames = mutableListOf<Frame>()
        var cur: Frame? = null
        for (c in list) {
            when (c.type) {
                "fcTL" -> {
                    if (frames.size >= MAX_FRAMES) break
                    val d = c.data
                    val num = u16(d, 20); val den = u16(d, 22).let { if (it == 0) 100 else it }
                    cur = Frame(u32(d, 4), u32(d, 8), u32(d, 12), u32(d, 16),
                        max(20, num * 1000 / den), d[24].toInt(), d[25].toInt()).also { frames += it }
                }
                "IDAT" -> cur?.data?.write(c.data)              // Standardbild gehört nur dazu, wenn fcTL davor
                "fdAT" -> cur?.data?.write(c.data, 4, c.data.size - 4)
            }
        }
        if (frames.size < 2) return null

        val canvasBmp = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888)
        canvasBmp.density = Bitmap.DENSITY_NONE
        val canvas = Canvas(canvasBmp).apply { density = Bitmap.DENSITY_NONE }
        val sc = minOf(1f, MAX_SIDE.toFloat() / max(fullW, fullH))
        val outW = (fullW * sc).toInt().coerceAtLeast(1); val outH = (fullH * sc).toInt().coerceAtLeast(1)
        val clear = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val src = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC) }
        val result = mutableListOf<Bitmap>()
        val delays = IntArray(frames.size)

        frames.forEachIndexed { i, f ->
            // Teilbild als eigenständiges PNG
            val png = ByteArrayOutputStream()
            DataOutputStream(png).use { o ->
                o.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
                val h = ihdr.copyOf()
                h[0] = (f.w ushr 24).toByte(); h[1] = (f.w ushr 16).toByte(); h[2] = (f.w ushr 8).toByte(); h[3] = f.w.toByte()
                h[4] = (f.h ushr 24).toByte(); h[5] = (f.h ushr 16).toByte(); h[6] = (f.h ushr 8).toByte(); h[7] = f.h.toByte()
                writeChunk(o, "IHDR", h)
                shared.forEach { writeChunk(o, it.type, it.data) }
                writeChunk(o, "IDAT", f.data.toByteArray())
                writeChunk(o, "IEND", ByteArray(0))
            }
            val bytes = png.toByteArray()
            val part = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val region = RectF(f.x.toFloat(), f.y.toFloat(), (f.x + f.w).toFloat(), (f.y + f.h).toFloat())
            val before = if (f.dispose == 2) Bitmap.createBitmap(canvasBmp) else null
            if (part != null) {
                if (f.blend == 0) canvas.drawRect(region, clear)               // APNG_BLEND_OP_SOURCE
                // Ziel-Rechteck explizit angeben: sonst skaliert Android nach Bildschirmdichte (Bild wird zu groß)
                canvas.drawBitmap(part, null, region, if (f.blend == 0) src else null)
                part.recycle()
            }
            result += Bitmap.createScaledBitmap(canvasBmp, outW, outH, true)
            delays[i] = f.delay
            when (f.dispose) {
                1 -> canvas.drawRect(region, clear)                            // auf transparent zurücksetzen
                2 -> before?.let {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    canvas.drawBitmap(it, null, Rect(0, 0, fullW, fullH), null); it.recycle()
                }
            }
        }
        canvasBmp.recycle()
        return result to delays
    }
}
