package com.memochat.app.ui.calendar

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.Entry
import com.memochat.app.ui.chat.ChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 月历视图：写过记录的日子按心情色打圆点，点日期跳条目。
 */
object CalendarDialog {

    private fun moodColor(mood: String): Int = when (mood) {
        "开心" -> 0xFF07C160.toInt()
        "平静" -> 0xFF3B82F6.toInt()
        "疲惫" -> 0xFF9CA3AF.toInt()
        "难过" -> 0xFFA855F7.toInt()
        "兴奋" -> 0xFFF97316.toInt()
        "生气" -> 0xFFEF4444.toInt()
        else -> 0xFF07C160.toInt()
    }

    fun show(context: Context) {
        val dialog = BottomSheetDialog(context)
        val density = context.resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
            setBackgroundColor(Color.WHITE)
        }

        val monthLabel = TextView(context).apply {
            textSize = 18f
            setTextColor(0xFF111111.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(monthLabel)

        val grid = GridLayout(context).apply {
            columnCount = 7
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(grid)

        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        val entriesByDate = mutableMapOf<String, Entry>()

        fun render() {
            grid.removeAllViews()
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            monthLabel.text = "${y}年${m + 1}月"

            // weekday header
            listOf("日","一","二","三","四","五","六").forEach {
                grid.addView(TextView(context).apply {
                    text = it
                    textSize = 12f
                    setTextColor(0xFF999999.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dp(44); height = dp(36)
                    }
                })
            }

            val firstDay = Calendar.getInstance().apply {
                time = cal.time
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val startOffset = firstDay.get(Calendar.DAY_OF_WEEK) - 1
            val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)

            repeat(startOffset) {
                grid.addView(View(context).apply {
                    layoutParams = GridLayout.LayoutParams().apply { width = dp(44); height = dp(52) }
                })
            }

            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            for (d in 1..daysInMonth) {
                val cellDate = (Calendar.getInstance().apply {
                    time = cal.time; set(Calendar.DAY_OF_MONTH, d)
                })
                val dateStr = fmt.format(cellDate.time)
                val hasEntry = entriesByDate.containsKey(dateStr)
                val isToday = y == today.get(Calendar.YEAR) &&
                              m == today.get(Calendar.MONTH) &&
                              d == today.get(Calendar.DAY_OF_MONTH)

                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dp(44); height = dp(52)
                    }
                    isClickable = hasEntry
                    if (hasEntry) setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        entriesByDate[dateStr]?.let { e ->
                            dialog.dismiss()
                            ChatActivity.start(context, e.id)
                        }
                    }
                }
                cell.addView(TextView(context).apply {
                    text = d.toString()
                    textSize = 15f
                    setTextColor(when {
                        isToday -> Color.WHITE
                        hasEntry -> 0xFF111111.toInt()
                        else -> 0xFF888888.toInt()
                    })
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, dp(2))
                    if (isToday) {
                        setBackgroundResource(R.drawable.bg_today_dot)
                        minWidth = dp(28); minHeight = dp(28)
                        gravity = Gravity.CENTER
                    }
                })
                if (hasEntry) {
                    val e = entriesByDate[dateStr]!!
                    cell.addView(View(context).apply {
                        setBackgroundColor(moodColor(e.mood))
                        layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                            topMargin = dp(2)
                        }
                    })
                }
                grid.addView(cell)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val all = AppDatabase.get(context).entryDao().getAll()
            // one entry per date (latest)
            for (e in all) {
                if (e.date.isNotBlank()) {
                    entriesByDate[e.date] = e
                }
            }
            withContext(Dispatchers.Main) { render() }
        }

        dialog.setContentView(root)
        dialog.show()
    }
}
