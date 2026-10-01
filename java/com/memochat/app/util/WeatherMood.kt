package com.memochat.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 天气 / 心情候选值与标题构建 */
object WeatherMood {
    val weatherOptions = listOf("晴", "多云", "阴", "雨", "雪", "雾")
    val moodOptions = listOf("开心", "平静", "疲惫", "难过", "兴奋", "生气")

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    /** 顶部的"备注"标题：日期 + 天气 + 心情，可自定义追加 */
    fun buildTitle(date: String, weather: String, mood: String): String {
        val parts = listOf(date, weather, mood).filter { it.isNotBlank() }
        return if (parts.isEmpty()) "未命名条目" else parts.joinToString(" ")
    }
}
