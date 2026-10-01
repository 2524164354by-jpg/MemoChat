package com.memochat.app.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.Entry
import com.memochat.app.util.BitmapUtil
import com.memochat.app.util.WeatherMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 新建条目的底部弹窗：自定义头像 / 标题 / 日期 / 天气 / 心情。
 */
object NewEntryDialog {

    /** 由 MainActivity 在相册选完后回填；重新 show 时会带上 */
    var pendingAvatarPath: String = ""

    /** 由 MainActivity 注入：点击头像时调用，打开相册 */
    var onPickAvatar: ((Context) -> Unit)? = null

    private var currentAvatarIv: ImageView? = null
    private var pendingOnCreated: ((Long) -> Unit)? = null
    private var pendingContext: Context? = null

    fun show(context: Context, onCreated: (entryId: Long) -> Unit) {
        pendingOnCreated = onCreated
        pendingContext = context
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_new_entry, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val inputTitle = view.findViewById<android.widget.EditText>(R.id.input_title)
        val typeRow = view.findViewById<LinearLayout>(R.id.type_row)
        val avatarIv = view.findViewById<ImageView>(R.id.pick_avatar)
        currentAvatarIv = avatarIv

        if (pendingAvatarPath.isNotEmpty()) {
            BitmapUtil.loadAvatarRound(avatarIv, pendingAvatarPath, 144)
        }

        avatarIv.setOnClickListener {
            onPickAvatar?.invoke(context)
            dialog.dismiss()
        }


        fun buildChips(row: LinearLayout, options: List<String>, onPick: (String) -> Unit) {
            row.removeAllViews()
            options.forEach { option ->
                val chip = TextView(context).apply {
                    text = option
                    textSize = 13f
                    setTextColor(context.getColor(R.color.text_primary))
                    background = context.getDrawable(R.drawable.bg_chip)
                    setPadding(
                        dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6)
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(context, 8) }
                    isClickable = true
                    setOnClickListener { onPick(option) }
                }
                row.addView(chip)
            }
        }

        val typeOptions = mutableListOf("日记", "吐槽", "工作")
        var selectedType = "日记"
        fun renderTypeChips() {
            typeRow.removeAllViews()
            typeOptions.forEach { opt ->
                val chip = TextView(context).apply {
                    text = opt
                    textSize = 13f
                    setTextColor(context.getColor(R.color.text_primary))
                    background = context.getDrawable(R.drawable.bg_chip)
                    setPadding(dp(context,14), dp(context,6), dp(context,14), dp(context,6))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(context,8) }
                    isClickable = true
                    setOnClickListener { selectedType = opt; highlight(typeRow, opt) }
                }
                typeRow.addView(chip)
            }
            // add "+" chip
            val addChip = TextView(context).apply {
                text = "+"
                textSize = 14f
                setTextColor(context.getColor(R.color.wx_green))
                background = context.getDrawable(R.drawable.bg_chip)
                setPadding(dp(context,14), dp(context,6), dp(context,14), dp(context,6))
                isClickable = true
                setOnClickListener {
                    val et = android.widget.EditText(context)
                    android.app.AlertDialog.Builder(context)
                        .setTitle("自定义类型")
                        .setView(et)
                        .setPositiveButton("添加") { _, _ ->
                            val t = et.text.toString().trim()
                            if (t.isNotEmpty() && !typeOptions.contains(t)) {
                                typeOptions.add(t)
                                renderTypeChips()
                                selectedType = t
                                highlight(typeRow, t)
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            typeRow.addView(addChip)
        }
        renderTypeChips()

        view.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            pendingAvatarPath = ""
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.btn_confirm).setOnClickListener {
            val userTitle = inputTitle.text.toString().trim()
            val title = if (userTitle.isEmpty()) selectedType else userTitle
            val date = WeatherMood.today()
            val now = System.currentTimeMillis()
            val entry = Entry(
                title = title,
                date = date,
                weather = "",
                mood = "",
                createdAt = now,
                updatedAt = now,
                avatarPath = pendingAvatarPath,
                type = selectedType
            )
            CoroutineScope(Dispatchers.IO).launch {
                val id = AppDatabase.get(context).entryDao().insert(entry)
                android.os.Handler(context.mainLooper).post {
                    pendingAvatarPath = ""
                    dialog.dismiss()
                    onCreated(id)
                }
            }
        }
        dialog.show()
    }

    /** 相册选完后回填头像并重新弹窗 */
    fun onPicked(uri: Uri?) {
        val ctx = pendingContext ?: return
        if (uri == null) {
            show(ctx, pendingOnCreated ?: {})
            return
        }
        val path = BitmapUtil.copyToPrivate(ctx, uri, "entry_avatar")
        if (path != null) {
            pendingAvatarPath = path
        }
        Toast.makeText(ctx, "头像已选择", Toast.LENGTH_SHORT).show()
        show(ctx, pendingOnCreated ?: {})
    }

    private fun highlight(row: LinearLayout, picked: String) {
        for (i in 0 until row.childCount) {
            val chip = row.getChildAt(i) as TextView
            chip.isSelected = chip.text.toString() == picked
            chip.setTextColor(
                if (chip.isSelected) Color.WHITE
                else chip.context.getColor(R.color.text_primary)
            )
        }
    }

    private fun dp(c: Context, v: Int): Int =
        (v * c.resources.displayMetrics.density).toInt()
}
