package com.memochat.app.ui.plaza

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.ui.chat.ChatActivity
import com.memochat.app.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryDrawActivity : AppCompatActivity() {

    private lateinit var fromLabel: TextView
    private lateinit var msgView: TextView
    private lateinit var viewBtn: TextView
    private var currentMsgId: Long = 0
    private var currentEntryId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBg()
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(50), dp(20), dp(48))
        }

        // top capsule title
        val topbar = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(16) }
            background = androidx.core.content.ContextCompat.getDrawable(this@MemoryDrawActivity, R.drawable.bg_topbar)
            elevation = dp(4).toFloat()
        }
        val t = TextView(this).apply {
            text = "回忆抽卡"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            gravity = Gravity.CENTER
        }
        topbar.addView(t, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(topbar)

        // spacer top
        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // source label above card
        fromLabel = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setAlpha(0.85f)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
            setShadowLayer(8f, 0f, 1f, 0x80000000.toInt())
        }
        root.addView(fromLabel)

        // card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(32), dp(28), dp(24))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt())
                cornerRadius = dp(22).toFloat()
            }
            elevation = dp(10).toFloat()
        }
        msgView = TextView(this).apply {
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            textSize = 19f
            setLineSpacing(dp(7).toFloat(), 1.35f)
            gravity = Gravity.CENTER
        }
        card.addView(msgView)
        viewBtn = TextView(this).apply {
            text = "查看原文 ›"
            setTextColor(0xFF07C160.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
            setOnClickListener {
                if (currentMsgId > 0) {
                    val i = Intent(this@MemoryDrawActivity, ChatActivity::class.java)
                    i.putExtra("entry_id", currentEntryId)
                    i.putExtra("jump_to_msg", currentMsgId)
                    startActivity(i)
                }
            }
        }
        card.addView(viewBtn)
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // spacer bottom
        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // draw button
        val btn = TextView(this).apply {
            text = "再抽一张"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(15), 0, dp(15))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF07C160.toInt())
                cornerRadius = dp(26).toFloat()
            }
            elevation = dp(4).toFloat()
            setOnClickListener { draw() }
        }
        root.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        draw()
    }

    private fun draw() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(this@MemoryDrawActivity)
            val all = db.messageDao().getAll().filter { it.deletedAt == 0L && it.type == 0 }
            val old = all.filter { System.currentTimeMillis() - it.createdAt > 30L * 24 * 3600 * 1000 }
            val pool = if (old.isNotEmpty()) old else all
            withContext(Dispatchers.Main) {
                if (pool.isEmpty()) {
                    fromLabel.text = ""
                    msgView.text = "✦\n\n多写几天，\n就有回忆可抽啦"
                    msgView.gravity = Gravity.CENTER
                    viewBtn.visibility = View.GONE
                    return@withContext
                }
                val m = pool.random()
                val entry = db.entryDao().getById(m.entryId)
                val days = ((System.currentTimeMillis() - m.createdAt) / (24 * 3600 * 1000)).toInt()
                val moodPart = entry?.mood.orEmpty()
                fromLabel.text = buildString {
                    append("${days} 天前・来自「${entry?.title ?: ""}」")
                    if (moodPart.isNotBlank()) append(" · $moodPart")
                }
                currentMsgId = m.id
                currentEntryId = m.entryId
                viewBtn.visibility = View.VISIBLE
                msgView.alpha = 0f
                msgView.scaleX = 0.95f
                msgView.scaleY = 0.95f
                msgView.text = m.content
                msgView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).start()
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
