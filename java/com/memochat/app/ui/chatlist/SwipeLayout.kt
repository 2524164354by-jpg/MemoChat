package com.memochat.app.ui.chatlist

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

class SwipeLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var opened = false

    private val buttonWidth: Int get() = if (childCount >= 2) getChildAt(0).width else 0

    fun isOpened() = opened

    fun close() {
        opened = false
        getChildAt(0)?.visibility = INVISIBLE
        getChildAt(1)?.animate()?.translationX(0f)?.setDuration(200)?.withEndAction {
            getChildAt(0)?.visibility = INVISIBLE
        }?.start()
    }

    fun open() {
        opened = true
        getChildAt(0)?.visibility = VISIBLE
        getChildAt(1)?.animate()?.translationX(-buttonWidth.toFloat())?.setDuration(200)?.start()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                // Horizontal drag dominates once detected; lock vertical scrolling
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 15) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return isDragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val fg = getChildAt(1) ?: return super.onTouchEvent(ev)
        when (ev.action) {
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                getChildAt(0)?.visibility = VISIBLE
                val dx = ev.rawX - startX
                var target = if (opened) -buttonWidth + dx else dx
                target = target.coerceIn(-buttonWidth.toFloat(), 0f)
                fg.translationX = target
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = ev.rawX - startX
                // If moved less than 30% of button width, snap closed; else open
                if (-dx < buttonWidth * 0.3) close() else open()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
