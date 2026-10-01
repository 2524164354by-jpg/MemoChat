package com.memochat.app.ui.chatlist

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.memochat.app.data.Entry
import com.memochat.app.databinding.ItemEntryBinding
import com.memochat.app.util.BitmapUtil
import com.memochat.app.util.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EntryAdapter : RecyclerView.Adapter<EntryAdapter.VH>() {

    val items = mutableListOf<Entry>()
    private val previews = mutableMapOf<Long, String>()
    private val matchCounts = mutableMapOf<Long, Int>()
    var query: String = ""
    var onEntryClick: ((Entry) -> Unit)? = null
    var onEntryLongClick: ((Entry, View) -> Unit)? = null
    var onPinClick: ((Entry) -> Unit)? = null
    var onDeleteClick: ((Entry) -> Unit)? = null

    var selectionMode = false
        private set
    val selectedIds = mutableSetOf<Long>()
    var onSelectionChanged: ((count: Int) -> Unit)? = null

    fun submit(entries: List<Entry>, lastContents: Map<Long, String>, counts: Map<Long, Int> = emptyMap()) {
        items.clear()
        items.addAll(entries)
        previews.clear()
        previews.putAll(lastContents)
        matchCounts.clear()
        matchCounts.putAll(counts)
        notifyDataSetChanged()
    }

    fun startSelection(id: Long) {
        selectionMode = true
        selectedIds.clear()
        selectedIds.add(id)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) {
            selectionMode = false
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun closeAllSwipes() {
        // no-op; tracked per-holder
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)
        val fg = (holder.itemView as? com.memochat.app.ui.chatlist.SwipeLayout)?.getChildAt(1)
        fg?.translationX = 0f
    }

    inner class VH(private val b: ItemEntryBinding) :
        RecyclerView.ViewHolder(b.root) {

        init {
            b.foreground.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val e = items[pos]
                if (selectionMode) toggleSelection(e.id)
                else onEntryClick?.invoke(e)
            }
            b.foreground.setOnLongClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                val e = items[pos]
                if (selectionMode) {
                    toggleSelection(e.id)
                } else {
                    startSelection(e.id)
                }
                true
            }
            b.btnPin.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                onPinClick?.invoke(items[pos])
            }
            b.btnDelete.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                onDeleteClick?.invoke(items[pos])
            }
        }

        fun bind(entry: Entry) {
            val ctx = b.root.context
            (b.root as SwipeLayout).let { it.getChildAt(1).translationX = 0f; it.getChildAt(1).animate().cancel() }
            val av = entry.avatarPath.ifBlank { Prefs.avatarPath }
            BitmapUtil.loadAvatarRound(b.avatar, av, dp(ctx, 64), entry.type)
            b.title.text = highlight(entry.title, query)
            if (entry.title != entry.type) {
                b.title.append("  ")
                val sp = android.text.SpannableString("· " + entry.type)
                sp.setSpan(android.text.style.ForegroundColorSpan(0xFF999999.toInt()), 0, sp.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(android.text.style.RelativeSizeSpan(0.7f), 0, sp.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                b.title.append(sp)
            }
            val tags = entry.tags()
            val last = previews[entry.id].orEmpty().trim()
            val draft = com.memochat.app.util.Prefs.getDraft(entry.id)
            val baseText = buildString {
                if (tags.isNotEmpty()) append(tags).append("  ")
                append(if (last.isEmpty()) "写下点什么…" else last)
            }
            if (draft.isNotBlank()) {
                val rendered = com.memochat.app.ui.components.EmojiGrid.render(ctx, "草稿: $draft")
                val ss = android.text.SpannableString(rendered)
                ss.setSpan(android.text.style.ForegroundColorSpan(0xFFFF3B30.toInt()), 0, 3, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                b.preview.text = ss
            } else {
                b.preview.text = com.memochat.app.ui.components.EmojiGrid.render(ctx, baseText)
            }
            val cnt = matchCounts[entry.id] ?: 0
            if (cnt > 0 && query.isNotBlank()) {
                b.matchCount.visibility = android.view.View.VISIBLE
                b.matchCount.text = "共 $cnt 条相关聊天记录"
            } else {
                b.matchCount.visibility = android.view.View.GONE
            }
            b.btnPin.text = if (entry.pinned) "取消置顶" else "置顶"
            b.time.text = formatTime(entry.updatedAt)
            if (selectionMode) {
                b.foreground.setBackgroundColor(
                    if (selectedIds.contains(entry.id)) Color.parseColor("#22000000")
                    else Color.TRANSPARENT
                )
            } else if (entry.pinned) {
                b.foreground.setBackgroundResource(com.memochat.app.R.drawable.bg_entry_pinned)
                b.title.setTypeface(b.title.typeface, android.graphics.Typeface.BOLD)
                b.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            } else {
                b.foreground.setBackgroundResource(com.memochat.app.R.drawable.bg_card)
                b.title.setTypeface(b.title.typeface, android.graphics.Typeface.NORMAL)
                b.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            }
        }
    }

    private fun highlight(text: String, q: String): android.text.Spanned {
        if (q.isBlank()) return android.text.SpannableString(text)
        val ss = android.text.SpannableString(text)
        var idx = text.indexOf(q, ignoreCase = true)
        while (idx >= 0) {
            ss.setSpan(android.text.style.ForegroundColorSpan(0xFF07C160.toInt()),
                idx, idx + q.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            idx = text.indexOf(q, idx + q.length, ignoreCase = true)
        }
        return ss
    }

    private fun formatTime(ts: Long): String {
        val now = System.currentTimeMillis()
        val day = 24 * 3600 * 1000L
        val todayStart = (now / day) * day
        return if (ts >= todayStart) {
            SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))
        } else if (ts >= todayStart - day) {
            "昨天"
        } else {
            SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(ts))
        }
    }

    private fun dp(c: Context, v: Int): Int =
        (v * c.resources.displayMetrics.density).toInt()
}
