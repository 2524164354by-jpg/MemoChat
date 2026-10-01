package com.memochat.app.ui.star

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

class StarredActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starred)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        val rv = findViewById<RecyclerView>(R.id.list)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = Adapter()
        rv.adapter = adapter
        val db = AppDatabase.get(this)
        lifecycleScope.launch {
            db.messageDao().starredAll().collectLatest { list ->
                adapter.items = list
                adapter.titles = db.entryDao().getAll().associate { it.id to it.title }
                adapter.notifyDataSetChanged()
            }
        }
    }

    inner class Adapter : RecyclerView.Adapter<VH>() {
        var items: List<Message> = emptyList()
        var titles: Map<Long, String> = emptyMap()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_starred, parent, false)
            return VH(v)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val m = items[pos]
            h.content.text = m.content
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val src = titles[m.entryId] ?: ""
            h.meta.text = "$src · ${df.format(Date(m.createdAt))}"
            h.itemView.setOnClickListener {
                ChatActivity.start(this@StarredActivity, m.entryId, "", m.id)
                finish()
            }
        }
    }
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val content: TextView = v.findViewById(R.id.tv_content)
        val meta: TextView = v.findViewById(R.id.tv_meta)
    }
}
