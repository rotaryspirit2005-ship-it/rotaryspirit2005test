package com.example.schedulelink.data

import android.content.Context
import java.time.Instant

private const val PREFS_NAME = "widget_debug_log"
private const val KEY_LOG = "log"
private const val MAX_ENTRIES = 30

/**
 * ウィジェット(バックグラウンドで動くGlanceのコード)は、画面に何も表示されない
 * まま失敗することがあり、通常の画面表示だけでは「タップがそもそも届いているか」
 * 「届いた上で何が失敗しているか」を切り分けられない。
 * ここに簡単な足跡(breadcrumb)とエラーを記録しておき、アプリ本編の
 * 「ウィジェットログ」画面からいつでも確認できるようにする。
 */
object WidgetErrorLog {
    fun recordInfo(context: Context, tag: String, message: String) {
        append(context, "${Instant.now()} [$tag] $message")
    }

    fun record(context: Context, tag: String, e: Throwable) {
        append(context, "${Instant.now()} [$tag] ${e::class.simpleName}: ${e.message}")
    }

    private fun append(context: Context, entry: String) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_LOG, "").orEmpty()
            val lines = (listOf(entry) + existing.lines().filter { it.isNotBlank() }).take(MAX_ENTRIES)
            prefs.edit().putString(KEY_LOG, lines.joinToString("\n")).apply()
        }
    }

    fun readAll(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LOG, "").orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
