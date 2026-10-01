package com.memochat.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.memochat.app.MainActivity
import com.memochat.app.R

class OnboardingActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout

    private val pages = listOf(
        OnbPage("先给记忆分个类", "", ""),
        OnbPage("每一次记录都算数", "", ""),
        OnbPage("按你喜欢的样子来", "", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        pager = findViewById(R.id.pager)
        dots = findViewById(R.id.dots)
        pager.adapter = OnbAdapter(pages)
        findViewById<Button>(R.id.btn_start).setOnClickListener { goMain() }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                findViewById<Button>(R.id.btn_start).visibility =
                    if (position == 2) View.VISIBLE else View.GONE
            }
        })
        updateDots(0)
    }

    private fun updateDots(pos: Int) {
        dots.removeAllViews()
        for (i in pages.indices) {
            val dot = View(this)
            val size = if (i == pos) 18 else 8
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = if (i > 0) 8 else 0 }
            dot.setBackgroundColor(if (i == pos) 0xFF07C160.toInt() else 0x33FFFFFF.toInt())
            dots.addView(dot)
        }
    }

    private fun goMain() {
        getSharedPreferences("onb", MODE_PRIVATE).edit().putBoolean("seen", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

data class OnbPage(val title: String, val subtitle: String, val bubble: String)
