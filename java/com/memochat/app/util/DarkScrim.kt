package com.memochat.app.util

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

object DarkScrim {
    fun apply(activity: Activity) {
        val mode = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (mode != android.content.res.Configuration.UI_MODE_NIGHT_YES) return
        try {
            val fl = activity.window.decorView as? FrameLayout ?: return
            // avoid duplicate
            if (fl.findViewWithTag<View>("dark_scrim") != null) return
            val v = View(activity)
            v.tag = "dark_scrim"
            v.setBackgroundColor(Color.argb(110, 0, 0, 0))
            v.isClickable = false
            fl.addView(v, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            v.setZ(0f)
        } catch (_: Exception) {}
    }
}
