package com.momanufaktur.mocam

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreUtil {

    const val FOLDER = "MoCam"

    fun fileName(prefix: String): String =
        prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())

    fun imageValues(name: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
        }
    }

    fun videoValues(name: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER)
        }
    }

    /**
     * Wendet Filter und Logo auf ein aufgenommenes JPEG an und speichert es in der Galerie.
     * Logo-Position: Mittelpunkt (cx, cy) und Breite w als Anteil des aufrechten Bildes.
     */
    fun processAndSavePhoto(
        ctx: Context,
        src: File,
        filter: Filter,
        logo: Bitmap?,
        cx: Float, cy: Float, w: Float, alpha: Float,
        mirror: Boolean = false,
        fix: Int = 0
    ): Uri? {
        val decoded = BitmapFactory.decodeFile(src.path) ?: return null

        // EXIF-Orientierung direkt beim Zeichnen anwenden (spart eine Bitmap-Kopie)
        val orientation = ExifInterface(src.path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        // Grundeinstellung (Drehen/Spiegeln) und Frontkamera-Spiegel – am aufrecht gedrehten Bild
        if (fix >= 4) m.postScale(-1f, 1f)
        if (fix % 4 != 0) m.postRotate((fix % 4) * 90f)
        if (mirror) m.postScale(-1f, 1f)
        val bounds = RectF(0f, 0f, decoded.width.toFloat(), decoded.height.toFloat())
        m.mapRect(bounds)
        m.postTranslate(-bounds.left, -bounds.top)

        val out = Bitmap.createBitmap(
            Math.round(bounds.width()), Math.round(bounds.height()), Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (!filter.isIdentity) paint.colorFilter = ColorMatrixColorFilter(filter.toColorMatrix())
        canvas.drawBitmap(decoded, m, paint)
        decoded.recycle()

        if (logo != null) {
            val lw = w * out.width
            val lh = lw * logo.height / logo.width
            val left = cx * out.width - lw / 2f
            val top = cy * out.height - lh / 2f
            val lp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            lp.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(logo, null, RectF(left, top, left + lw, top + lh), lp)
        }

        val uri = saveJpeg(ctx, out, fileName("MoCam"))
        out.recycle()
        return uri
    }

    private fun saveJpeg(ctx: Context, bmp: Bitmap, name: String): Uri? {
        val resolver = ctx.contentResolver
        val values = imageValues(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
            dir.mkdirs()
            val file = File(dir, "$name.jpg")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            @Suppress("DEPRECATION")
            values.put(MediaStore.MediaColumns.DATA, file.absolutePath)
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    fun loadThumbnail(ctx: Context, uri: Uri, isVideo: Boolean): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.contentResolver.loadThumbnail(uri, Size(256, 256), null)
        } else if (isVideo) {
            MediaMetadataRetriever().run {
                setDataSource(ctx, uri)
                val b = frameAtTime
                release()
                b
            }
        } else {
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })
            }
        }
    } catch (e: Exception) {
        null
    }
}
