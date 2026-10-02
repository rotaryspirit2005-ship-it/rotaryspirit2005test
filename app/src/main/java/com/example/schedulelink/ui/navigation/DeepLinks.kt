package com.example.schedulelink.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.schedulelink.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ウィジェットなどアプリの外から、特定の画面を開くための受け渡し。MainActivityが起動時に
 * 開きたい画面をここへ置き、ScheduleNavHost(サインイン・家族の確認が済んだ後)が1度だけ遷移する。
 */
object DeepLinks {
    const val TODOS = "todos"

    val pending = MutableStateFlow<String?>(null)

    /**
     * やること一覧を開くIntent。PendingIntentはextraの違いを区別しないため、
     * 画面ごとにdataのURIを変えて別物として扱わせる。
     */
    fun todosIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).setData(Uri.parse("schedulelink://$TODOS"))

    fun destinationOf(intent: Intent?): String? = intent?.data?.takeIf { it.scheme == "schedulelink" }?.host
}
