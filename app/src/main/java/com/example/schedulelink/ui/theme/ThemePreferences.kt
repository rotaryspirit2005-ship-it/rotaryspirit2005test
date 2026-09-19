package com.example.schedulelink.ui.theme

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "theme_prefs"
private const val KEY_DARK_THEME = "is_dark_theme"

/**
 * ダークモード/ライトモードの手動切り替えを保存する。
 * 初回起動時だけ端末のシステム設定に合わせ、以降はユーザーがアプリ内で
 * 選んだ方を優先する(端末の設定を後で変えてもアプリ側の選択は変わらない)。
 */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val systemDefaultIsDark =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, systemDefaultIsDark))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun setDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        prefs.edit().putBoolean(KEY_DARK_THEME, isDark).apply()
    }
}
