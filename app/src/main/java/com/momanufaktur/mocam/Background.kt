package com.momanufaktur.mocam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hintergrund ersetzen (wie Zoom). Zwei Erkennungen zur Wahl:
 *  - "mlkit": Google ML Kit Selfie Segmentation (in der App enthalten, schnell)
 *  - "mediapipe": MediaPipe Image Segmenter mit dem Mehrklassen-Selfie-Modell (Haare/Gesicht/Körper/Kleidung,
 *    genauere Ränder; Modell ~16 MB wird einmalig geladen)
 */
object Background {
    const val ENGINE_MLKIT = "mlkit"
    const val ENGINE_MEDIAPIPE = "mediapipe"
    val ENGINES = listOf(ENGINE_MLKIT to "⚡ ML Kit – schnell", ENGINE_MEDIAPIPE to "🎯 MediaPipe – genauer")
    private const val MP_URL =
        "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite"

    /** Aktive Erkennung (von der Activity gesetzt). */
    @Volatile var engine = ENGINE_MLKIT
    private lateinit var appCtx: Context
    fun init(ctx: Context, eng: String) { appCtx = ctx.applicationContext; engine = eng }

    fun mpModelFile(ctx: Context) = File(dir(ctx), "selfie_multiclass_256x256.tflite")
    fun mpReady(ctx: Context) = mpModelFile(ctx).length() > 1_000_000L
    fun downloadMp(ctx: Context, onProgress: (Int) -> Unit) =
        Subtitles.download(MP_URL, mpModelFile(ctx), 1_000_000L, onProgress)

    private val mpExec = Executors.newSingleThreadExecutor()
    @Volatile private var mp: ImageSegmenter? = null
    private var mpFailed = false

