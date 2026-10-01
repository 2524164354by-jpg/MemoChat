package com.memochat.app.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.InputStream
import java.util.regex.Pattern

object EmojiGrid {

    private const val PREF = "emoji_pref"
    private const val KEY_RECENT = "recent"

    data class WxEmoji(val name: String, val file: String)

    private val EMOJIS: List<WxEmoji> = listOf(
        WxEmoji("微笑", "face_微笑.png"), WxEmoji("撇嘴", "face_撇嘴.png"), WxEmoji("色", "face_色.png"),
        WxEmoji("发呆", "face_发呆.png"), WxEmoji("得意", "face_得意.png"), WxEmoji("流泪", "face_流泪.png"),
        WxEmoji("害羞", "face_害羞.png"), WxEmoji("闭嘴", "face_闭嘴.png"), WxEmoji("睡", "face_睡.png"),
        WxEmoji("大哭", "face_大哭.png"), WxEmoji("尴尬", "face_尴尬.png"), WxEmoji("发怒", "face_发怒.png"),
        WxEmoji("调皮", "face_调皮.png"), WxEmoji("呲牙", "face_呲牙.png"), WxEmoji("惊讶", "face_惊讶.png"),
        WxEmoji("难过", "face_难过.png"), WxEmoji("囧", "face_囧.png"), WxEmoji("抓狂", "face_抓狂.png"),
        WxEmoji("吐", "face_吐.png"), WxEmoji("偷笑", "face_偷笑.png"), WxEmoji("愉快", "face_愉快.png"),
        WxEmoji("白眼", "face_白眼.png"), WxEmoji("傲慢", "face_傲慢.png"), WxEmoji("困", "face_困.png"),
        WxEmoji("惊恐", "face_惊恐.png"), WxEmoji("憨笑", "face_憨笑.png"), WxEmoji("悠闲", "face_悠闲.png"),
        WxEmoji("咒骂", "face_咒骂.png"), WxEmoji("疑问", "face_疑问.png"), WxEmoji("嘘", "face_嘘.png"),
        WxEmoji("晕", "face_晕.png"), WxEmoji("衰", "face_衰.png"), WxEmoji("骷髅", "face_骷髅.png"),
        WxEmoji("敲打", "face_敲打.png"), WxEmoji("再见", "face_再见.png"), WxEmoji("擦汗", "face_擦汗.png"),
        WxEmoji("抠鼻", "face_抠鼻.png"), WxEmoji("鼓掌", "face_鼓掌.png"), WxEmoji("坏笑", "face_坏笑.png"),
        WxEmoji("右哼哼", "face_右哼哼.png"), WxEmoji("鄙视", "face_鄙视.png"), WxEmoji("委屈", "face_委屈.png"),
        WxEmoji("快哭了", "face_快哭了.png"), WxEmoji("阴险", "face_阴险.png"), WxEmoji("亲亲", "face_亲亲.png"),
        WxEmoji("可怜", "face_可怜.png"), WxEmoji("笑脸", "face_笑脸.png"), WxEmoji("生病", "face_生病.png"),
        WxEmoji("脸红", "face_脸红.png"), WxEmoji("破涕为笑", "face_破涕为笑.png"), WxEmoji("恐惧", "face_恐惧.png"),
        WxEmoji("失望", "face_失望.png"), WxEmoji("无语", "face_无语.png"), WxEmoji("嘿哈", "face_嘿哈.png"),
        WxEmoji("捂脸", "face_捂脸.png"), WxEmoji("机智", "face_机智.png"), WxEmoji("皱眉", "face_皱眉.png"),
        WxEmoji("耶", "face_耶.png"), WxEmoji("吃瓜", "face_吃瓜.png"), WxEmoji("加油", "face_加油.png"),
        WxEmoji("汗", "face_汗.png"), WxEmoji("天啊", "face_天啊.png"), WxEmoji("Emm", "face_Emm.png"),
        WxEmoji("社会社会", "face_社会社会.png"), WxEmoji("旺柴", "face_旺柴.png"), WxEmoji("好的", "face_好的.png"),
        WxEmoji("打脸", "face_打脸.png"), WxEmoji("哇", "face_哇.png"), WxEmoji("翻白眼", "face_翻白眼.png"),
        WxEmoji("666", "face_666.png"), WxEmoji("让我看看", "face_让我看看.png"), WxEmoji("叹气", "face_叹气.png"),
        WxEmoji("苦涩", "face_苦涩.png"), WxEmoji("裂开", "face_裂开.png"), WxEmoji("奸笑", "face_奸笑.png"),
        WxEmoji("握手", "gesture_握手.png"), WxEmoji("胜利", "gesture_胜利.png"), WxEmoji("抱拳", "gesture_抱拳.png"),
        WxEmoji("勾引", "gesture_勾引.png"), WxEmoji("拳头", "gesture_拳头.png"), WxEmoji("OK", "gesture_OK.png"),
        WxEmoji("合十", "gesture_合十.png"), WxEmoji("强", "gesture_强.png"), WxEmoji("拥抱", "gesture_拥抱.png"),
        WxEmoji("弱", "gesture_弱.png"),
        WxEmoji("猪头", "animal_猪头.png"), WxEmoji("跳跳", "animal_跳跳.png"),
        WxEmoji("发抖", "animal_发抖.png"), WxEmoji("转圈", "animal_转圈.png"),
        WxEmoji("庆祝", "blessing_庆祝.png"), WxEmoji("红包", "blessing_红包.png"),
        WxEmoji("烟花", "blessing_烟花.png"), WxEmoji("爆竹", "blessing_爆竹.png"), WxEmoji("福", "blessing_福.png"),
        WxEmoji("菜刀", "other_菜刀.png"), WxEmoji("炸弹", "other_炸弹.png"), WxEmoji("便便", "other_便便.png"),
        WxEmoji("太阳", "other_太阳.png"), WxEmoji("月亮", "other_月亮.png"), WxEmoji("玫瑰", "other_玫瑰.png"),
        WxEmoji("爱心", "other_爱心.png"), WxEmoji("啤酒", "other_啤酒.png"), WxEmoji("蛋糕", "other_蛋糕.png"),
        WxEmoji("咖啡", "other_咖啡.png")
    )

