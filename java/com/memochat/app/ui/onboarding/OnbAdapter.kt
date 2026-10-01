package com.memochat.app.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.memochat.app.R
import android.widget.ImageView

class OnbAdapter(val pages: List<OnbPage>) : RecyclerView.Adapter<OnbAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.onb_title)
        val sub: TextView = v.findViewById(R.id.onb_sub)
        val image: ImageView = v.findViewById(R.id.onb_image)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding, parent, false)
        return VH(v)
    }
    override fun getItemCount() = pages.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = pages[pos]
        h.title.text = p.title
        h.sub.text = p.subtitle
        h.image.setImageResource(arrayOf(R.drawable.onb_1, R.drawable.onb_2, R.drawable.onb_3)[pos])
    }
}
