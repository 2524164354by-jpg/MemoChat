package com.memochat.app.util

import android.content.Context

/** 外观模式 */
object Mode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/**
 * 本地偏好存储（全部数据仅保存在本机，不联网）。
 */
object Prefs {
    private const val FILE = "memochat_prefs"
    private const val KEY_MODE = "ui_mode"
    private const val KEY_AVATAR = "avatar_path"
    private const val KEY_BG = "chat_bg_path"
    private const val KEY_VERSION = "version"

    fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var mode: Int
        get() = prefs(getApp()).getInt(KEY_MODE, Mode.SYSTEM)
        set(v) { prefs(getApp()).edit().putInt(KEY_MODE, v).apply() }

    var avatarPath: String
        get() = prefs(getApp()).getString(KEY_AVATAR, "") ?: ""
        set(v) { prefs(getApp()).edit().putString(KEY_AVATAR, v).apply() }

    var chatBgPath: String
        get() = prefs(getApp()).getString(KEY_BG, "") ?: ""
        set(v) { prefs(getApp()).edit().putString(KEY_BG, v).apply() }

    private const val KEY_APP_BG = "app_bg_path"
    var appBgPath: String
        get() = prefs(getApp()).getString(KEY_APP_BG, "") ?: ""
        set(v) { prefs(getApp()).edit().putString(KEY_APP_BG, v).apply() }

    var version: String
        get() = prefs(getApp()).getString(KEY_VERSION, "1.0.0") ?: "1.0.0"
        set(v) { prefs(getApp()).edit().putString(KEY_VERSION, v).apply() }

    var onboardingDone: Boolean
        get() = prefs(getApp()).getBoolean("onboarding_done", false)
        set(v) { prefs(getApp()).edit().putBoolean("onboarding_done", v).apply() }

    var enterSend: Boolean
        get() = prefs(getApp()).getBoolean("enter_send", false)
        set(v) { prefs(getApp()).edit().putBoolean("enter_send", v).apply() }

    var quickWriteDrafts: String
        get() = prefs(getApp()).getString("quick_drafts", "[]") ?: "[]"
        set(v) { prefs(getApp()).edit().putString("quick_drafts", v).apply() }

    var lastQuickRemind: Long
        get() = prefs(getApp()).getLong("quick_last_remind", 0L)
        set(v) { prefs(getApp()).edit().putLong("quick_last_remind", v).apply() }

    var todayQAnswered: String
        get() = prefs(getApp()).getString("today_q_answered", "[]") ?: "[]"
        set(v) { prefs(getApp()).edit().putString("today_q_answered", v).apply() }


    fun saveDraft(entryId: Long, text: String) { prefs(getApp()).edit().putString("draft_$entryId", text).apply() }
    fun getDraft(entryId: Long): String = prefs(getApp()).getString("draft_$entryId", "") ?: ""
    fun clearDraft(entryId: Long) { prefs(getApp()).edit().remove("draft_$entryId").apply() }

    @Volatile
    private var APP: Context? = null
    fun init(context: Context) { APP = context.applicationContext }
    private fun getApp(): Context = APP ?: throw IllegalStateException("Prefs not initialized")
}
