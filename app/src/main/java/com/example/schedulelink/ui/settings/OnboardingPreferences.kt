package com.example.schedulelink.ui.settings

import android.content.Context

private const val PREFS_NAME = "onboarding_prefs"
private const val KEY_WELCOME_SEEN = "welcome_seen"

/** 初めて開いたときの「はじめに」を、もう見せたか。端末ごとに1度だけ出す。 */
class OnboardingPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var welcomeSeen: Boolean
        get() = prefs.getBoolean(KEY_WELCOME_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOME_SEEN, value).apply()
}
