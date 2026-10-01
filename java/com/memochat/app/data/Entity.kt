package com.memochat.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 一条"条目" = 一个类似微信聊天的会话。
 * 顶部的"备注"可自定义为日期 / 天气 / 心情等组合。
 */
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,      // 顶部备注 / 标题
    val date: String,       // 日期，如 2026-09-28
    val weather: String,    // 天气：晴/多云/阴/雨/雪/雾
    val mood: String,       // 心情：开心/平静/疲惫/难过/兴奋/生气
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val avatarPath: String = "",
    val type: String = "日记",
    val deletedAt: Long = 0
) {
    /** 会话预览用的子标题 */
    fun tags(): String {
        return listOf(weather, mood).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

/** 消息类型 */
object MsgType {
    const val TEXT = 0
    const val IMAGE = 1
    const val EMOJI = 2
    const val VOICE = 3
}

/**
 * 条目内的一条消息（文字 / 表情 / 图片）。
 */
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = Entry::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [androidx.room.Index("entryId")]
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val type: Int,
    val content: String,    // 文本 / 表情字符 / 图片文件路径
    val createdAt: Long,
    val edited: Boolean = false,
    val starred: Boolean = false,
    val isBackfill: Boolean = false,
    val deletedAt: Long = 0
)

/** 每天的天气心情（全局，按日期） */
@Entity(tableName = "day_meta", primaryKeys = ["dayKey"])
data class DayMeta(
    val dayKey: String,  // 2026-09-28
    val weather: String = "",
    val mood: String = ""
)

/** 时光胶囊 */
@Entity(tableName = "capsules")
data class TimeCapsule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createdAt: Long,
    val unlockAt: Long,
    val entryId: Long = 0,
    val unlocked: Boolean = false
)
