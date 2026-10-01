package com.memochat.app.ui.plaza

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.Entry
import com.memochat.app.ui.chat.ChatActivity
import com.memochat.app.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class TodayQuestionActivity : AppCompatActivity() {

    private val questions = listOf(
        "今天最让你开心的一件小事是什么？",
        "今天最让你无语的一件事是什么？",
        "今天吃到最好吃的一口是什么？",
        "今天遇到的最暖心的陌生人？",
        "今天最想感谢的人是谁？",
        "今天最想逃开的一件事？",
        "今天如果能重来一次，你会改什么？",
        "今天身体哪个部位最累？",
        "今天听到最入耳的一句话？",
        "今天最让你发笑的瞬间？",
        "今天花得最值的一笔钱？",
        "今天最浪费时间的一件事？",
        "今天最想分享给谁？",
        "今天最想给自己说的一句话？",
        "今天哪个瞬间觉得自己长大了？",
        "今天有没有一件小事想记很久？",
        "今天闻到最喜欢的味道？",
        "今天最温柔的一个画面？",
        "今天最累的时刻和最松的时刻？",
        "今天最舍不得的瞬间？",
        "今天最尴尬的一件事？",
        "今天最有成就感的一件事？",
        "今天最想吐槽谁？",
        "今天最想抱抱谁？",
        "今天最想删掉的一段回忆？",
        "今天最想保留的一段回忆？",
        "今天吃到最惊艳的一样东西？",
        "今天听到的最好笑的笑话？",
        "今天路上看到最治愈的一幕？",
        "今天睡前最想回味的一件事？"
    )

    private fun answeredSet(): MutableSet<String> {
        val s = mutableSetOf<String>()
        try {
            val arr = JSONArray(Prefs.todayQAnswered)
            for (i in 0 until arr.length()) s.add(arr.getString(i))
        } catch (e: Exception) {}
        return s
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBg()
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val dayIdx = (System.currentTimeMillis() / (24*3600*1000)).toInt()
        val q = questions[dayIdx % questions.size]
        val answered = answeredSet()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(50), dp(20), dp(40))
        }

        // top capsule
        val topbar = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(10) }
            background = androidx.core.content.ContextCompat.getDrawable(this@TodayQuestionActivity, R.drawable.bg_topbar)
            elevation = dp(4).toFloat()
        }
        val tt = TextView(this).apply {
            text = "今日一问"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            gravity = Gravity.CENTER
        }
        topbar.addView(tt, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(topbar)

        val sv = ScrollView(this)
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(40), 0, dp(40))
        }

        val label = TextView(this).apply {
            text = "今天的问题"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            alpha = 0.9f
            setShadowLayer(8f, 0f, 1f, 0x80000000.toInt())
        }
        inner.addView(label)

        val qCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) 0xFF2C2C2E.toInt() else 0xFFEFEFEF.toInt())
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(3).toFloat()
        }
        val qText = TextView(this).apply {
            text = q
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.NORMAL)
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.5f)
        }
        qCard.addView(qText)
        inner.addView(qCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(28) })

        val alreadyAnswered = q in answered
        val btn = TextView(this).apply {
            text = if (alreadyAnswered) "今天已答 ✓" else "开始回答"
            gravity = Gravity.CENTER
            setTextColor(-0x1)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(40), dp(14), dp(40), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (alreadyAnswered) 0xFF888888.toInt() else 0xFF07C160.toInt())
                cornerRadius = dp(26).toFloat()
            }
            isClickable = true
            setOnClickListener { goAnswer(q) }
        }
        inner.addView(btn)

sv.addView(inner)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun goAnswer(question: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(this@TodayQuestionActivity)
            val existing = db.entryDao().getAll().firstOrNull { it.title == "今日一问" }
            val entryId = if (existing != null) existing.id else {
                val now = System.currentTimeMillis()
                db.entryDao().insert(Entry(
                    title = "今日一问",
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(now)),
                    weather = "", mood = "",
                    createdAt = now, updatedAt = now,
                    type = "日记"
                ))
            }
            // mark answered
            val s = answeredSet()
            s.add(question)
            Prefs.todayQAnswered = JSONArray(s).toString()
            withContext(Dispatchers.Main) {
                ChatActivity.start(this@TodayQuestionActivity, entryId)
            }
        }
    }


    private fun isDark(): Boolean {
        val m = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun applyBg() {
        val bg = Prefs.appBgPath
        if (bg.isNotBlank() && java.io.File(bg).exists()) {
            try {
                val bmp = android.graphics.BitmapFactory.decodeFile(bg)
                val dm = resources.displayMetrics
                val sw = dm.widthPixels.toFloat(); val sh = dm.heightPixels.toFloat()
                val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
                val scale = maxOf(sw / bw, sh / bh)
                val nw = (bw * scale).toInt(); val nh = (bh * scale).toInt()
                val sx = ((nw - sw) / 2f).toInt(); val sy = ((nh - sh) / 2f).toInt()
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
}