    private fun getRecent(ctx: Context): List<String> {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sp.getString(KEY_RECENT, "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    fun recordRecent(ctx: Context, name: String) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val list = getRecent(ctx).toMutableList()
        list.remove(name)
        list.add(0, name)
        if (list.size > 16) list.subList(16, list.size).clear()
        sp.edit().putString(KEY_RECENT, list.joinToString(",")).apply()
    }

    fun build(context: Context, container: ViewGroup, onPick: (String) -> Unit) {
        container.removeAllViews()
        val columns = 8
        val screenW = context.resources.displayMetrics.widthPixels
        val cell = (screenW / columns.toFloat()).toInt()
        val density = context.resources.displayMetrics.density

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val recent = getRecent(context)
        if (recent.isNotEmpty()) {
            val label = TextView(context).apply {
                text = "最近使用"
                textSize = 11f
                setTextColor(0xFF888888.toInt())
                setPadding((12*density).toInt(), (8*density).toInt(), 0, (4*density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            mainLayout.addView(label)
            val recentGrid = GridLayout(context).apply { columnCount = columns }
            recent.take(columns).forEach { name ->
                EMOJIS.find { it.name == name }?.let { emoji ->
                    recentGrid.addView(makeCell(context, emoji, cell, onPick))
                }
            }
            mainLayout.addView(recentGrid)
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (0.5f * density).toInt()
                ).apply { topMargin = (8*density).toInt(); bottomMargin = (8*density).toInt() }
                setBackgroundColor(0x22888888.toInt())
            }
            mainLayout.addView(divider)
        }

        val allGrid = GridLayout(context).apply { columnCount = columns }
        EMOJIS.forEach { emoji ->
            allGrid.addView(makeCell(context, emoji, cell, onPick))
        }
        mainLayout.addView(allGrid)

        val sv = android.widget.ScrollView(context)
        sv.addView(mainLayout)
        container.addView(sv)
    }

    private fun makeCell(ctx: Context, emoji: WxEmoji, cell: Int, onPick: (String) -> Unit): View {
        return ImageView(ctx).apply {
            try {
                val ins: InputStream = ctx.assets.open("wechat_emoji/${emoji.file}")
                val d = Drawable.createFromStream(ins, null)
                setImageDrawable(d)
            } catch (e: Exception) {
                visibility = View.GONE
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = (cell * 0.16f).toInt()
            setPadding(p, p, p, p)
            layoutParams = GridLayout.LayoutParams().apply {
                width = cell
                height = cell
            }
            isClickable = true
            setOnClickListener {
                recordRecent(ctx, emoji.name)
                onPick("[${emoji.name}]")
            }
        }
    }

    private val fileMap: Map<String, String> by lazy {
        EMOJIS.associate { it.name to it.file }
    }

    fun render(ctx: Context, text: String): CharSequence {
        val pattern = Pattern.compile("\\[([^\\[\\]]{1,10})\\]")
        val m = pattern.matcher(text)
        val sb = SpannableString(text)
        val density = ctx.resources.displayMetrics.density
        val size = (18 * density).toInt()
        while (m.find()) {
            val name = m.group(1) ?: continue
            val file = fileMap[name] ?: continue
            try {
                val ins: InputStream = ctx.assets.open("wechat_emoji/$file")
                val d = Drawable.createFromStream(ins, null) ?: continue
                d.setBounds(0, 0, size, size)
                val span = ImageSpan(d, ImageSpan.ALIGN_BOTTOM)
                sb.setSpan(span, m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } catch (e: Exception) {}
        }
        return sb
    }
}
