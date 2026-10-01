package com.memochat.app.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class MoodLineChartView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    data class Point(val label: String, val value: Float, val color: Int, val mood: String = "")

    var points: List<Point> = emptyList()
        set(v) { field = v; postInvalidate() }

    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDDDDD"); strokeWidth = 2f
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#07C160"); strokeWidth = 6f; style = Paint.Style.STROKE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val moodText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333"); textSize = 30f
    }
    private val dateText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#999999"); textSize = 24f
    }

    override fun onDraw(canvas: Canvas) {
        try {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            if (points.isEmpty()) {
                canvas.drawText("暂无数据", width / 2f - 60f, height / 2f, moodText)
                return
            }
            val padL = 30f; val padR = 30f; val padT = 50f; val padB = 50f
            val w = width - padL - padR
            val h = height - padT - padB
            if (w <= 0 || h <= 0) return
            for (i in 1..5) {
                val y = padT + h - (i - 1) * h / 4f
                canvas.drawLine(padL, y, width - padR, y, axis)
            }
            val n = points.size
            val stepX = if (n > 1) w / (n - 1) else 0f
            val path = Path()
            for (i in points.indices) {
                val p = points[i]
                val x = padL + i * stepX
                val y = padT + h - (p.value - 1) * h / 4f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                dot.color = p.color
                canvas.drawCircle(x, y, 12f, dot)
                // mood label above dot
                canvas.drawText(p.mood, x - 20f, y - 20f, moodText)
                // date below
                canvas.drawText(p.label, x - 24f, height - 18f, dateText)
            }
            canvas.drawPath(path, line)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
