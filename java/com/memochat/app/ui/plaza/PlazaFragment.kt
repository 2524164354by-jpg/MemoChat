package com.memochat.app.ui.plaza

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.memochat.app.R
import com.memochat.app.util.Prefs
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlazaFragment : Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ): View {
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * dm).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        val topbar = android.widget.FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                marginStart = dp(14); marginEnd = dp(14); topMargin = dp(10)
            }
            background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_topbar)
            elevation = dp(4).toFloat()
        }
        val title = TextView(ctx).apply {
            text = "广场"
            textSize = 17f
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        topbar.addView(title, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(topbar)

        val sub = TextView(ctx).apply {
            text = "发现更多记录自己的方式"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x80000000.toInt()); cornerRadius = 999f
            }
            elevation = dp(2).toFloat()
        }
        val subWrap = android.widget.LinearLayout(ctx).apply {
            setPadding(dp(20), dp(12), dp(20), dp(8))
            addView(sub)
        }
        root.addView(subWrap)

        val cards = listOf(
            Card("写点什么", "无干扰快速记录", R.drawable.ic_feather) {
                startActivity(Intent(ctx, QuickWriteActivity::class.java))
            },
            Card("回忆抽卡", "随机翻一条你以前写的", R.drawable.ic_shuffle) {
                startActivity(Intent(ctx, MemoryDrawActivity::class.java))
            },
            Card("时光胶囊", "写给未来的自己", R.drawable.ic_envelope) {
                startActivity(Intent(ctx, TimeCapsuleActivity::class.java))
            },
            Card("今日一问", "每天一个小问题", R.drawable.ic_question) {
                startActivity(Intent(ctx, TodayQuestionActivity::class.java))
            },
            Card("我的数据", "总条数、记录天数", R.drawable.ic_chart) {
                startActivity(Intent(ctx, DataActivity::class.java))
            }
        )

        cards.forEach { card ->
            root.addView(buildCard(card), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(14); marginEnd = dp(14); bottomMargin = dp(12)
                })
        }

        // 发酵中的草稿
        val drafts = JSONArray(Prefs.quickWriteDrafts)
        if (drafts.length() > 0) {
            val dh = TextView(ctx).apply {
                text = "还有几句没写完"
                textSize = 13f
                setTextColor(0xFF999999.toInt())
                setPadding(dp(20), dp(20), 0, dp(10))
            }
            root.addView(dh)
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            for (i in 0 until drafts.length()) {
                val o = drafts.getJSONObject(i)
                val id = o.optString("id")
                val content = o.optString("content")
                val updated = o.optLong("updatedAt")
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), dp(14), dp(18), dp(14))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (isDark()) 0xF51C1C1E.toInt() else 0xF5FFFFFF.toInt())
                        cornerRadius = dp(16).toFloat()
                    }
                    isClickable = true
                    setOnClickListener {
                        startActivity(Intent(ctx, QuickWriteActivity::class.java).putExtra("draft_id", id))
                    }
                }
                val line1 = TextView(ctx).apply {
                    text = content.take(30)
                    textSize = 15f
                    setTextColor(0xFF222222.toInt())
                }
                val line2 = TextView(ctx).apply {
                    text = sdf.format(Date(updated))
                    textSize = 11f
                    setTextColor(0xFF999999.toInt())
                    setPadding(0, dp(4), 0, 0)
                }
                row.addView(line1); row.addView(line2)
                root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(14); marginEnd = dp(14); bottomMargin = dp(10)
                })
            }
        }

        val sv = ScrollView(ctx).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addView(root)
        }
        return sv
    }

    private fun isDark(): Boolean {
        val m = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun buildCard(card: Card): View {
        val dm = resources.displayMetrics.density
        fun dp(v: Int) = (v * dm).toInt()
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) 0xF51C1C1E.toInt() else 0xF5FFFFFF.toInt())
                cornerRadius = dp(16).toFloat()
            }
            isClickable = true
            setOnClickListener { card.onClick() }
        }

        val iconBox = android.widget.FrameLayout(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) 0xFF2C2C2E.toInt() else 0xFFF2F2F7.toInt())
                cornerRadius = dp(12).toFloat()
            }
        }
        val icon = ImageView(ctx).apply {
            setImageResource(card.icon)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        iconBox.addView(icon, android.widget.FrameLayout.LayoutParams(dp(40), dp(40)))
        row.addView(iconBox, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })

        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val name = TextView(ctx).apply {
            text = card.name
            textSize = 16f
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val desc = TextView(ctx).apply {
            text = card.desc
            textSize = 12f
            setTextColor(if (isDark()) 0xFF8E8E93.toInt() else 0xFF8E8E93.toInt())
            setPadding(0, dp(2), 0, 0)
        }
        mid.addView(name); mid.addView(desc)
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val arrow = TextView(ctx).apply {
            text = "›"
            textSize = 22f
            setTextColor(0xFFC7C7CC.toInt())
        }
        row.addView(arrow)

        return row
    }

    private data class Card(val name: String, val desc: String, val icon: Int, val onClick: () -> Unit)
}
