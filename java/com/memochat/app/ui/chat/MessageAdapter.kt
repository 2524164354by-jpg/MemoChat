package com.memochat.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.memochat.app.R
import com.memochat.app.data.MsgType
import com.memochat.app.data.Message
import com.memochat.app.databinding.ItemMessageBinding
import com.memochat.app.util.BitmapUtil
import com.memochat.app.util.Prefs
import java.io.File

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.VH>() {

    private val items = mutableListOf<Message>()
    var avatarPath: String = ""
    var entryType: String = ""
    var highlightQuery: String = ""
    var focusMsgId: Long = 0L
    var onMessageLongClick: ((Message, View) -> Unit)? = null
    var dayMetaMap: Map<String, com.memochat.app.data.DayMeta> = emptyMap()
    var onDayHeaderClick: ((String) -> Unit)? = null

    var selectionMode = false
        private set
    val selectedIds = mutableSetOf<Long>()
    var onSelectionChanged: ((count: Int) -> Unit)? = null

    fun submit(list: List<Message>) {
        val old = ArrayList(items)
        items.clear()
        items.addAll(list)
        // Use simple diff for chat - notify range change is faster than full rebind
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
        if (selectedIds.isEmpty()) selectionMode = false
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) notifyItemChanged(idx)
        onSelectionChanged?.invoke(selectedIds.size)
    }
    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position)
    }

    inner class VH(private val b: ItemMessageBinding) :
        RecyclerView.ViewHolder(b.root) {

        init {
            b.dateHeader.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = items[pos].createdAt
                val dayStr = String.format(java.util.Locale.getDefault(),
                    "%04d-%02d-%02d",
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH)+1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH))
                onDayHeaderClick?.invoke(dayStr)
            }
            b.root.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val m = items[pos]
                if (selectionMode) toggleSelection(m.id)
            }
            b.root.setOnLongClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                val m = items[pos]
                if (selectionMode) toggleSelection(m.id)
                else onMessageLongClick?.invoke(m, b.bubble)
                true
            }
        }

        fun bind(msg: Message, pos: Int) {
            BitmapUtil.loadAvatarRound(b.avatar, avatarPath.ifBlank { Prefs.avatarPath }, dp(b.root.context, 60), entryType)
            // date header
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = msg.createdAt
            val dayKey = cal.get(java.util.Calendar.YEAR) * 10000 + (cal.get(java.util.Calendar.MONTH)+1) * 100 + cal.get(java.util.Calendar.DAY_OF_MONTH)
            var showHeader = false
            var headerText = ""
            if (pos == 0) {
                showHeader = true
                val dayStr0 = String.format(java.util.Locale.getDefault(),
                    "%04d-%02d-%02d",
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH)+1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH))
                val dm0 = dayMetaMap[dayStr0]
                val extras0 = listOf(dm0?.weather.orEmpty(), dm0?.mood.orEmpty())
                    .filter { it.isNotBlank() }.joinToString("  ")
                headerText = formatDay(cal) + if (extras0.isNotBlank()) "  " + extras0 else ""
            } else {
                val prev = items[pos-1]
                val pcal = java.util.Calendar.getInstance()
                pcal.timeInMillis = prev.createdAt
                val pDay = pcal.get(java.util.Calendar.YEAR) * 10000 + (pcal.get(java.util.Calendar.MONTH)+1) * 100 + pcal.get(java.util.Calendar.DAY_OF_MONTH)
                val gapMin = (msg.createdAt - prev.createdAt) / 60000
                if (pDay != dayKey) {
                    showHeader = true
                    val dayStr = String.format(java.util.Locale.getDefault(),
                        "%04d-%02d-%02d",
                        cal.get(java.util.Calendar.YEAR),
                        cal.get(java.util.Calendar.MONTH)+1,
                        cal.get(java.util.Calendar.DAY_OF_MONTH))
                    val dm = dayMetaMap[dayStr]
                    val extras = listOf(dm?.weather.orEmpty(), dm?.mood.orEmpty())
                        .filter { it.isNotBlank() }.joinToString("  ")
                    headerText = formatDay(cal) + if (extras.isNotBlank()) "  " + extras else ""
                } else if (gapMin >= 5) {
                    showHeader = true
                    headerText = String.format(java.util.Locale.getDefault(),
                        "%02d:%02d",
                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                        cal.get(java.util.Calendar.MINUTE))
                }
            }
            if (showHeader) {
                b.dateHeader.visibility = android.view.View.VISIBLE
                b.dateHeader.text = headerText
            } else {
                b.dateHeader.visibility = android.view.View.GONE
            }
            b.backfillTag.visibility = if (msg.isBackfill) View.VISIBLE else View.GONE
            if (msg.type == MsgType.IMAGE) {
                b.bubble.visibility = android.view.View.GONE
                b.editedDot.visibility = android.view.View.GONE
                b.image.visibility = android.view.View.VISIBLE
                val path = msg.content
                val bmp = decode(path, 1600)
                if (bmp != null) {
                    b.image.setImageBitmap(bmp)
                    b.image.background = b.root.context.getDrawable(R.drawable.bg_bubble_image)
                } else {
                    b.image.setImageResource(R.drawable.ic_image)
                }
                b.image.setOnClickListener {
                    if (!selectionMode) {
                        val imgs = items.filter { it.type == 1 }.map { it.content } as ArrayList<String>
                        val idx = imgs.indexOf(path)
                        com.memochat.app.ui.chat.ImageViewerActivity.start(b.root.context, imgs, idx.coerceAtLeast(0))
                    } else toggleSelection(msg.id)
                }
            } else {
                b.image.visibility = android.view.View.GONE
                b.bubble.visibility = android.view.View.VISIBLE
                b.bubble.text = null
                b.bubble.gravity = android.view.Gravity.CENTER
                b.bubble.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                b.bubble.text = com.memochat.app.ui.components.EmojiGrid.render(b.root.context, msg.content).let { rendered ->
                    if (focusMsgId == msg.id) {
                        android.text.SpannableString(rendered).apply {
                            setSpan(android.text.style.BackgroundColorSpan(0xFFFFD54F.toInt()), 0, length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    } else if (highlightQuery.isNotBlank()) {
                        val ss = android.text.SpannableString(rendered)
                        var idx = msg.content.indexOf(highlightQuery, ignoreCase = true)
                        while (idx >= 0) {
                            ss.setSpan(android.text.style.BackgroundColorSpan(0xFFB2F2BB.toInt()),
                                idx, idx + highlightQuery.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            idx = msg.content.indexOf(highlightQuery, idx + highlightQuery.length, ignoreCase = true)
                        }
                        ss
                    } else {
                        rendered
                    }
                }
                b.bubble.gravity = if (msg.content.length <= 12) android.view.Gravity.CENTER
                                 else android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                if (focusMsgId == msg.id) {
                    b.bubble.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFFD54F.toInt())
                } else {
                    b.bubble.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF95EC69.toInt())
                }
                b.editedDot.visibility = if (msg.edited) View.VISIBLE else View.GONE
            }
            b.root.setBackgroundColor(
                if (selectionMode && selectedIds.contains(msg.id)) Color.parseColor("#5534C759")
                else Color.TRANSPARENT
            )
        }
    }

    private fun highlight(text: String, q: String): android.text.Spanned {
        if (q.isBlank()) return android.text.SpannableString.valueOf(text)
        val ss = android.text.SpannableString(text)
        var idx = text.indexOf(q, ignoreCase = true)
        while (idx >= 0) {
            ss.setSpan(android.text.style.BackgroundColorSpan(0xFFB2F2BB.toInt()),
                idx, idx + q.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            idx = text.indexOf(q, idx + q.length, ignoreCase = true)
        }
        return ss
    }

    private fun formatDay(cal: java.util.Calendar): String {
        val weekNames = arrayOf("周日","周一","周二","周三","周四","周五","周六")
        val sb = StringBuilder()
        sb.append(cal.get(java.util.Calendar.MONTH)+1).append("月").append(cal.get(java.util.Calendar.DAY_OF_MONTH)).append("日")
        sb.append(" ").append(weekNames[cal.get(java.util.Calendar.DAY_OF_WEEK)-1])
        return sb.toString()
    }

    private fun decode(path: String, maxPx: Int): Bitmap? {
        if (!File(path).exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / sample > maxPx || opts.outHeight / sample > maxPx) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, o2)
    }

    private fun dp(c: Context, v: Int): Int =
        (v * c.resources.displayMetrics.density).toInt()
}
