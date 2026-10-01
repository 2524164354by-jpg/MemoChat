package com.memochat.app.ui.plaza

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DataActivity : AppCompatActivity() {
    private var density = 1f
    private fun dp(v: Int) = (v * density).toInt()
    private var rangeDays: Long = 0
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        density = resources.displayMetrics.density

        // themed background
        applyBg()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(70), dp(16), dp(30))
        }

        // 顶部胶囊标题
        val topbar = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(12) }
            background = androidx.core.content.ContextCompat.getDrawable(this@DataActivity, R.drawable.bg_topbar)
            elevation = dp(4).toFloat()
        }
        val title = TextView(this).apply {
            text = "我的数据"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            gravity = Gravity.CENTER
        }
        topbar.addView(title, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(topbar)

        val sv = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(root)
        }
        setContentView(sv)
        reload()
    }

    private fun buildRangeRow() {
        // remove existing range row (index 1)
        while (root.childCount > 1) root.removeViewAt(1)
        val rangeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, 0, dp(16))
        }
        listOf(Pair(0L, "全部"), Pair(7L, "近7天"), Pair(30L, "近30天")).forEach { (days, label) ->
            val selected = rangeDays == days
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                setPadding(dp(14), dp(6), dp(14), dp(6))
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) 0xFF07C160.toInt() else (if (isDark()) 0x33FFFFFF.toInt() else 0x88FFFFFF.toInt()))
                    cornerRadius = dp(14).toFloat()
                }
                setTextColor(if (selected) 0xFFFFFFFF.toInt() else (if (isDark()) 0xFFE5E5EA.toInt() else 0xFF333333.toInt()))
                isClickable = true
                setOnClickListener { rangeDays = days; reload() }
            }
            rangeRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        root.addView(rangeRow, 1)
    }


    private fun isDark(): Boolean {
        val m = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun applyBg() {
        val bg = com.memochat.app.util.Prefs.appBgPath
        if (bg.isNotBlank() && java.io.File(bg).exists()) {
            try {
                val bmp = android.graphics.BitmapFactory.decodeFile(bg)
                val dm = resources.displayMetrics
                val sw = dm.widthPixels.toFloat()
                val sh = dm.heightPixels.toFloat()
                val bw = bmp.width.toFloat()
                val bh = bmp.height.toFloat()
                val scale = maxOf(sw / bw, sh / bh)
                val nw = (bw * scale).toInt()
                val nh = (bh * scale).toInt()
                val sx = ((nw - sw) / 2f).toInt()
                val sy = ((nh - sh) / 2f).toInt()
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, nw, nh, true)
                val cropped = android.graphics.Bitmap.createBitmap(scaled, sx, sy, sw.toInt(), sh.toInt())
                val darkened = com.memochat.app.util.BgDarkener.darkenIfDarkMode(this, cropped)
                window.setBackgroundDrawable(android.graphics.drawable.BitmapDrawable(resources, darkened))
            } catch (e: Exception) {
                window.setBackgroundDrawableResource(R.drawable.bg_main)
            }
        } else {
            window.setBackgroundDrawableResource(R.drawable.bg_main)
        }
    }

    private fun reload() {
        buildRangeRow()
        while (root.childCount > 2) root.removeViewAt(2)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(this@DataActivity)
            val cutoff = if (rangeDays == 0L) 0L else System.currentTimeMillis() - rangeDays * 24 * 3600 * 1000
            val msgs = db.messageDao().getAll().filter { it.deletedAt == 0L && it.type == 0 && it.createdAt >= cutoff }
            val entries = db.entryDao().getAll()
            val entryMap = entries.associateBy { it.id }

            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val daySet = msgs.map { fmt.format(Date(it.createdAt)) }.toSet()
            val total = msgs.size
            val chars = msgs.sumOf { it.content.length }
            val days = daySet.size

            var streak = 0
            var cursor = Calendar.getInstance()
            while (true) {
                if (daySet.contains(fmt.format(cursor.time))) { streak++; cursor.add(Calendar.DAY_OF_YEAR, -1) }
                else break
            }

            val hourMap = msgs.groupingBy { val c = Calendar.getInstance(); c.time = Date(it.createdAt); c.get(Calendar.HOUR_OF_DAY) }.eachCount()
            val topHour = hourMap.maxByOrNull { it.value }?.key

            val wkMap = msgs.groupingBy { val c = Calendar.getInstance(); c.time = Date(it.createdAt); c.get(Calendar.DAY_OF_WEEK) }.eachCount()
            val wkName = mapOf(1 to "周日", 2 to "周一", 3 to "周二", 4 to "周三", 5 to "周四", 6 to "周五", 7 to "周六")
            val topWk = wkMap.maxByOrNull { it.value }?.key?.let { wkName[it] }

            val lateNights = msgs.count { val c = Calendar.getInstance(); c.time = Date(it.createdAt); val h = c.get(Calendar.HOUR_OF_DAY); h in 0..4 }

            val wordMap = mutableMapOf<String, Int>()
            msgs.forEach { m ->
                m.content.split(Regex("[，。！？\\s、,.!?~～…\\[\\]（）()]+")).filter { it.length >= 2 }.forEach { w ->
                    wordMap[w] = (wordMap[w] ?: 0) + 1
                }
            }
            val topWords = wordMap.entries.sortedByDescending { it.value }.take(5)

            val entryCount = msgs.groupingBy { it.entryId }.eachCount()
            val topEntries = entryCount.entries.sortedByDescending { it.value }.take(5)
            val maxEntry = topEntries.maxOfOrNull { it.value } ?: 1

            val first = msgs.minByOrNull { it.createdAt }
            val longest = msgs.maxByOrNull { it.content.length }

            withContext(Dispatchers.Main) {
                // 总览
                addCard {
                    val row = LinearLayout(this@DataActivity).apply { orientation = LinearLayout.HORIZONTAL }
                    row.addView(numBlock("$streak", "连续天数"))
                    row.addView(numBlock("$total", "共写条数"))
                    row.addView(numBlock("$chars", "累计字数"))
                    it.addView(row)
                    pad(it, 8, 0)
                    line(it, "已经记录了 $days 天", 0xFF999999.toInt(), 12f)
                }

                // 你的节奏
                addCard {
                    cardTitle(it, "你的节奏")
                    if (topHour != null) line(it, "你最爱在 ${hourText(topHour)}写东西")
                    if (topWk != null) line(it, "$topWk 写得最多")
                    if (lateNights > 0) line(it, "这段时间熬夜写了 $lateNights 次")
                    if (topHour == null && topWk == null && lateNights == 0) line(it, "多写几天就有啦", 0xFF999999.toInt(), 13f)
                }

                // 这个月的心情
                addCard {
                    cardTitle(it, "这个月的心情")
                    line(it, "最近写了 $days 天，继续保持", if (isDark()) 0xFFE5E5EA.toInt() else 0xFF333333.toInt(), 14f)
                }

                // 你最爱说
                if (topWords.isNotEmpty()) {
                    addCard {
                        cardTitle(it, "你最爱说")
                        val maxC = topWords.first().value
                        val flow = LinearLayout(it.context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, 6, 0, 6)
                        }
                        topWords.forEachIndexed { idx, (w, c) ->
                            val size = 22f - idx * 2.5f
                            val t = TextView(it.context).apply {
                                text = w
                                textSize = size
                                setTextColor(if (idx == 0) 0xFF07C160.toInt() else 0xFF444444.toInt())
                                setTypeface(typeface, if (idx == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                                setPadding(0, 0, dp(14), 0)
                            }
                            flow.addView(t)
                        }
                        it.addView(flow)
                    }
                } else {
                    addCard {
                        cardTitle(it, "你最爱说")
                        line(it, "多写几天就有啦", 0xFF999999.toInt(), 13f)
                    }
                }

                // 哪个话题写最多
                if (topEntries.isNotEmpty()) {
                    addCard {
                        cardTitle(it, "哪个话题写最多")
                        topEntries.forEach { (eid, c) ->
                            val t = entryMap[eid]?.title ?: "条目"
                            line(it, "$t  ·  $c 条", if (isDark()) 0xFFE5E5EA.toInt() else 0xFF333333.toInt(), 14f)
                            val bar = ProgressBar(it.context, null, android.R.attr.progressBarStyleHorizontal).apply {
                                max = maxEntry
                                progress = c
                                progressTintList = android.content.res.ColorStateList.valueOf(0xFF07C160.toInt())
                                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x22000000)
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(4); bottomMargin = dp(8) }
                            }
                            it.addView(bar)
                        }
                    }
                }

                // 小纪念
                addCard {
                    cardTitle(it, "小纪念")
                    if (first != null) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                        line(it, "· 第一条写于 ${sdf.format(Date(first.createdAt))}", 0xFF666666.toInt(), 12f)
                        line(it, first.content.take(50), if (isDark()) 0xFFE5E5EA.toInt() else 0xFF333333.toInt(), 14f)
                    }
                    if (longest != null) {
                        pad(it, 8, 0)
                        line(it, "· 最长的一条有 ${longest.content.length} 字", 0xFF666666.toInt(), 12f)
                        line(it, longest.content.take(50), if (isDark()) 0xFFE5E5EA.toInt() else 0xFF333333.toInt(), 14f)
                    }
                    if (first == null && longest == null) line(it, "多写几天就有啦", 0xFF999999.toInt(), 13f)
                }
            }
        }
    }

    private fun hourText(h: Int): String {
        return when (h) {
            in 5..10 -> "早上 $h 点"
            in 11..13 -> "中午 $h 点"
            in 14..17 -> "下午 $h 点"
            in 18..22 -> "晚上 $h 点"
            else -> "深夜 $h 点"
        }
    }

    private fun pad(card: LinearLayout, top: Int, bottom: Int) {
        card.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(top + bottom)).apply { topMargin = dp(top) }
        })
    }

    private fun numBlock(value: String, label: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val v = TextView(this).apply {
            text = value
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            gravity = Gravity.CENTER
        }
        val l = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(0xFF999999.toInt())
            gravity = Gravity.CENTER
        }
        col.addView(v); col.addView(l)
        return col
    }

    private fun addCard(body: (LinearLayout) -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt())
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(1).toFloat()
        }
        body(card)
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })
    }

    private fun cardTitle(card: LinearLayout, t: String) {
        card.addView(TextView(this).apply {
            text = t
            textSize = 14f
            setTextColor(0xFF999999.toInt())
            setPadding(0, 0, 0, dp(8))
        })
    }

    private fun line(card: LinearLayout, t: String, color: Int = 0xFF222222.toInt(), size: Float = 15f) {
        card.addView(TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            setPadding(0, dp(3), 0, dp(3))
        })
    }

    private fun addScrimOverlay() {
        try {
            val fl = window.decorView as? android.widget.FrameLayout ?: return
            val s = android.view.View(this)
            s.setBackgroundColor(0x33000000)
            s.isClickable = false
            fl.addView(s, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        } catch (e: Exception) {}
    }
}
