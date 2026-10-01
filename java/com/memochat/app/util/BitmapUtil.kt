package com.memochat.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.memochat.app.R
import java.io.File
import java.io.FileOutputStream

object BitmapUtil {

    fun copyToPrivate(context: Context, uri: Uri, prefix: String): String? {
        return try {
            val dir = File(context.filesDir, "media").apply { mkdirs() }
            val out = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { src ->
                FileOutputStream(out).use { dst -> src.copyTo(dst) }
            }
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(path: String?, maxPx: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / sample > maxPx || opts.outHeight / sample > maxPx) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, o2)
    }

    private var cachedAvatarPath: String? = null
    private var cachedAvatarBmp: Bitmap? = null

    fun defaultAvatarForType(ctx: android.content.Context, type: String?): Int {
        return when (type) {
            "日记" -> R.drawable.ic_avatar_diary
            "吐槽" -> R.drawable.ic_avatar_complaint
            "工作" -> R.drawable.ic_avatar_work
            else -> R.drawable.ic_avatar_default
        }
    }

    fun loadAvatarRound(iv: ImageView, path: String?, sizePx: Int, type: String? = null) {
        val fallback = defaultAvatarForType(iv.context, type)
        if (path.isNullOrBlank()) {
            iv.setImageResource(fallback)
            return
        }
        if (cachedAvatarPath == path && cachedAvatarBmp != null) {
            iv.setImageBitmap(cachedAvatarBmp)
            return
        }
        val bmp = decode(path, sizePx)
        if (bmp == null) {
            iv.setImageResource(fallback)
            return
        }
        val rounded = circle(bmp)
        cachedAvatarPath = path
        cachedAvatarBmp = rounded
        iv.setImageBitmap(rounded)
    }

    fun circle(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (src.width - size) / 2f
        val top = (src.height - size) / 2f
        canvas.drawBitmap(src, left, top, paint)
        return out
    }

    fun copyAndCropBg(context: Context, uri: Uri, prefix: String): String? {
        return try {
            // first copy raw to temp
            val dir = File(context.filesDir, "media").apply { mkdirs() }
            val raw = File(dir, "raw_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { src ->
                FileOutputStream(raw).use { dst -> src.copyTo(dst) }
            } ?: return null
            // decode
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 2560) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(raw.absolutePath, o2) ?: return null
            // screen ratio
            val dm = context.resources.displayMetrics
            val targetRatio = dm.heightPixels.toDouble() / dm.widthPixels
            val srcRatio = bmp.height.toDouble() / bmp.width
            var cropW = bmp.width
            var cropH = bmp.height
            if (srcRatio > targetRatio) {
                // too tall, crop sides
                cropH = bmp.height
                cropW = (bmp.height / targetRatio).toInt()
            } else {
                cropW = bmp.width
                cropH = (bmp.width * targetRatio).toInt()
            }
            val left = ((bmp.width - cropW) / 2f).toInt().coerceAtLeast(0)
            val top = ((bmp.height - cropH) / 2f).toInt().coerceAtLeast(0)
            val cropped = Bitmap.createBitmap(bmp, left, top, cropW, cropH)
            val out = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { dst -> cropped.compress(Bitmap.CompressFormat.JPEG, 90, dst) }
            raw.delete()
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun loadChatBg(imageView: ImageView, path: String?) {
        val bmp = decode(path, 1600)
        if (bmp == null) {
            val color = ContextCompat.getColor(imageView.context, R.color.bg_chat)
            imageView.setImageBitmap(null)
            imageView.setBackgroundColor(color)
        } else {
            imageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            imageView.setImageBitmap(bmp)
        }
    }
}