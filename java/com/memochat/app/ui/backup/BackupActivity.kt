package com.memochat.app.ui.backup

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.Entry
import com.memochat.app.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "备份"
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 30)
        }
        scroll.addView(container)
        setContentView(scroll)

        addCard {
            addRow("导出 TXT", "纯文本，适合分享/打印") { pickEntriesForTxt() }
            divider()
            addRow("导出备份包", "完整数据，可用于恢复") { exportBackup() }
            divider()
            addRow("从备份恢复", "选择备份包还原数据") { pickBackup() }
        }

        addCard {
            addRow("清除所有数据", "删除全部条目与消息", danger = true) { confirmClear() }
        }
    }

    private fun addCard(builder: LinearLayout.() -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            setPadding(14, 0, 14, 0)
        }
        card.builder()
        container.addView(card)
    }

    private fun LinearLayout.addRow(title: String, sub: String, danger: Boolean = false, click: () -> Unit) {
        val row = LinearLayout(this@BackupActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 0, 14, 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(54))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_item_pressed)
            isClickable = true
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val tv = TextView(this@BackupActivity).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            text = title
            textSize = 16f
            setTextColor(if (danger) 0xFFFF3B30.toInt() else 0xFF333333.toInt())
        }
        val subTv = TextView(this@BackupActivity).apply {
            text = sub
            textSize = 13f
            setTextColor(0xFF999999.toInt())
        }
        row.addView(tv)
        row.addView(subTv)
        addView(row)
    }

    private fun LinearLayout.divider() {
        val v = View(this@BackupActivity).apply {
            setBackgroundColor(0xFFEEEEEE.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 1)
        }
        addView(v)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pickEntriesForTxt() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(this@BackupActivity)
            val entries = db.entryDao().getAll().filter { it.deletedAt == 0L }
            withContext(Dispatchers.Main) {
                val labels = entries.map { it.date + "  " + it.title }.toTypedArray()
                val checked = BooleanArray(entries.size) { true }
                AlertDialog.Builder(this@BackupActivity, R.style.Theme_MemoChat_Dialog)
                    .setTitle("选择要导出的日记")
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        checked[which] = isChecked
                    }
                    .setPositiveButton("导出") { _, _ -> exportTxt(entries, checked) }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun exportTxt(entries: List<Entry>, checked: BooleanArray) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(this@BackupActivity)
                val sb = StringBuilder()
                sb.append("微记日记导出\n")
                sb.append("导出时间: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())).append("\n\n")
                entries.forEachIndexed { i, e ->
                    if (!checked[i]) return@forEachIndexed
                    sb.append("================================\n")
                    sb.append("标题: ").append(e.title).append("\n")
                    sb.append("日期: ").append(e.date).append("\n")
                    if (e.weather.isNotBlank()) sb.append("天气: ").append(e.weather).append("\n")
                    if (e.mood.isNotBlank()) sb.append("心情: ").append(e.mood).append("\n")
                    sb.append("--------------------------------\n")
                    val msgs = db.messageDao().getForEntry(e.id)
                    for (m in msgs) {
                        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.createdAt))
                        when (m.type) {
                            0 -> sb.append("[").append(t).append("] ").append(m.content).append("\n")
                            1 -> sb.append("[").append(t).append("] [图片]\n")
                            2 -> sb.append("[").append(t).append("] [语音]\n")
                            else -> sb.append("[").append(t).append("] [其他]\n")
                        }
                    }
                    sb.append("\n")
                }
                val dir = File(cacheDir, "export").apply { mkdirs() }
                val out = File(dir, "微记日记_" + System.currentTimeMillis() + ".txt")
                out.writeText(sb.toString(), Charsets.UTF_8)
                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@BackupActivity, packageName + ".fileprovider", out)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(share, "导出日记"))
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupActivity, "导出失败: " + t.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportBackup() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(this@BackupActivity)
                val obj = JSONObject()
                val es = JSONArray()
                db.entryDao().getAll().forEach { e ->
                    val jo = JSONObject()
                    jo.put("id", e.id)
                    jo.put("title", e.title)
                    jo.put("date", e.date)
                    jo.put("weather", e.weather)
                    jo.put("mood", e.mood)
                    jo.put("type", e.type)
                    jo.put("pinned", e.pinned)
                    jo.put("messages", JSONArray().apply {
                        db.messageDao().getForEntry(e.id).forEach { m ->
                            val mj = JSONObject()
                            mj.put("content", m.content)
                            mj.put("type", m.type)
                            mj.put("createdAt", m.createdAt)
                            mj.put("isBackfill", m.isBackfill)
                            put(mj)
                        }
                    })
                    es.put(jo)
                }
                obj.put("entries", es)
                val dir = File(cacheDir, "backup").apply { mkdirs() }
                val out = File(dir, "微记备份_" + System.currentTimeMillis() + ".json")
                out.writeText(obj.toString(), Charsets.UTF_8)
                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@BackupActivity, packageName + ".fileprovider", out)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(share, "导出备份包"))
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupActivity, "导出失败: " + t.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pickBackup() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/json" }
        startActivityForResult(i, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            data?.data?.let { restoreFrom(it) }
        }
    }

    private fun restoreFrom(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() } ?: return@launch
                val obj = JSONObject(json)
                val es = obj.getJSONArray("entries")
                val db = AppDatabase.get(this@BackupActivity)
                for (i in 0 until es.length()) {
                    val e = es.getJSONObject(i)
                    val newId = db.entryDao().insert(Entry(
                        title = e.optString("title"),
                        date = e.optString("date"),
                        weather = e.optString("weather"),
                        mood = e.optString("mood"),
                        type = e.optString("type", "日记"),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    ))
                    val ms = e.getJSONArray("messages")
                    for (j in 0 until ms.length()) {
                        val m = ms.getJSONObject(j)
                        db.messageDao().insert(Message(
                            entryId = newId,
                            content = m.optString("content"),
                            type = m.optInt("type", 0),
                            createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                            isBackfill = m.optBoolean("isBackfill", false)
                        ))
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupActivity, "恢复完成", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupActivity, "恢复失败: " + t.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this, R.style.Theme_MemoChat_Dialog)
            .setTitle("清除所有数据？")
            .setMessage("将删除全部条目与消息，此操作不可恢复")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.get(this@BackupActivity)
                    db.entryDao().deleteAll()
                    db.messageDao().deleteAll()
                }
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
            }.show()
    }
}
