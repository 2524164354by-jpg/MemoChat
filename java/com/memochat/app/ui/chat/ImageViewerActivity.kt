package com.memochat.app.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.github.chrisbanes.photoview.PhotoView

class ImageViewerActivity : Activity() {

    companion object {
        fun start(context: Context, paths: ArrayList<String>, startIndex: Int) {
            context.startActivity(
                Intent(context, ImageViewerActivity::class.java)
                    .putStringArrayListExtra("paths", paths)
                    .putExtra("index", startIndex)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        window.setBackgroundDrawableResource(android.R.color.black)

        val paths = intent.getStringArrayListExtra("paths") ?: arrayListOf()
        val startIndex = intent.getIntExtra("index", 0)

        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }

        val pager = ViewPager2(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(pager)

        val tvCounter = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                topMargin = (30 * resources.displayMetrics.density).toInt()
            }
            root.addView(this, lp)
        }

        pager.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<PVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PVH {
                val pv = PhotoView(parent.context)
                pv.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                return PVH(pv)
            }
            override fun getItemCount() = paths.size
            override fun onBindViewHolder(h: PVH, pos: Int) {
                val pv = h.itemView as PhotoView
                val bmp = BitmapFactory.decodeFile(paths[pos])
                pv.setImageBitmap(bmp)
                pv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                pv.setOnPhotoTapListener { _, _, _ -> finish() }
                pv.setOnScaleChangeListener { _, _, _ ->
                    pager.isUserInputEnabled = pv.scale <= 1.01f
                }
            }
        }
        pager.setCurrentItem(startIndex, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvCounter.text = "${position + 1} / ${paths.size}"
            }
        })
        tvCounter.text = "${startIndex + 1} / ${paths.size}"

        setContentView(root)
    }

    class PVH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v)
}
