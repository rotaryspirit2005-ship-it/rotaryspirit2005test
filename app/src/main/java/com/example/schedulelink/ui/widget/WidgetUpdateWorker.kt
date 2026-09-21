package com.example.schedulelink.ui.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** ウィジェットの定期更新(手動更新ボタンとは別に、放置していても内容が古くならないようにする)。 */
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            AgendaWidget().updateAll(applicationContext)
            MonthMiniWidget().updateAll(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
