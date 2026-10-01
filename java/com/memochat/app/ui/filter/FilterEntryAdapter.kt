package com.memochat.app.ui.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.memochat.app.data.Entry
import com.memochat.app.databinding.ItemFilterEntryBinding

class FilterEntryAdapter : RecyclerView.Adapter<FilterEntryAdapter.VH>() {

    private val items = mutableListOf<Entry>()
    private val previews = mutableMapOf<Long, String>()
    var onEntryClick: ((Entry) -> Unit)? = null

    fun submit(entries: List<Entry>, lastContents: Map<Long, String>) {
        items.clear()
        items.addAll(entries)
        previews.clear()
        previews.putAll(lastContents)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFilterEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemFilterEntryBinding) :
        RecyclerView.ViewHolder(b.root) {

        init { b.root.setOnClickListener { onEntryClick?.invoke(items[adapterPosition]) } }

        fun bind(e: Entry) {
            b.title.text = e.title
            b.tags.text = listOf(e.date, e.weather, e.mood)
                .filter { it.isNotBlank() }.joinToString(" · ")
            val last = previews[e.id].orEmpty().trim()
            b.preview.text = if (last.isEmpty()) "写下点什么…" else last
        }
    }
}