    /** MediaPipe-Segmenter (lazy, nur auf mpExec benutzen). null = nicht verfügbar → ML Kit. */
    private fun mpSegmenter(): ImageSegmenter? {
        mp?.let { return it }
        if (mpFailed || !::appCtx.isInitialized || !mpReady(appCtx)) return null
        return try {
            val buf = java.io.FileInputStream(mpModelFile(appCtx)).channel.use {
                it.map(FileChannel.MapMode.READ_ONLY, 0, it.size())
            }
            val opts = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buf).build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .build()
            ImageSegmenter.createFromOptions(appCtx, opts).also { mp = it }
        } catch (e: Throwable) {
            Log.w("MoCam", "MediaPipe-Start", e); mpFailed = true; null
        }
    }

    /** MediaPipe: Wahrscheinlichkeit „Person“ je Pixel (1 − Hintergrund) als FloatArray, oder null. */
    private fun mpPerson(bmp: Bitmap): Triple<FloatArray, Int, Int>? {
        val seg = mpSegmenter() ?: return null
        return try {
            val res = seg.segment(BitmapImageBuilder(bmp).build())
            val masks = res.confidenceMasks().orElse(null) ?: return null
            val bgMask = masks[0]                       // Klasse 0 = Hintergrund
            val w = bgMask.width; val h = bgMask.height
            val fb = ByteBufferExtractor.extract(bgMask).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val out = FloatArray(w * h) { 1f - fb.get(it) }
            masks.forEach { it.close() }
            Triple(out, w, h)
        } catch (e: Throwable) {
            Log.w("MoCam", "MediaPipe-Segmentierung", e); null
        }
    }

    /** Wechsel der Erkennung: MediaPipe neu laden lassen (z. B. nach Modell-Download). */
    fun resetEngine() {
        mpExec.execute { try { mp?.close() } catch (_: Throwable) {}; mp = null; mpFailed = false }
    }

    private val live by lazy {
        Segmentation.getClient(SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build())
    }
    private val single by lazy {
        Segmentation.getClient(SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
    }

    fun dir(ctx: Context) = File(ctx.filesDir, "bg").apply { mkdirs() }
    fun imageFile(ctx: Context) = File(dir(ctx), "image.jpg")
    fun videoFile(ctx: Context) = File(dir(ctx), "video.mp4")

    /** Live-Erkennung für den GL-Prozessor: Bild rein, 1-Byte-Maske über Callback zurück. */
    fun liveSegmenter(): (Bitmap, (ByteBuffer?, Int, Int) -> Unit) -> Unit = { bmp, cb ->
        if (engine == ENGINE_MEDIAPIPE && ::appCtx.isInitialized && mpReady(appCtx)) {
            mpExec.execute {
                val r = mpPerson(bmp)
                if (r == null) { mlkitLive(bmp, cb); return@execute }
                val (f, w, h) = r
                val out = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder())
                for (v in f) out.put((v.coerceIn(0f, 1f) * 255f).toInt().toByte())
                out.position(0)
                bmp.recycle()
                cb(out, w, h)
            }
        } else mlkitLive(bmp, cb)
    }

    private fun mlkitLive(bmp: Bitmap, cb: (ByteBuffer?, Int, Int) -> Unit) {
        live.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { m ->
                val w = m.width; val h = m.height
                val src = m.buffer.apply { rewind() }
                val out = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder())
                for (i in 0 until w * h) out.put((src.float.coerceIn(0f, 1f) * 255f).toInt().toByte())
                out.position(0)
                bmp.recycle()
                cb(out, w, h)
            }
            .addOnFailureListener { e -> Log.w("MoCam", "Segmentierung", e); bmp.recycle(); cb(null, 0, 0) }
    }

    /** Hintergrund-Foto laden, auf max. 1920 px verkleinert. */
    fun loadImage(f: File): Bitmap? {
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > 1920) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /**
     * Foto: Hintergrund ersetzen. mode 1 = weichzeichnen, 2 = Foto, 3 = Video (Standbild bei 1 s).
     * Läuft im Hintergrund-Thread (wartet auf ML Kit).
     */
    fun replaceInPhoto(ctx: Context, photo: Bitmap, mode: Int, blur: Float): Bitmap {
        if (mode == 0) return photo
        return try {
            val sc = 512f / max(photo.width, photo.height)
            val small = Bitmap.createScaledBitmap(photo, (photo.width * sc).roundToInt().coerceAtLeast(1),
                (photo.height * sc).roundToInt().coerceAtLeast(1), true)
            val mpRes = if (engine == ENGINE_MEDIAPIPE)
                try { mpExec.submit<Triple<FloatArray, Int, Int>?> { mpPerson(small) }.get() } catch (e: Exception) { null }
            else null
            val probs: FloatArray; val mw: Int; val mh: Int
            if (mpRes != null) {
                probs = mpRes.first; mw = mpRes.second; mh = mpRes.third
            } else {
                val m = Tasks.await(single.process(InputImage.fromBitmap(small, 0)))
                mw = m.width; mh = m.height
                val buf = m.buffer.apply { rewind() }
                probs = FloatArray(mw * mh) { buf.float }
            }
            val px = IntArray(mw * mh)
            for (i in px.indices) {
                val v = probs[i]
                // weicher Übergang wie im Live-Bild
                val t = ((v - 0.35f) / 0.4f).coerceIn(0f, 1f)
                val a = (t * t * (3 - 2 * t) * 255f).toInt()
                px[i] = a shl 24
            }
            val maskSmall = Bitmap.createBitmap(px, mw, mh, Bitmap.Config.ARGB_8888)
            small.recycle()

            val out = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val full = Rect(0, 0, photo.width, photo.height)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            // 1) Hintergrund
            when (mode) {
                1 -> {
                    // Weichzeichnen: stark verkleinern und wieder vergrößern (zweimal für weiche Übergänge)
                    val f = (1f / (8f + 40f * blur)).coerceAtLeast(0.01f)
                    val tiny = Bitmap.createScaledBitmap(photo, (photo.width * f).roundToInt().coerceAtLeast(2),
                        (photo.height * f).roundToInt().coerceAtLeast(2), true)
                    val mid = Bitmap.createScaledBitmap(tiny, tiny.width * 4, tiny.height * 4, true)
                    c.drawBitmap(mid, null, full, paint)
                    tiny.recycle(); mid.recycle()
                }
                else -> {
                    val bg = if (mode == 2) loadImage(imageFile(ctx)) else videoFrame(videoFile(ctx))
                    if (bg != null) {
                        c.drawBitmap(bg, coverSrc(bg.width, bg.height, photo.width, photo.height), full, paint)
                        bg.recycle()
                    } else c.drawBitmap(photo, 0f, 0f, null)
                }
            }
            // 2) Person freigestellt darüber
            val person = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
            val pc = Canvas(person)
            pc.drawBitmap(photo, 0f, 0f, null)
            pc.drawBitmap(maskSmall, null, full, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            })
            c.drawBitmap(person, 0f, 0f, null)
            person.recycle(); maskSmall.recycle()
            photo.recycle()
            out
        } catch (e: Exception) {
            Log.w("MoCam", "Hintergrund im Foto", e)
            photo
        }
    }

    private fun coverSrc(bw: Int, bh: Int, w: Int, h: Int): Rect {
        val a = w.toFloat() / h; val b = bw.toFloat() / bh
        return if (b > a) {
            val cw = (bh * a).roundToInt(); val x = (bw - cw) / 2
            Rect(x, 0, x + cw, bh)
        } else {
            val ch = (bw / a).roundToInt(); val y = (bh - ch) / 2
            Rect(0, y, bw, y + ch)
        }
    }

    private fun videoFrame(f: File): Bitmap? = try {
        if (!f.exists()) null else {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(f.path)
            val b = r.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            r.release(); b
        }
    } catch (e: Exception) { null }
}
