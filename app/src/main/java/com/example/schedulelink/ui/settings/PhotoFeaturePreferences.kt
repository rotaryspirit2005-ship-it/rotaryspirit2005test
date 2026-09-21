package com.example.schedulelink.ui.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "photo_feature_prefs"
private const val KEY_ENABLED = "is_photo_feature_enabled"

/**
 * 写真添付機能のON/OFF設定。Firebase Storageは無料枠を超えると課金が
 * 発生しうるため、既定ではOFFにしておき、ユーザーが明示的に有効化した
 * 場合だけ写真関連のUIを表示する。
 */
class PhotoFeaturePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
