package com.memochat.app.ui.plaza

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.memochat.app.R
import com.memochat.app.util.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class QuickWriteActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private var draftId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        applyBg()

        val dm = resources.displayMetrics.density
        fun dp(v: Int) = (v * dm).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(50), dp(20), dp(30))
        }

        // 顶部胶囊
        val topbar = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(20) }
            background = androidx.core.content.ContextCompat.getDrawable(this@QuickWriteActivity, R.drawable.bg_topbar)
            elevation = dp(4).toFloat()
        }
        val title = TextView(this).apply {
            text = "写点什么"
            textSize = 16f
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        topbar.addView(title, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(topbar)

        // 提示
        val hintTv = TextView(this).apply {
            text = "写半句也行，它会等你回来"
            textSize = 13f
            setTextColor(0xFF999999.toInt())
            setPadding(dp(8), 0, 0, dp(20))
        }
        root.addView(hintTv)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) 0xFF2C2C2E.toInt() else 0xFFEFEFEF.toInt())
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(2).toFloat()
        }
        input = EditText(this).apply {
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, 0, 0, 0)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            gravity = Gravity.TOP
            isSingleLine = false
            minHeight = dp(400)
            setLineSpacing(dp(6).toFloat(), 1.2f)
        }
        card.addView(input)
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        draftId = intent.getStringExtra("draft_id")
        if (draftId != null) loadDraft(draftId!!) else input.requestFocus()
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

    private fun loadDraft(id: String) {
        val arr = JSONArray(Prefs.quickWriteDrafts)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) {
                input.setText(o.optString("content"))
                input.setSelection(input.text.length)
                break
            }
        }
    }

    override fun onBackPressed() {
        val text = input.text.toString().trim()
        if (text.isNotEmpty()) {
            saveOrUpdateDraft(text)
        } else {
            draftId?.let { deleteDraft(it) }
        }
        super.onBackPressed()
    }

    private fun saveOrUpdateDraft(text: String) {
        val arr = JSONArray(Prefs.quickWriteDrafts)
        val id = draftId ?: UUID.randomUUID().toString()
        var found = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) {
                o.put("content", text); o.put("updatedAt", System.currentTimeMillis()); found = true; break
            }
        }
        if (!found) {
            val o = JSONObject()
            o.put("id", id); o.put("content", text)
            o.put("createdAt", System.currentTimeMillis()); o.put("updatedAt", System.currentTimeMillis())
            arr.put(o)
        }
        Prefs.quickWriteDrafts = arr.toString()
        Toast.makeText(this, "已存为草稿", Toast.LENGTH_SHORT).show()
    }

    private fun deleteDraft(id: String) {
        val arr = JSONArray(Prefs.quickWriteDrafts)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) out.put(o)
        }
        Prefs.quickWriteDrafts = out.toString()
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
