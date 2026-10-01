package com.memochat.app.util

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 全局动效 Token — 按 ColorOS 17 规范
 * - 时长：instant 100 / fast 150 / normal 250 / slow 350
 * - 曲线：cubic-bezier(0.2,0,0,1) ease-out
 * - 位移：4/8/12dp；缩放按下 0.96，弹窗 0.96→1.0
 */
object Motion {
    const val DUR_INSTANT = 100L
    const val DUR_FAST = 150L
    const val DUR_NORMAL = 250L
    const val DUR_SLOW = 350L

    const val SHIFT_SMALL = 4
    const val SHIFT_MED = 8
    const val SHIFT_LARGE = 12

    const val SCALE_PRESS = 0.96f
    const val SCALE_OPEN_FROM = 0.96f

    /** 标准 ease-out (cubic-bezier(0.2,0,0,1)) */
    fun easeOut(): android.view.animation.Interpolator =
        android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)

    /** 标准 ease-in */
    fun easeIn(): android.view.animation.Interpolator =
        android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f)

    /** 给按钮加按下反馈：scale 0.96 回弹（调用前不要 setOnClickListener） */
    fun View.pressFeedback(onClick: (() -> Unit)? = null) {
        setOnClickListener { v ->
            v.animate().scaleX(SCALE_PRESS).scaleY(SCALE_PRESS)
                .setDuration(DUR_FAST).setInterpolator(easeOut()).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(DUR_NORMAL).setInterpolator(easeOut()).start()
                }.start()
            onClick?.invoke()
        }
    }

    /** 进入：从下方 8px 滑入 + 淡入 */
    fun View.enterFadeSlide(dp: Int = SHIFT_MED) {
        val density = resources.displayMetrics.density
        translationY = dp * density
        alpha = 0f
        animate().alpha(1f).translationY(0f)
            .setDuration(DUR_NORMAL).setInterpolator(easeOut()).start()
    }

    /** 退出：向上 8px + 淡出 */
    fun View.exitFadeSlide(dp: Int = SHIFT_MED, onEnd: (() -> Unit)? = null) {
        val density = resources.displayMetrics.density
        animate().alpha(0f).translationYBy(-dp * density)
            .setDuration(DUR_FAST).setInterpolator(easeIn())
            .withEndAction { onEnd?.invoke() }.start()
    }
}
