package com.example.schedulelink.ui.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.WidgetPreferences
import com.example.schedulelink.reminder.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestore

/** ウィジェットの定期更新(手動更新ボタンとは別に、放置していても内容が古くならないようにする)。 */
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            refreshAgendaWidgets(applicationContext)
            refreshMonthMiniWidgets(applicationContext)
            // 家族が予定の日時を変えた場合も、通知の時刻を合わせ直す(30分おき)
            WidgetPreferences(applicationContext).familyId?.let { familyId ->
                runCatching {
                    val schedules = ScheduleRepository(FirebaseFirestore.getInstance(), familyId).allSchedulesOnce()
                    ReminderScheduler(applicationContext).reconcile(schedules)
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
