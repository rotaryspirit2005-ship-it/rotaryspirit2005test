package com.example.schedulelink.data

import android.content.Context

private const val PREFS_NAME = "widget_prefs"
private const val KEY_FAMILY_ID = "family_id"

/**
 * ホーム画面ウィジェットは、アプリ本編のサインイン画面や家族選択の
 * Composeツリーの外(バックグラウンド)から動くため、現在の家族IDだけを
 * ここに保存しておき、ウィジェット側はFirestoreへ直接問い合わせる際に使う。
 */
class WidgetPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var familyId: String?
        get() = prefs.getString(KEY_FAMILY_ID, null)
        set(value) { prefs.edit().putString(KEY_FAMILY_ID, value).apply() }
}
