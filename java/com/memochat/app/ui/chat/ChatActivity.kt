package com.memochat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnNextLayout
import android.widget.EditText
import android.widget.PopupMenu
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.Entry
import com.memochat.app.data.MsgType
import com.memochat.app.data.Message
import com.memochat.app.databinding.ActivityChatBinding
import com.memochat.app.ui.components.EmojiGrid
import com.memochat.app.util.BitmapUtil
import com.memochat.app.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val adapter = MessageAdapter()

    private var entryId: Long = -1L
    private var entry: Entry? = null
    private var backfillDayMillis: Long? = null
    private var keyboardHeight: Int = 0

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val path = BitmapUtil.copyToPrivate(this, uri, "msg")
                if (path != null) sendMessage(MsgType.IMAGE, path)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        adapter.highlightQuery = intent.getStringExtra("highlight").orEmpty()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.selectionMode) { adapter.exitSelection(); return }
                if (binding.emojiScroll.visibility == android.view.View.VISIBLE) {
                    binding.emojiScroll.visibility = android.view.View.GONE
                    return
                }
                finish()
            }
        })
        if (entryId < 0) { finish(); return }

        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        binding.recycler.layoutManager = lm
        binding.recycler.adapter = adapter
        binding.recycler.overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
        binding.recycler.isScrollbarFadingEnabled = true
        binding.recycler.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            addDuration = 2000
            moveDuration = 0
            changeDuration = 0
            supportsChangeAnimations = false
        }
        // dark mode icon tint
        val iconTint = if (isDark()) android.content.res.ColorStateList.valueOf(0xFF636366.toInt()) else android.content.res.ColorStateList.valueOf(0xFF333333.toInt())
        binding.btnBack.imageTintList = iconTint
        binding.btnCalendarLeft.imageTintList = iconTint
        binding.btnEmoji.imageTintList = iconTint
        binding.btnPlus.imageTintList = iconTint
        binding.btnEmojiBackspace.imageTintList = iconTint
        // top bar bg in dark
        if (isDark()) binding.topBar.setBackgroundColor(0xF21C1C1E.toInt())
        if (isDark()) binding.selectionBar.setBackgroundColor(0xF21C1C1E.toInt())
        binding.btnToBottom.setOnClickListener { scrollToBottom() }
        binding.recycler.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                val last = lm.findLastVisibleItemPosition()
                val total = rv.adapter?.itemCount ?: 0
                binding.btnToBottom.visibility = if (total > 0 && last < total - 2) android.view.View.VISIBLE else android.view.View.GONE
            }
        })
        // When keyboard pops up, gently scroll to last message
        var wasKeyboard = false
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            binding.root.getWindowVisibleDisplayFrame(r)
            val h = binding.root.rootView.height
            val kb = h - r.bottom
            val nowKeyboard = kb > h * 0.15
            if (nowKeyboard && !wasKeyboard) {
                keyboardHeight = kb
                binding.root.postDelayed({ scrollToBottom() }, 100)
            }
            wasKeyboard = nowKeyboard
        }

        binding.root.setOnClickListener {
            if (binding.emojiScroll.visibility == android.view.View.VISIBLE) {
                toggleEmoji()
            } else {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.input.windowToken, 0)
                binding.input.clearFocus()
            }
        }

        adapter.onDayHeaderClick = { dayStr -> showDayMetaDialog(dayStr) }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCalendarLeft.setOnClickListener { showBackfillPicker() }
        binding.btnBackToToday.setOnClickListener {
            backfillDayMillis = null
            binding.backfillPill.visibility = android.view.View.GONE
            binding.btnBackToToday.visibility = android.view.View.GONE
            binding.backfillBar.visibility = android.view.View.GONE
        }
        binding.title.setOnClickListener { showTitleEdit() }
        binding.btnEmoji.setOnClickListener { toggleEmoji() }
        binding.input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.emojiScroll.visibility == View.VISIBLE) {
                toggleEmoji()
            }
        }
        binding.input.setOnClickListener {
            if (binding.emojiScroll.visibility == View.VISIBLE) {
                toggleEmoji()
            }
        }
        binding.btnPlus.setOnClickListener { pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        ) }
        binding.input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        binding.input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        binding.input.setSingleLine(true)
        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendText(); true } else false
        }

        adapter.onMessageLongClick = { msg, anchor ->
            showMessageMenu(msg, anchor)
        }
        adapter.onSelectionChanged = { count ->
            val sel = count > 0
            binding.selectionBar.visibility = if (sel) android.view.View.VISIBLE else android.view.View.GONE
            binding.topBar.visibility = if (sel) android.view.View.GONE else android.view.View.VISIBLE
            binding.inputBar.visibility = if (sel) android.view.View.GONE else android.view.View.VISIBLE
            binding.emojiScroll.visibility = android.view.View.GONE
            binding.selectionCount.text = "已选 $count 条"
        }
        binding.selectionDelete.setOnClickListener {
            if (adapter.selectedIds.isEmpty()) return@setOnClickListener
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除 ${adapter.selectedIds.size} 条消息？")
                .setPositiveButton("删除") { _, _ ->
                    val ids = adapter.selectedIds.toList()
                    CoroutineScope(Dispatchers.IO).launch {
                        val ts = System.currentTimeMillis()
                        for (id in ids) AppDatabase.get(this@ChatActivity).messageDao().softDelete(id, ts)
                        withContext(Dispatchers.Main) {
                            adapter.exitSelection()
                            load()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        EmojiGrid.build(this, binding.emojiPanel) { emoji ->
            binding.input.append(android.text.SpannableString(EmojiGrid.render(this@ChatActivity, emoji)))
            binding.input.setSelection(binding.input.text.length)
        }

        binding.btnEmojiBackspace.setOnClickListener {
            val s = binding.input.text
            var len = s.length
            if (len > 0) {
                // If last char is inside an ImageSpan, delete the whole span
                val spans = s.getSpans(len - 1, len, android.text.style.ImageSpan::class.java)
                if (spans.isNotEmpty()) {
                    val start = s.getSpanStart(spans[0])
                    binding.input.setText(s.subSequence(0, start))
                } else {
                    if (len >= 2 && Character.isLowSurrogate(s[len-1]) && Character.isHighSurrogate(s[len-2])) len--
                    binding.input.setText(s.subSequence(0, len - 1))
                }
            }
            binding.input.setSelection(binding.input.text.length)
        }
        binding.btnEmojiSend.setOnClickListener { sendText() }

        val draft = com.memochat.app.util.Prefs.getDraft(entryId)
        if (draft.isNotEmpty()) binding.input.setText(draft)
        binding.input.setSelection(binding.input.text.length)
        applyChatBackground()
        load()
    }

    override fun onPause() {
        super.onPause()
        val t = binding.input.text.toString()
        if (t.isNotBlank()) com.memochat.app.util.Prefs.saveDraft(entryId, t)
        else com.memochat.app.util.Prefs.clearDraft(entryId)
    }

    private var menuDim: View? = null
    private var menuPopup: android.widget.PopupWindow? = null

    private fun showMessageMenu(msg: Message, anchor: View) {
        // Dim background
        val dim = View(this).apply { setBackgroundColor(0x66000000) }
        val root = window.decorView as android.view.ViewGroup
        root.addView(dim, android.view.ViewGroup.LayoutParams(-1, -1))
        menuDim = dim


        val v = layoutInflater.inflate(R.layout.popup_msg_menu, null)
        val pw = android.widget.PopupWindow(v, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.animationStyle = android.R.style.Animation_Dialog
        menuPopup = pw

        fun close() {
            pw.dismiss()
        }
        fun removeDim() {
            menuDim?.let {
                it.animate().cancel()
                val parent = it.parent as? android.view.ViewGroup
                parent?.removeView(it)
            }
            menuDim = null
        }
        pw.setOnDismissListener { removeDim() }
        dim.setOnClickListener { close() }

        v.findViewById<View>(R.id.action_edit).visibility = if (msg.type == MsgType.TEXT) View.VISIBLE else View.GONE
        // Star label dynamic
        v.findViewById<android.widget.TextView>(R.id.tv_star).text = if (msg.starred) "已星标" else "收藏"

        v.findViewById<View>(R.id.action_copy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("msg", msg.content))
            close()
        }
        v.findViewById<View>(R.id.action_forward).setOnClickListener { close() }
        v.findViewById<View>(R.id.action_star).setOnClickListener {
            val nowStarred = !msg.starred
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.get(this@ChatActivity).messageDao().setStarred(msg.id, nowStarred)
                withContext(Dispatchers.Main) {
                    load()
                    android.widget.Toast.makeText(this@ChatActivity,
                        if (nowStarred) "已星标" else "已取消星标",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            close()
        }
        v.findViewById<View>(R.id.action_edit).setOnClickListener { showEditDialog(msg); close() }
        v.findViewById<View>(R.id.action_recall).setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.get(this@ChatActivity).messageDao().delete(msg)
                withContext(Dispatchers.Main) { load() }
            }
            close()
        }
        v.findViewById<View>(R.id.action_multi).setOnClickListener { adapter.startSelection(msg.id); close() }

        // Position: below bubble by default, flip above if near bottom
        pw.contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuH = pw.contentView.measuredHeight
        val menuW = pw.contentView.measuredWidth
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val screenH = resources.displayMetrics.heightPixels
        val screenW = resources.displayMetrics.widthPixels
        val x = (screenW - menuW - dp(16)).coerceAtLeast(dp(16))
        val statusBarH = dp(80)
        val y = if (loc[1] - menuH - dp(8) < statusBarH) {
            loc[1] + anchor.height + dp(8)
        } else {
            loc[1] - menuH - dp(8)
        }
        pw.showAtLocation(anchor, android.view.Gravity.TOP or android.view.Gravity.START, x, y)
    }

    private fun showEditDialog(msg: Message) {
        val v = layoutInflater.inflate(R.layout.dialog_edit, null)
        val input = v.findViewById<EditText>(R.id.edit_input)
        input.setText(msg.content)
        input.setSelection(msg.content.length)
        val d = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(v)
            .setBackgroundInsetStart(0)
            .setBackgroundInsetEnd(0)
            .create()
        v.findViewById<View>(R.id.edit_cancel).setOnClickListener { d.dismiss() }
        v.findViewById<View>(R.id.edit_ok).setOnClickListener {
            val newText = input.text.toString().trim()
            if (newText.isEmpty()) return@setOnClickListener
            d.dismiss()
            val db = AppDatabase.get(this)
            CoroutineScope(Dispatchers.IO).launch {
                db.messageDao().updateContent(msg.id, newText)
                withContext(Dispatchers.Main) { load() }
            }
        }
        d.show()
    }

    private fun applyChatBackground() {
        val bgPath = Prefs.chatBgPath
        val bmp = if (bgPath.isNotEmpty() && File(bgPath).exists())
            BitmapFactory.decodeFile(bgPath) else null
        if (bmp != null) {
            try {
                val dm = resources.displayMetrics
                val sw = dm.widthPixels.toFloat()
                val sh = dm.heightPixels.toFloat()
                val bw = bmp.width.toFloat()
                val bh = bmp.height.toFloat()
                val scale = maxOf(sw / bw, sh / bh)
                val nw = (bw * scale).toInt()
                val nh = (bh * scale).toInt()
                val sx = ((nw - sw) / 2f).toInt()
                val sy = ((nh - sh) / 2f).toInt()
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, nw, nh, true)
                val cropped = android.graphics.Bitmap.createBitmap(scaled, sx, sy, sw.toInt(), sh.toInt())
                val darkened = com.memochat.app.util.BgDarkener.darkenIfDarkMode(this, cropped)
                val d = BitmapDrawable(resources, darkened)
                window.setBackgroundDrawable(d)
                binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            } catch (e: Exception) {
                window.setBackgroundDrawableResource(R.drawable.bg_main)
            }
        } else {
            val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            window.setBackgroundDrawableResource(if (isNight) R.drawable.bg_main_night else R.drawable.bg_main)
            binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    private fun load() {
        val db = AppDatabase.get(this)
        CoroutineScope(Dispatchers.IO).launch {
            val e = db.entryDao().getById(entryId)
            val msgs = db.messageDao().getForEntry(entryId)
            val dmList = db.dayMetaDao().all()
            withContext(Dispatchers.Main) {
                entry = e
                if (e == null) { finish(); return@withContext }
                binding.title.text = e.title
                binding.subtitle.text = listOf(e.date, e.weather, e.mood)
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
                adapter.avatarPath = entry?.avatarPath.orEmpty()
                adapter.entryType = entry?.type.orEmpty()
                adapter.dayMetaMap = dmList.associateBy { it.dayKey }
                adapter.submit(msgs)
                binding.recycler.post {
                    val n = adapter.itemCount
                    if (n > 0) binding.recycler.scrollToPosition(n - 1)
                }
                binding.input.clearFocus()
                val focusId = intent.getLongExtra("focus_msg_id", 0L)
                adapter.focusMsgId = focusId
                if (focusId > 0L) {
                    val idx = msgs.indexOfFirst { it.id == focusId }
                    if (idx >= 0) binding.recycler.scrollToPosition(idx)
                    binding.recycler.postDelayed({
                        adapter.focusMsgId = 0L
                        adapter.notifyItemChanged(idx)
                    }, 1500)
                    intent.removeExtra("focus_msg_id")
                } else {
                    val hl = adapter.highlightQuery
                    if (hl.isNotBlank()) {
                        val idx = msgs.indexOfFirst { it.type == 0 && it.content.contains(hl, ignoreCase = true) }
                        if (idx >= 0) {
                            binding.recycler.scrollToPosition(idx)
                        } else {
                            scrollToBottom()
                        }
                    }
                }
            }
        }
    }


    private fun showDayMetaDialog(dayStr: String) {
        val db = AppDatabase.get(this)
        CoroutineScope(Dispatchers.IO).launch {
            val existing = db.dayMetaDao().get(dayStr)
            withContext(Dispatchers.Main) {
                val layout = android.widget.LinearLayout(this@ChatActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 20)
                }
                val wInput = android.widget.EditText(this@ChatActivity).apply {
                    hint = "天气（晴/雨/阴...）"
                    setText(existing?.weather.orEmpty())
                }
                val mInput = android.widget.EditText(this@ChatActivity).apply {
                    hint = "心情（开心/平静/疲惫...）"
                    setText(existing?.mood.orEmpty())
                }
                layout.addView(wInput)
                layout.addView(mInput)
                android.app.AlertDialog.Builder(this@ChatActivity, R.style.Theme_MemoChat_Dialog)
                    .setTitle("$dayStr 的天气心情")
                    .setView(layout)
                    .setPositiveButton("保存") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            db.dayMetaDao().upsert(com.memochat.app.data.DayMeta(
                                dayKey = dayStr,
                                weather = wInput.text.toString().trim(),
                                mood = mInput.text.toString().trim()))
                            withContext(Dispatchers.Main) { load() }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun showTitleEdit() {
        val cur0 = entry ?: return
        val dm = resources.displayMetrics.density
        fun dp(v: Int) = (v * dm).toInt()
        val dialogBg = cardBg()
        val primary = primaryText()
        val sub = secondaryText()

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(dialogBg)
            setPadding(dp(20), dp(22), dp(20), dp(14))
        }
        val title = android.widget.TextView(this).apply {
            text = "修改标题"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primary)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(title)

        val input = android.widget.EditText(this).apply {
            setText(cur0.title)
            setSelection(text.length)
            setTextColor(primary)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark()) 0xFF2C2C2E.toInt() else 0xFFF5F5F5.toInt())
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0x33000000.toInt())
            }
        }
        root.addView(input, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }
        val cancel = android.widget.TextView(this).apply {
            text = "取消"
            textSize = 15f
            setTextColor(sub)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { (root.parent as? android.view.View)?.let { v -> (v.context as? android.app.Dialog)?.dismiss() } }
        }
        val ok = android.widget.TextView(this).apply {
            text = "确定"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF07C160.toInt())
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        row.addView(cancel)
        row.addView(ok)
        root.addView(row)

        val d = android.app.Dialog(this, R.style.Theme_MemoChat_Dialog)
        d.setContentView(root)
        ok.setOnClickListener {
            val t = input.text.toString().trim().ifEmpty { cur0.title }
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.get(this@ChatActivity)
                val updated = cur0.copy(title = t)
                db.entryDao().update(updated)
                withContext(Dispatchers.Main) {
                    entry = updated
                    binding.title.text = t
                }
            }
            d.dismiss()
        }
        cancel.setOnClickListener { d.dismiss() }
        d.show()
    }

    private fun sendText() {
        val text = binding.input.text.toString().trim()
        if (text.isEmpty()) return
        binding.input.setText("")
        com.memochat.app.util.Prefs.clearDraft(entryId)
        sendMessage(MsgType.TEXT, text)
    }

    private fun buildWheel(options: List<String>, initialIndex: Int, dp: (Int)->Int): android.widget.FrameLayout {
        val root = android.widget.FrameLayout(this)
        val rv = androidx.recyclerview.widget.RecyclerView(this).apply {
            setHasFixedSize(true)
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ChatActivity, androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, false)
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
        }
        root.addView(rv, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp(220)))
        val snap = androidx.recyclerview.widget.LinearSnapHelper()
        snap.attachToRecyclerView(rv)
        val itemH = dp(44)
        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            inner class VH(val tv: android.widget.TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv)
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
                val tv = android.widget.TextView(this@ChatActivity).apply {
                    gravity = android.view.Gravity.CENTER
                    layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT, itemH)
                }
                return VH(tv)
            }
            override fun getItemCount() = options.size
            override fun onBindViewHolder(h: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                (h as VH).tv.text = options[position]
            }
        }
        rv.adapter = adapter
        rv.post {
            val lm = rv.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
            val centerOffset = (root.height - itemH) / 2
            lm.scrollToPositionWithOffset(initialIndex, centerOffset)
        }
        // center highlight overlay
        val highlight = android.view.View(this).apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(0x2207C160.toInt()); cornerRadius = dp(10).toFloat()
            })
        }
        root.addView(highlight, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, itemH, android.view.Gravity.CENTER))
        // adjust text size/alpha on scroll
        fun update() {
            val lm = rv.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
            val centerY = root.height / 2f
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                val top = (child.top + child.bottom) / 2f
                val dist = kotlin.math.abs(top - centerY) / itemH
                val vh = rv.getChildViewHolder(child)
                val tv = (vh as? androidx.recyclerview.widget.RecyclerView.ViewHolder)?.itemView as? android.widget.TextView ?: continue
                val t = (1 - dist).coerceIn(0f, 1f)
                tv.textSize = 14f + 6f * t
                tv.alpha = 0.35f + 0.65f * t
                tv.setTypeface(tv.typeface, if (t > 0.7f) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                tv.setTextColor(if (t > 0.7f) (if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()) else (if (isDark()) 0xFF8E8E93.toInt() else 0xFF888888.toInt()))
            }
        }
        rv.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(r: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) { update() }
        })
        rv.postDelayed({ update() }, 100)
        // expose getter
        root.tag = object {
            fun getSelected(): Int {
                val lm = rv.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                val centerY = root.height / 2f
                var bestPos = 0
                var bestDist = Float.MAX_VALUE
                for (i in 0 until rv.childCount) {
                    val child = rv.getChildAt(i)
                    val cc = (child.top + child.bottom) / 2f
                    val d = kotlin.math.abs(cc - centerY)
                    if (d < bestDist) {
                        bestDist = d
                        bestPos = lm.getPosition(child)
                    }
                }
                return bestPos.coerceAtLeast(0)
            }
        }
        return root
    }

    private fun isDark(): Boolean {
        val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun cardBg(): Int = if (isDark()) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
    private fun primaryText(): Int = if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
    private fun secondaryText(): Int = if (isDark()) 0xFF8A8A8E.toInt() else 0xFF888888.toInt()

    private fun styleWheel(np: android.widget.NumberPicker, dp: (Int)->Int) {
        np.setSelectionDividerHeight(dp(1))
        try {
            val df = android.widget.NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight")
            df.isAccessible = true
            df.setInt(np, dp(1))
        } catch (_: Exception) {}
        // style middle selected EditText: big bold black
        fun apply(v: android.view.View) {
            if (v is android.widget.EditText) {
                v.setTextColor(0xFF111111.toInt())
                v.textSize = 20f
                v.setTypeface(v.typeface, android.graphics.Typeface.BOLD)
                v.isCursorVisible = false
                v.setSelection(v.text.length)
                v.setBackgroundColor(0)
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) apply(v.getChildAt(i))
            }
        }
        apply(np)
        // try to set fade behavior / text appearance via reflection
        try {
            val smField = android.widget.NumberPicker::class.java.getDeclaredField("mTopTextSize")
            smField.isAccessible = true
            smField.setFloat(np, 12f * resources.displayMetrics.density)
        } catch (_: Exception) {}
        try {
            val smField = android.widget.NumberPicker::class.java.getDeclaredField("mBottomTextSize")
            smField.isAccessible = true
            smField.setFloat(np, 12f * resources.displayMetrics.density)
        } catch (_: Exception) {}
        np.setPadding(0, dp(8), 0, dp(8))
    }

    private fun showBackfillPicker() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            setBackgroundColor(cardBg())
        }
        val title = android.widget.TextView(this).apply {
            text = "补记到哪天"; textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(primaryText())
        }
        container.addView(title)

        // start month = this month; pager holds 120 pages (5 years back .. 5 years forward)
        val nowCal = java.util.Calendar.getInstance()
        val totalPages = 120
        val startOffset = 60
        fun pageToCal(page: Int): java.util.Calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = nowCal.timeInMillis
            add(java.util.Calendar.MONTH, page - startOffset)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        val navRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(10))
        }
        val yearLabel = android.widget.TextView(this).apply {
            text = "${nowCal.get(java.util.Calendar.YEAR)} 年"
            textSize = 15f
            setTextColor(0xFF07C160.toInt()); gravity = android.view.Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val monthLabel = android.widget.TextView(this).apply {
            text = "${nowCal.get(java.util.Calendar.MONTH)+1} 月"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (isDark()) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()); gravity = android.view.Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val titleCenter = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            addView(yearLabel)
            addView(monthLabel)
        }
        val prev = android.widget.TextView(this).apply { text = "‹"; textSize = 26f; setTextColor(0xFF07C160.toInt()); setPadding(dp(16),0,dp(16),0) }
        val next = android.widget.TextView(this).apply { text = "›"; textSize = 26f; setTextColor(0xFF07C160.toInt()); setPadding(dp(16),0,dp(16),0) }
        navRow.addView(prev)
        navRow.addView(titleCenter, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        navRow.addView(next)
        container.addView(navRow)

        // weekday header
        val wk = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        listOf("一","二","三","四","五","六","日").forEach {
            wk.addView(android.widget.TextView(this).apply {
                text = it; textSize = 12f; setTextColor(secondaryText()); gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        container.addView(wk)

        // ViewPager2
        val vp = androidx.viewpager2.widget.ViewPager2(this)
        vp.offscreenPageLimit = 1
        container.addView(vp, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(46*6)))

        var pickedMillis: Long? = null
        var pickedLabel = ""
        var selectedCell: android.widget.FrameLayout? = null

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            inner class VH(val root: android.widget.LinearLayout) : androidx.recyclerview.widget.RecyclerView.ViewHolder(root)
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
                val root = android.widget.LinearLayout(this@ChatActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                }
                return VH(root)
            }
            override fun getItemCount() = totalPages
            override fun onBindViewHolder(h: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val vh = h as VH
                vh.root.removeAllViews()
                val cal = pageToCal(position)
                val y = cal.get(java.util.Calendar.YEAR)
                val m = cal.get(java.util.Calendar.MONTH)
                val offset = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val todayCal = java.util.Calendar.getInstance()
                var row = android.widget.LinearLayout(this@ChatActivity).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
                for (i in 0 until 42) {
                    if (i > 0 && i % 7 == 0) { vh.root.addView(row); row = android.widget.LinearLayout(this@ChatActivity).apply { orientation = android.widget.LinearLayout.HORIZONTAL } }
                    val dayNum = i - offset + 1
                    if (dayNum < 1 || dayNum > daysInMonth) {
                        row.addView(android.view.View(this@ChatActivity), android.widget.LinearLayout.LayoutParams(0, dp(46), 1f))
                        continue
                    }
                    val cell = android.widget.FrameLayout(this@ChatActivity)
                    val tv2 = android.widget.TextView(this@ChatActivity).apply {
                        text = dayNum.toString(); textSize = 15f; gravity = android.view.Gravity.CENTER
                    }
                    cell.addView(tv2)
                    val isFuture = (y > todayCal.get(java.util.Calendar.YEAR)) ||
                                   (y == todayCal.get(java.util.Calendar.YEAR) && m > todayCal.get(java.util.Calendar.MONTH)) ||
                                   (y == todayCal.get(java.util.Calendar.YEAR) && m == todayCal.get(java.util.Calendar.MONTH) && dayNum > todayCal.get(java.util.Calendar.DAY_OF_MONTH))
                    val isToday = y == todayCal.get(java.util.Calendar.YEAR) && m == todayCal.get(java.util.Calendar.MONTH) && dayNum == todayCal.get(java.util.Calendar.DAY_OF_MONTH)
                    if (isFuture) {
                        tv2.setTextColor(if (isDark()) 0xFF48484A.toInt() else 0xFFD0D0D0.toInt())
                    } else {
                        tv2.setTextColor(if (isDark()) 0xFFE5E5EA.toInt() else 0xFF222222.toInt())
                        cell.setOnClickListener {
                            pickedMillis = java.util.Calendar.getInstance().apply { set(y, m, dayNum, 23, 59, 59) }.timeInMillis
                            pickedLabel = "${m+1}月${dayNum}日"
                            selectedCell?.background = null
                            selectedCell = cell
                            cell.background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF07C160.toInt()); cornerRadius = 999f }
                            tv2.setTextColor(0xFFFFFFFF.toInt())
                        }
                    }
                    if (isToday) cell.background = android.graphics.drawable.GradientDrawable().apply { setColor(0x3307C160.toInt()); cornerRadius = 999f }
                    row.addView(cell, android.widget.LinearLayout.LayoutParams(0, dp(46), 1f))
                }
                vh.root.addView(row)
            }
        }
        vp.adapter = adapter
        vp.setCurrentItem(startOffset, false)

        fun updateTitle() {
            val c = pageToCal(vp.currentItem)
            yearLabel.text = "${c.get(java.util.Calendar.YEAR)} 年"
            monthLabel.text = "${c.get(java.util.Calendar.MONTH)+1} 月"
        }
        // year picker wheel
        yearLabel.setOnClickListener {
            val curY = pageToCal(vp.currentItem).get(java.util.Calendar.YEAR)
            val minY = nowCal.get(java.util.Calendar.YEAR) - 5
            val bs = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val ll = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(20), dp(20), dp(20), dp(24))
                setBackgroundColor(cardBg())
            }
            ll.addView(android.widget.TextView(this).apply {
                text = "选择年份"; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(primaryText()); gravity = android.view.Gravity.CENTER; setPadding(0,0,0,dp(10))
            })
            val years = (minY..minY+10).toList()
            val wheel = buildWheel(years.map { "${it} 年" }, curY - minY, { v -> dp(v) })
            ll.addView(wheel)
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0)
            }
            row.addView(android.widget.TextView(this).apply {
                text = "取消"; textSize = 15f; setTextColor(0xFF888888.toInt()); gravity = android.view.Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setOnClickListener { bs.dismiss() }
            }, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(android.widget.TextView(this).apply {
                text = "确定"; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(0xFF07C160.toInt()); gravity = android.view.Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setOnClickListener {
                    val idx = (wheel.tag as? Any)?.let { m ->
                        val meth = m.javaClass.getMethod("getSelected"); meth.invoke(m) as Int
                    } ?: 0
                    val y = minY + idx
                    val curM = pageToCal(vp.currentItem).get(java.util.Calendar.MONTH)
                    val diffMonths = (y - nowCal.get(java.util.Calendar.YEAR)) * 12 + (curM - nowCal.get(java.util.Calendar.MONTH))
                    vp.setCurrentItem(startOffset + diffMonths, true)
                    bs.dismiss()
                }
            })
            ll.addView(row)
            bs.setContentView(ll); bs.show()
        }
        monthLabel.setOnClickListener {
            val curC = pageToCal(vp.currentItem)
            val curY = curC.get(java.util.Calendar.YEAR)
            val curM = curC.get(java.util.Calendar.MONTH)
            val bs = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val ll = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(20), dp(20), dp(20), dp(24))
                setBackgroundColor(cardBg())
            }
            ll.addView(android.widget.TextView(this).apply {
                text = "选择月份"; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(primaryText()); gravity = android.view.Gravity.CENTER; setPadding(0,0,0,dp(10))
            })
            val wheel = buildWheel((1..12).map { "${it} 月" }, curM, { v -> dp(v) })
            ll.addView(wheel)
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0)
            }
            row.addView(android.widget.TextView(this).apply {
                text = "取消"; textSize = 15f; setTextColor(0xFF888888.toInt()); gravity = android.view.Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setOnClickListener { bs.dismiss() }
            }, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(android.widget.TextView(this).apply {
                text = "确定"; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(0xFF07C160.toInt()); gravity = android.view.Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setOnClickListener {
                    val m = (wheel.tag as? Any)?.let { m2 ->
                        val meth = m2.javaClass.getMethod("getSelected"); meth.invoke(m2) as Int
                    } ?: 0
                    val diffMonths = (curY - nowCal.get(java.util.Calendar.YEAR)) * 12 + (m - nowCal.get(java.util.Calendar.MONTH))
                    vp.setCurrentItem(startOffset + diffMonths, true)
                    bs.dismiss()
                }
            })
            ll.addView(row)
            bs.setContentView(ll); bs.show()
        }
        vp.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(p: Int) { updateTitle() }
        })
        prev.setOnClickListener { vp.setCurrentItem(vp.currentItem - 1, true) }
        next.setOnClickListener { vp.setCurrentItem(vp.currentItem + 1, true) }

        // buttons
        val btnRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        val cancel = android.widget.TextView(this).apply {
            text = "取消"; textSize = 15f; setTextColor(0xFF888888.toInt()); gravity = android.view.Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { sheet.dismiss() }
        }
        val ok = android.widget.TextView(this).apply {
            text = "确定"; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF07C160.toInt()); gravity = android.view.Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener {
                val pm = pickedMillis
                if (pm != null && pm < System.currentTimeMillis()) {
                    backfillDayMillis = pm
                    binding.backfillPill.visibility = android.view.View.VISIBLE
                    binding.backfillPill.text = "正在补记到 $pickedLabel"
                    binding.btnBackToToday.visibility = android.view.View.VISIBLE
                    binding.backfillBar.visibility = android.view.View.VISIBLE
                }
                sheet.dismiss()
            }
        }
        btnRow.addView(cancel, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(ok)
        container.addView(btnRow)

        sheet.setContentView(container)
        sheet.show()
    }

    private fun sendMessage(type: Int, content: String) {
        val db = AppDatabase.get(this)
        val now = System.currentTimeMillis()
        val backfill = backfillDayMillis
        val useTime = if (backfill != null && backfill < now) backfill else now
        val isBackfill = backfill != null && backfill < now
        CoroutineScope(Dispatchers.IO).launch {
            db.messageDao().insert(
                Message(entryId = entryId, type = type, content = content,
                    createdAt = useTime, isBackfill = isBackfill)
            )
            entry?.let {
                db.entryDao().update(it.copy(updatedAt = now))
            }
            val msgs = db.messageDao().getForEntry(entryId)
            val dmList = db.dayMetaDao().all()
            withContext(Dispatchers.Main) {
                adapter.avatarPath = entry?.avatarPath.orEmpty()
                adapter.entryType = entry?.type.orEmpty()
                adapter.dayMetaMap = dmList.associateBy { it.dayKey }
                adapter.submit(msgs)
                binding.recycler.post {
                    val n = adapter.itemCount
                    if (n > 0) binding.recycler.scrollToPosition(n - 1)
                }
                binding.input.clearFocus()
                scrollToBottom()

                    try {
                        val vib = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                        vib.vibrate(android.os.VibrationEffect.createOneShot(20, 128))
                    } catch (_: Exception) {}
            }
        }
    }

    private fun toggleEmoji() {
        val scroll = binding.emojiScroll
        val isVisible = scroll.visibility == View.VISIBLE
        if (isVisible) {
            scroll.animate().translationY(scroll.height.toFloat()).setDuration(150).withEndAction {
                scroll.visibility = View.GONE
                scroll.animate().translationY(0f).setDuration(0).start()
                binding.root.postDelayed({ scrollToBottom() }, 100)
            }.start()
            binding.input.requestFocus()
            showKeyboard(true)
        } else {
            showKeyboard(false)
            EmojiGrid.build(this, binding.emojiPanel) { emoji ->
                binding.input.append(android.text.SpannableString(EmojiGrid.render(this@ChatActivity, emoji)))
                binding.input.setSelection(binding.input.text.length)
            }
            if (keyboardHeight <= 0) {
                keyboardHeight = (resources.displayMetrics.heightPixels * 0.35f).toInt()
            }
            val lp = scroll.layoutParams
            lp.height = keyboardHeight
            scroll.layoutParams = lp
            scroll.translationY = scroll.height.toFloat()
            scroll.visibility = View.VISIBLE
            scroll.animate().translationY(0f).setDuration(200).start()
            binding.root.postDelayed({ scrollToBottom() }, 200)
        }
    }

    private fun showKeyboard(show: Boolean) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        if (show) imm.showSoftInput(binding.input, 0)
        else imm.hideSoftInputFromWindow(binding.input.windowToken, 0)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_UP) {
            val x = ev.rawX.toInt()
            val y = ev.rawY.toInt()
            // check if touch is outside input_bar and emoji_panel
            val inputLoc = IntArray(2); binding.inputBar.getLocationOnScreen(inputLoc)
            val emojiLoc = IntArray(2); binding.emojiScroll.getLocationOnScreen(emojiLoc)
            val inInput = y >= inputLoc[1]
            val inEmoji = binding.emojiScroll.visibility == android.view.View.VISIBLE && y >= emojiLoc[1]
            if (!inInput && !inEmoji) {
                if (binding.emojiScroll.visibility == android.view.View.VISIBLE) {
                    binding.emojiScroll.visibility = android.view.View.GONE
                } else {
                    showKeyboard(false)
                    binding.input.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun scrollToBottom() {
        binding.recycler.post {
            val n = binding.recycler.adapter?.itemCount ?: 0
            if (n > 0) {
                val sm = object : androidx.recyclerview.widget.LinearSmoothScroller(this) {
                    override fun getVerticalSnapPreference() = SNAP_TO_END
                    override fun calculateTimeForScrolling(dx: Int): Int = 50
                }
                sm.targetPosition = n - 1
                (binding.recycler.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).startSmoothScroll(sm)
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_ENTRY_ID = "entry_id"
        fun start(context: Context, entryId: Long, highlight: String = "", focusMsgId: Long = 0L) {
            context.startActivity(
                Intent(context, ChatActivity::class.java)
                    .putExtra(EXTRA_ENTRY_ID, entryId)
                    .putExtra("highlight", highlight)
                    .putExtra("focus_msg_id", focusMsgId)
            )
        }
    }
}
