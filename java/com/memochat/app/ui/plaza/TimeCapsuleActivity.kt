package com.memochat.app.ui.plaza

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.TimeCapsule
import com.memochat.app.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TimeCapsuleActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private var chosenDays: Int = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        fun dp(v: Int) = (v * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(80), dp(24), dp(24))
        }

        val topbar = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(16) }
            background = androidx.core.content.ContextCompat.getDrawable(this@TimeCapsuleActivity, R.drawable.bg_topbar)
            elevation = dp(4).toFloat()
        }
        val title = TextView(this).apply {
            text = "时光信箱"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            gravity = Gravity.CENTER
        }
        topbar.addView(title, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(topbar)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { addView(list) }
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF07C160.toInt())
            setOnClickListener { showLetterSheet() }
        }

        val wrap = FrameLayout(this).apply {
            addView(root)
            addView(fab, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin = dp(24); marginEnd = dp(20)
            })
        }
        setContentView(wrap)
        applyBg()
        reload()
    }

    private fun showLetterSheet() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        chosenDays = 30

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (isDark()) 0xFF1C1C1E.toInt() else 0xFFFDF6E3.toInt())
            setPadding(dp(24), dp(28), dp(24), dp(24))
        }

        val head = TextView(this).apply {
            text = "✉️ 写一封信给未来"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(head)

        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, dp(16))
        }
        val dateLabel = TextView(this).apply {
            text = "想在什么时候打开："
            textSize = 14f
            setTextColor(0xFF666666.toInt())
        }
        val dateBtn = TextView(this).apply {
            text = "30 天后"
            textSize = 14f
            setTextColor(0xFF07C160.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        dateBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this@TimeCapsuleActivity, { _, y, m, dd ->
                cal.set(y, m, dd)
                val days = ((cal.timeInMillis - System.currentTimeMillis()) / (24*3600*1000)).toInt().coerceAtLeast(1)
                chosenDays = days
                dateBtn.text = "${y}年${m+1}月${dd}日"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        dateRow.addView(dateLabel)
        dateRow.addView(dateBtn)
        root.addView(dateRow)

        val edit = android.widget.EditText(this).apply {
            hint = "此刻想说的话，写下来，封进信封…"
            setTextColor(if (isDark()) 0xFFE5E5EA.toInt() else 0xFF333333.toInt())
            setHintTextColor(0xFFAAAAAA.toInt())
            setBackgroundColor(0x00000000)
            minLines = 6
            textSize = 15f
            gravity = Gravity.TOP or Gravity.START
        }
        root.addView(edit)

        val sheet = BottomSheetDialog(this)
        val seal = android.widget.Button(this).apply {
            text = "封上信封"
            setBackgroundColor(0xFF8B6F47.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val t = edit.text.toString()
                if (t.isBlank()) { toast("写点什么再封上吧"); return@setOnClickListener }
                save(t, chosenDays)
                sheet.dismiss()
            }
        }
        root.addView(seal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })

        sheet.setContentView(root)
        sheet.show()
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    private fun reload() {
        CoroutineScope(Dispatchers.IO).launch {
            val all = AppDatabase.get(this@TimeCapsuleActivity).capsuleDao().all()
            withContext(Dispatchers.Main) {
                list.removeAllViews()
                if (all.isEmpty()) {
                    val empty = TextView(this@TimeCapsuleActivity).apply {
                        text = "信箱还空着，点右下角 + 写第一封吧"
                        setTextColor(0xFF999999.toInt())
                        gravity = Gravity.CENTER
                        setPadding(0, dp20(), 0, 0)
                    }
                    list.addView(empty)
                    return@withContext
                }
                val now = System.currentTimeMillis()
                all.forEach { c ->
                    val unlocked = c.unlocked || c.unlockAt <= now
                    list.addView(buildCard(c, unlocked))
                }
            }
        }
    }

    private fun dp20() = (20 * resources.displayMetrics.density).toInt()

    private fun buildCard(c: TimeCapsule, unlocked: Boolean): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (unlocked) (if (isDark()) 0xFF2C2C2E.toInt() else 0xFFFFFFFF.toInt()) else (if (isDark()) 0xFF1C1C1E.toInt() else 0xFFEFEFEF.toInt()))
                cornerRadius = dp(14).toFloat()
            }
                        isClickable = true
            setOnClickListener {
                if (!unlocked) toast("还要 " + daysLeft(c.unlockAt) + " 天才能打开哦")
            }
        }
        val head = TextView(this).apply {
            text = if (unlocked) "✉️ 已拆开的信" else "✉️ 未拆开的信"
            setTextColor(if (unlocked) 0xFFB8860B.toInt() else 0xFF888888.toInt())
            textSize = 12f
        }
        card.addView(head)
        val body = TextView(this).apply {
            text = if (unlocked) c.content else "写给 ${daysLeft(c.unlockAt)} 天后的自己"
            setTextColor(if (isDark()) 0xFFE5E5EA.toInt() else 0xFF333333.toInt())
            textSize = 15f
            setPadding(0, dp(10), 0, 0)
        }
        card.addView(body)
        val foot = TextView(this).apply {
            val sdf = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
            text = if (unlocked) "写于 ${sdf.format(Date(c.createdAt))}"
                   else "还有 ${daysLeft(c.unlockAt)} 天打开"
            setTextColor(0xFF999999.toInt())
            textSize = 11f
            setPadding(0, dp(8), 0, 0)
        }
        card.addView(foot)
        card.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        return card
    }

    private fun daysLeft(unlockAt: Long): Int {
        return ((unlockAt - System.currentTimeMillis()) / (24 * 3600 * 1000)).toInt().coerceAtLeast(1)
    }

    private fun save(content: String, days: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.get(this@TimeCapsuleActivity).capsuleDao().insert(
                TimeCapsule(content = content, createdAt = System.currentTimeMillis(), unlockAt = System.currentTimeMillis() + days * 24L * 3600 * 1000)
            )
            withContext(Dispatchers.Main) { reload() }
        }
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
}
