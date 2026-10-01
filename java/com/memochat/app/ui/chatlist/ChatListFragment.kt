package com.memochat.app.ui.chatlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.Entry
import com.memochat.app.databinding.FragmentChatListBinding
import com.memochat.app.ui.chat.ChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!
    private val adapter = EntryAdapter()
    private var allEntries: List<Entry> = emptyList()
    private var allPreviews: Map<Long, String> = emptyMap()
    private var backCallback: androidx.activity.OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        adapter.onEntryClick = { entry ->
            if (!adapter.selectionMode) ChatActivity.start(requireContext(), entry.id, binding.searchInput.text.toString().trim())
        }
        adapter.onSelectionChanged = { count ->
            val sel = adapter.selectionMode
            binding.selectionTopBar.visibility = if (sel) View.VISIBLE else View.GONE
            binding.selectionBottomBar.visibility = if (sel) View.VISIBLE else View.GONE
            binding.selectionCount.text = "已选 $count 项"
            binding.selectionDelete.text = "删除 $count 项"
            binding.selectionAll.text = if (count == adapter.items.size && count > 0) "取消全选" else "全选"
        }
        binding.selectionClose.setOnClickListener { adapter.exitSelection() }
        binding.selectionAll.setOnClickListener {
            if (adapter.selectedIds.size == adapter.items.size) {
                adapter.selectedIds.clear()
                adapter.notifyDataSetChanged()
                adapter.onSelectionChanged?.invoke(0)
            } else {
                adapter.items.forEach { adapter.selectedIds.add(it.id) }
                adapter.notifyDataSetChanged()
                adapter.onSelectionChanged?.invoke(adapter.items.size)
            }
        }
        adapter.onPinClick = { entry ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val db = AppDatabase.get(requireContext())
                db.entryDao().setPinned(entry.id, !entry.pinned)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { reload() }
            }
        }
        adapter.onDeleteClick = { entry ->
            androidx.appcompat.app.AlertDialog.Builder(requireContext(), com.memochat.app.R.style.Theme_MemoChat_Dialog)
                .setTitle("删除这条？")
                .setMessage("删除后可在回收站恢复")
                .setPositiveButton("删除") { _, _ ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val db = AppDatabase.get(requireContext())
                        db.entryDao().softDelete(entry.id, System.currentTimeMillis())
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { reload() }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
        binding.selectionDelete.setOnClickListener {
            if (adapter.selectedIds.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext(), com.memochat.app.R.style.Theme_MemoChat_Dialog)
                .setTitle("删除 ${adapter.selectedIds.size} 条条目？")
                .setMessage("已移入回收站，30 天内可恢复")
                .setPositiveButton("删除") { _, _ -> doDeleteSelected() }
                .setNegativeButton("取消", null)
                .show()
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                applyFilter(binding.searchInput.text.toString()); true
            } else false
        }
    }

    private fun doDeleteSelected() {
        val ids = adapter.selectedIds.toList()
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(requireContext())
            for (id in ids) {
                db.entryDao().softDelete(id, System.currentTimeMillis())
            }
            withContext(Dispatchers.Main) {
                adapter.exitSelection()
                Toast.makeText(requireContext(), "已移到回收站，30天内可恢复", Toast.LENGTH_SHORT).show()
                reload()
            }
        }
    }

    private fun applyFilter(q: String) {
        val kw = q.trim()
        if (kw.isEmpty()) {
            adapter.submit(allEntries, allPreviews)
            binding.emptyView.visibility = View.GONE
            return
        }
        val db = com.memochat.app.data.AppDatabase.get(requireContext())
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val matchedIds = try { db.messageDao().searchEntryIds(kw).toSet() } catch (e: Exception) { emptySet() }
            val filtered = allEntries.filter {
                it.title.contains(kw, true) ||
                it.date.contains(kw, true) ||
                it.weather.contains(kw, true) ||
                it.mood.contains(kw, true) ||
                (allPreviews[it.id] ?: "").contains(kw, true) ||
                matchedIds.contains(it.id)
            }
            val searchPreviews = mutableMapOf<Long, String>()
            val searchCounts = mutableMapOf<Long, Int>()
            for (e in filtered) {
                val snip = try { db.messageDao().searchSnippet(e.id, kw) } catch (ex: Exception) { null }
                searchPreviews[e.id] = snip ?: (allPreviews[e.id] ?: "")
                searchCounts[e.id] = try { db.messageDao().searchCount(e.id, kw) } catch (ex: Exception) { 0 }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                adapter.query = kw
                adapter.submit(filtered, searchPreviews, searchCounts)
                binding.emptyView.visibility =
                    if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showEntryMenu(entry: Entry, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(if (entry.pinned) "取消置顶" else "置顶")
        popup.menu.add("隐藏")
        popup.menu.add("删除")
        popup.setOnMenuItemClickListener { item ->
            val db = AppDatabase.get(requireContext())
            CoroutineScope(Dispatchers.IO).launch {
                when (item.title) {
                    "置顶" -> db.entryDao().setPinned(entry.id, true)
                    "取消置顶" -> db.entryDao().setPinned(entry.id, false)
                    "隐藏" -> db.entryDao().setHidden(entry.id, true)
                    "删除" -> {
                        db.entryDao().softDelete(entry.id, System.currentTimeMillis())
                    }
                }
                withContext(Dispatchers.Main) { reload() }
            }
            true
        }
        popup.show()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    fun reload() {
        val db = AppDatabase.get(requireContext())
        CoroutineScope(Dispatchers.IO).launch {
            val entries = db.entryDao().getAll()
            val previews = mutableMapOf<Long, String>()
            for (e in entries) {
                val last = db.messageDao().lastContent(e.id) ?: ""
                val type = db.messageDao().lastType(e.id) ?: 0
                previews[e.id] = when (type) {
                    com.memochat.app.data.MsgType.IMAGE -> "[图片]"
                    com.memochat.app.data.MsgType.EMOJI -> "[表情]"
                    else -> last
                }
            }
            withContext(Dispatchers.Main) {
                allEntries = entries
                allPreviews = previews
                applyFilter(binding.searchInput.text.toString())
            }
        }
    }

    fun isSelectionMode() = adapter.selectionMode
    fun exitSelection() = adapter.exitSelection()


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
