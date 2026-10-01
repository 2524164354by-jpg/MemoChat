package com.memochat.app.util

import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    fun apply(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                Mode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Mode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
