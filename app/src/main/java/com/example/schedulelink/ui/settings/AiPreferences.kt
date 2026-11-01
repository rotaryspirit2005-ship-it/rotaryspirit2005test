package com.example.schedulelink.ui.settings

import android.content.Context

private const val PREFS_NAME = "ai_prefs"
private const val KEY_API_KEY = "anthropic_api_key"
private const val KEY_CONSENT = "send_consent"

/**
 * 「表で計画」のAIで分解に使う、利用者自身のAnthropic APIキーと送信への同意。
 * この端末にだけ保存し、Firestore(家族)には共有しない。料金はキーの持ち主に請求される。
 */
class AiPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var consent: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_CONSENT, value).apply()

    val isReady: Boolean get() = consent && apiKey.isNotBlank()

    fun clear() {
        prefs.edit().remove(KEY_API_KEY).putBoolean(KEY_CONSENT, false).apply()
    }
}
