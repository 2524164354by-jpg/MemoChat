package com.memochat.app.ui.star

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.Message
import com.memochat.app.ui.chat.ChatActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StarredFragment : Fragment() {
    private var allItems: List<Message> = emptyList()
    private var titles: Map<Long, String> = emptyMap()
    private lateinit var adapter: Adapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_starred, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.list)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = Adapter()
        rv.adapter = adapter
        val db = AppDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            db.messageDao().starredAll().collectLatest { list ->
                allItems = list
                titles = db.entryDao().getAll().associate { it.id to it.title }
                view.findViewById<TextView>(R.id.tv_count).text = "${list.size} 项"
                adapter.items = list
                adapter.notifyDataSetChanged()
            }
        }
        view.findViewById<View>(R.id.btn_search).visibility = android.view.View.GONE
        view.findViewById<View>(R.id.btn_search).setOnClickListener {
            val et = android.widget.EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("搜索星标")
                .setView(et)
                .setPositiveButton("搜索") { _, _ ->
                    val kw = et.text.toString()
                    adapter.items = allItems.filter { it.content.contains(kw, ignoreCase = true) }
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton("全部") { _, _ ->
                    adapter.items = allItems
                    adapter.notifyDataSetChanged()
                }
                .show()
        }
    }

    inner class Adapter : RecyclerView.Adapter<VH>() {
        var items: List<Message> = emptyList()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_starred, parent, false)
            return VH(v)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val m = items[pos]
            h.content.text = if (m.type == 1) "[图片]" else m.content
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val src = titles[m.entryId] ?: ""
            h.meta.text = "$src · ${df.format(Date(m.createdAt))}"
            h.itemView.setOnClickListener {
                ChatActivity.start(requireContext(), m.entryId, "", m.id)
            }
            h.itemView.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setItems(arrayOf("取消星标", "删除")) { d, which ->
                        when (which) {
                            0 -> lifecycleScope.launch {
                                AppDatabase.get(requireContext()).messageDao().setStarred(m.id, false)
                            }
                            1 -> AlertDialog.Builder(requireContext())
                                .setMessage("删除这条消息？")
                                .setPositiveButton("删除") { _, _ ->
                                    lifecycleScope.launch {
                                        AppDatabase.get(requireContext()).messageDao().delete(m)
                                    }
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                    }
                    .show()
                true
            }
        }
    }
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val content: TextView = v.findViewById(R.id.tv_content)
        val meta: TextView = v.findViewById(R.id.tv_meta)
    }
}
