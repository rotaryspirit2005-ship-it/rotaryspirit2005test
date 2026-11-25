package com.example.schedulelink.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.schedulelink.R
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.WidgetPreferences
import com.example.schedulelink.ui.navigation.DeepLinks
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

// 予定の通知。この端末だけで鳴らす(家族には届かない・サーバーも料金も使わない)。
// 時刻ちょうどの通知には特別な許可が要るため、WorkManagerで「その頃」に出す(数分遅れることがある)。

const val REMINDER_CHANNEL_ID = "schedule_reminders"

/** 通知のタイミングの選択肢。 */
enum class ReminderOption(val label: String) {
    Min30("30分前"),
    Hour1("1時間前"),
    Morning("当日の朝 8:00"),
    EveningBefore("前日の夜 20:00");

    /** この予定で通知を出す日時。 */
    fun triggerAt(schedule: ScheduleEntity): LocalDateTime {
        val start = LocalDateTime.of(schedule.date, schedule.startTime)
        return when (this) {
            Min30 -> start.minusMinutes(30)
            Hour1 -> start.minusHours(1)
            Morning -> LocalDateTime.of(schedule.date, LocalTime.of(8, 0))
            EveningBefore -> LocalDateTime.of(schedule.date.minusDays(1), LocalTime.of(20, 0))
        }
    }
}

/** 予定ごとの通知の選択(この端末だけに保存。Firestoreには書かない)。 */
class ReminderPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)

    fun optionOf(scheduleId: String): ReminderOption? =
        prefs.getString("opt_$scheduleId", null)?.let { name -> ReminderOption.entries.firstOrNull { it.name == name } }

    fun setOption(scheduleId: String, option: ReminderOption?) {
        prefs.edit().apply {
            if (option == null) {
                remove("opt_$scheduleId")
                remove("at_$scheduleId")
            } else {
                putString("opt_$scheduleId", option.name)
            }
        }.apply()
    }

    /** 最後に予約した通知の時刻(同じなら予約し直さない)。 */
    fun scheduledAt(scheduleId: String): Long = prefs.getLong("at_$scheduleId", 0L)

    fun setScheduledAt(scheduleId: String, epochMillis: Long) {
        prefs.edit().putLong("at_$scheduleId", epochMillis).apply()
    }

    fun clearScheduledAt(scheduleId: String) {
        prefs.edit().remove("at_$scheduleId").apply()
    }

    fun scheduleIdsWithReminder(): List<String> =
        prefs.all.keys.filter { it.startsWith("opt_") }.map { it.removePrefix("opt_") }
}

/** 通知の予約・取り消し。予定が変わったら、[reconcile]で時刻を合わせ直す。 */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = ReminderPreferences(appContext)
    private val workManager get() = WorkManager.getInstance(appContext)

    private fun workName(id: String) = "reminder_$id"

    /** この予定の通知を、いまの日時と選択に合わせて予約する(選択なし・過去なら取り消す)。 */
    fun schedule(schedule: ScheduleEntity) {
        val option = prefs.optionOf(schedule.id)
        if (option == null) {
            cancel(schedule.id)
            return
        }
        val at = option.triggerAt(schedule).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val delay = at - System.currentTimeMillis()
        // 時刻を過ぎていても取り消さない(省電力中は少し遅れて動くことがあり、その通知を消してしまうため)
        if (delay <= 0) return
        if (prefs.scheduledAt(schedule.id) == at) return
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to schedule.id, ReminderWorker.KEY_TITLE to schedule.title))
            .build()
        workManager.enqueueUniqueWork(workName(schedule.id), ExistingWorkPolicy.REPLACE, request)
        prefs.setScheduledAt(schedule.id, at)
    }

    /** 予約だけ取り消す(選択は残す。削除を「元に戻す」と、また予約される)。 */
    fun cancel(scheduleId: String) {
        workManager.cancelUniqueWork(workName(scheduleId))
        prefs.clearScheduledAt(scheduleId)
    }

    /** 家族の変更や日付のずらしも反映するため、通知を選んだ予定の時刻を合わせ直す。 */
    fun reconcile(schedules: List<ScheduleEntity>) {
        val byId = schedules.associateBy { it.id }
        prefs.scheduleIdsWithReminder().forEach { id ->
            val schedule = byId[id]
            if (schedule == null) cancel(id) else schedule(schedule)
        }
    }
}

/** 通知を出すかどうかを、いまのアプリの許可で確かめる。 */
fun canPostNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

fun createReminderChannel(context: Context) {
    val channel = NotificationChannel(REMINDER_CHANNEL_ID, "予定の通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "予定ごとに選んだ時刻の頃にお知らせします"
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

private val bodyDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E) H:mm", Locale.JAPAN)

/** 予約した頃に動き、予定を読み直してから通知する(消された・時刻が変わった予定では出さない)。 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val prefs = ReminderPreferences(applicationContext)
        prefs.clearScheduledAt(id)
        val option = prefs.optionOf(id) ?: return Result.success()
        val familyId = WidgetPreferences(applicationContext).familyId
        // ウィジェットと同じく、1度きりの取得(collection.get系)で最新を読む
        val schedule = familyId?.let {
            runCatching { ScheduleRepository(FirebaseFirestore.getInstance(), it).scheduleByIdOnce(id) }.getOrNull()
        }
        if (familyId != null && schedule == null) {
            // 予定が消されていたら通知しない(読めなかっただけのときは、予約していた題名で出す)
            val readable = runCatching { ScheduleRepository(FirebaseFirestore.getInstance(), familyId).existsOnce(id) }.getOrNull()
            if (readable == false) return Result.success()
        }
        if (schedule != null) {
            val trigger = option.triggerAt(schedule)
            // 家族が日時を変えていたら、新しい時刻に予約し直す
            if (Duration.between(LocalDateTime.now(), trigger).toMinutes() > 10) {
                ReminderScheduler(applicationContext).schedule(schedule)
                return Result.success()
            }
        }
        if (!canPostNotifications(applicationContext)) return Result.success()
        val title = schedule?.title ?: inputData.getString(KEY_TITLE) ?: "予定"
        val body = schedule?.let { s ->
            buildString {
                append(LocalDateTime.of(s.date, s.startTime).format(bodyDateFormatter))
                append("〜")
                append(s.endTime.format(DateTimeFormatter.ofPattern("H:mm")))
                if (s.location.isNotBlank()) append("・").append(s.location)
            }
        } ?: "まもなく予定があります"
        val intent = DeepLinks.scheduleDetailIntent(applicationContext, id)
        val pending = PendingIntent.getActivity(
            applicationContext, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
        } catch (e: SecurityException) {
            // 許可が途中で外されたときは出さない
        }
        return Result.success()
    }

    companion object {
        const val KEY_ID = "scheduleId"
        const val KEY_TITLE = "title"
    }
}
