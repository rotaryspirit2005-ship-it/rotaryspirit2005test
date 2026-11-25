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
    const val WHOLE_MAP = "wholeMap"
    const val DETAIL = "detail"

    val pending = MutableStateFlow<String?>(null)

    /**
     * やること一覧を開くIntent。PendingIntentはextraの違いを区別しないため、
     * 画面ごとにdataのURIを変えて別物として扱わせる。アプリが起動済みなら新しく重ねず、
     * 既存の画面にonNewIntentで届ける(重ねると古い方が先に指定を受け取り、月画面のままになる)。
     */
    fun todosIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("schedulelink://$TODOS"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** 全体マップを開くIntent(ウィジェットのタイムラインから)。 */
    fun wholeMapIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("schedulelink://$WHOLE_MAP"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** 予定の詳細を開くIntent(通知から)。予定ごとにURIを変えて、別々の通知として扱わせる。 */
    fun scheduleDetailIntent(context: Context, scheduleId: String): Intent =
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("schedulelink://$DETAIL/$scheduleId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** 開く画面。予定の詳細は「detail/予定のID」、それ以外は画面の名前だけ。 */
    fun destinationOf(intent: Intent?): String? {
        val data = intent?.data?.takeIf { it.scheme == "schedulelink" } ?: return null
        val host = data.host ?: return null
        val id = data.pathSegments.firstOrNull()
        return if (host == DETAIL && id != null) "$DETAIL/$id" else host
    }
}
