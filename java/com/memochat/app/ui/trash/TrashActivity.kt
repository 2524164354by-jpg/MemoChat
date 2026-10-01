package com.memochat.app.ui.trash

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrashActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "回收站"
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
        }
        scroll.addView(container)
        setContentView(scroll)
        load()
    }

    private fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(this@TrashActivity)
            val es = db.entryDao().trashEntries()
            val ms = db.messageDao().trashMessages()
            withContext(Dispatchers.Main) {
                container.removeAllViews()
                if (es.isEmpty() && ms.isEmpty()) {
                    container.addView(TextView(this@TrashActivity).apply {
                        text = "回收站是空的"
                        textSize = 16f
                        setTextColor(0xFF999999.toInt())
                        gravity = Gravity.CENTER
                    })
                    return@withContext
                }
                for (e in es) {
                    val days = 30 - ((System.currentTimeMillis() - e.deletedAt) / 86400000).toInt()
                    container.addView(row(
                        "条目: ${e.title}",
                        "删除于 ${fmt.format(Date(e.deletedAt))} · 还剩 ${days} 天",
                        onRestore = {
                            CoroutineScope(Dispatchers.IO).launch {
                                db.entryDao().restore(e.id)
                                withContext(Dispatchers.Main) { load() }
                            }
                        },
                        onDelete = {
                            AlertDialog.Builder(this@TrashActivity, R.style.Theme_MemoChat_Dialog)
                                .setTitle("彻底删除？")
                                .setMessage("此操作不可恢复")
                                .setPositiveButton("删除") { _, _ ->
                                    CoroutineScope(Dispatchers.IO).launch {
                                        db.entryDao().deleteById(e.id)
                                        withContext(Dispatchers.Main) { load() }
                                    }
                                }
                                .setNegativeButton("取消", null).show()
                        }
                    ))
                }
                for (m in ms) {
                    val days = 30 - ((System.currentTimeMillis() - m.deletedAt) / 86400000).toInt()
                    container.addView(row(
                        "消息: ${m.content.take(40)}",
                        "删除于 ${fmt.format(Date(m.deletedAt))} · 还剩 ${days} 天",
                        onRestore = {
                            CoroutineScope(Dispatchers.IO).launch {
                                db.messageDao().restore(m.id)
                                withContext(Dispatchers.Main) { load() }
                            }
                        },
                        onDelete = {
                            AlertDialog.Builder(this@TrashActivity, R.style.Theme_MemoChat_Dialog)
                                .setTitle("彻底删除？")
                                .setPositiveButton("删除") { _, _ ->
                                    CoroutineScope(Dispatchers.IO).launch {
                                        db.messageDao().delete(m)
                                        withContext(Dispatchers.Main) { load() }
                                    }
                                }
                                .setNegativeButton("取消", null).show()
                        }
                    ))
                }
            }
        }
    }

    private fun row(title: String, sub: String, onRestore: () -> Unit, onDelete: () -> Unit): View {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(0x22FFFFFF)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 }
        }
        ll.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(0xFF333333.toInt()) })
        ll.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(0xFF999999.toInt()) })
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 20, 0, 0) }
        btns.addView(TextView(this).apply {
            text = "恢复"; setTextColor(0xFF07C160.toInt()); textSize = 14f
            setOnClickListener { onRestore() }
        })
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(60, 1) }
        btns.addView(spacer)
        btns.addView(TextView(this).apply {
            text = "彻底删除"; setTextColor(0xFFFF3B30.toInt()); textSize = 14f
            setOnClickListener { onDelete() }
        })
        ll.addView(btns)
        return ll
    }
}
