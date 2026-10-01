package com.memochat.app.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter

object BgDarkener {
    fun darkenIfDarkMode(activity: Activity, src: Bitmap): Bitmap {
        val mode = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (mode != android.content.res.Configuration.UI_MODE_NIGHT_YES) return src
        val out = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(src, 0f, 0f, Paint().apply {
            colorFilter = PorterDuffColorFilter(0x80000000.toInt(), PorterDuff.Mode.SRC_ATOP)
        })
        c.drawColor(0x66000000)
        return out
    }
}
