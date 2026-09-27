package com.example.schedulelink.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.schedulelink.MainActivity
import com.example.schedulelink.ScheduleLinkApplication
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.WidgetErrorLog
import com.example.schedulelink.data.WidgetPreferences
import com.example.schedulelink.ui.common.commonTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val agendaDateFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)

/** ホーム画面ウィジェット: 今日から1週間分の直近の予定を、日付ごとに区切って表示する。 */
class AgendaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetErrorLog.recordInfo(context, "AgendaWidget", "provideGlance開始")
        var schedules: List<ScheduleEntity> = emptyList()
        var isSignedIn = false
        try {
            val app = context.applicationContext as ScheduleLinkApplication
            val familyId = WidgetPreferences(context).familyId
            isSignedIn = familyId != null
            if (familyId != null) {
                // ウィジェット更新はOSの実行時間制約を受けるため、タイムアウトを設けて
                // 必ず表示を完了させる。タイムアウト(キャンセル)は握りつぶさずに伝播させる。
                val completed = withTimeoutOrNull(8_000) {
                    try {
                        val repo = ScheduleRepository(app.firestore, familyId)
                        val today = LocalDate.now()
                        schedules = repo.schedulesInRangeOnce(today, today.plusDays(6))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        WidgetErrorLog.record(context, "AgendaWidget.fetch", e)
                    }
                    true
                }
                if (completed == null) {
                    WidgetErrorLog.recordInfo(context, "AgendaWidget", "データ取得タイムアウト")
                }
            }
            WidgetErrorLog.recordInfo(context, "AgendaWidget", "データ取得完了: schedules=${schedules.size}件")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WidgetErrorLog.record(context, "AgendaWidget.provideGlance", e)
        }

        provideContent {
            AgendaWidgetContent(schedules = schedules, isSignedIn = isSignedIn)
        }
        WidgetErrorLog.recordInfo(context, "AgendaWidget", "provideGlance完了")
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

class AgendaRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetErrorLog.recordInfo(context, "AgendaRefreshAction", "onAction呼び出された")
        try {
            AgendaWidget().update(context, glanceId)
            WidgetErrorLog.recordInfo(context, "AgendaRefreshAction", "update()完了")
        } catch (e: Throwable) {
            WidgetErrorLog.record(context, "AgendaRefreshAction", e)
        }
    }
}

private sealed interface AgendaEntry {
    data class DayHeader(val date: LocalDate) : AgendaEntry
    data class Item(val schedule: ScheduleEntity) : AgendaEntry
}

private fun agendaDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今日"
    today.plusDays(1) -> "明日"
    else -> date.format(agendaDateFormatter)
}

// 予定の件数ぶん縦に並べると、小さいサイズのウィジェットでははみ出して切れるうえ、
// Glanceは1つのColumnにつき子を10個までしか描画しない。一覧はLazyColumnでスクロールさせる。
@Composable
private fun AgendaWidgetContent(schedules: List<ScheduleEntity>, isSignedIn: Boolean) {
    val context = LocalContext.current
    val openAppAction = actionStartActivity(Intent(context, MainActivity::class.java))
    val today = LocalDate.now()
    val entries: List<AgendaEntry> = schedules
        .groupBy { it.date }
        .toSortedMap()
        .flatMap { (date, items) -> listOf(AgendaEntry.DayHeader(date)) + items.map { AgendaEntry.Item(it) } }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "直近の予定",
                style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                modifier = GlanceModifier.defaultWeight().clickable(openAppAction)
            )
            Text(
                text = "更新",
                style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<AgendaRefreshAction>())
            )
        }
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            val message = when {
                !isSignedIn -> "タップしてアプリでサインインしてください"
                entries.isEmpty() -> "直近の予定はありません"
                else -> null
            }
            if (message != null) {
                Text(
                    text = message,
                    style = TextStyle(color = WidgetSubText, fontSize = 13.sp),
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(openAppAction)
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(entries, itemId = { entry ->
                        when (entry) {
                            is AgendaEntry.DayHeader -> entry.date.toEpochDay()
                            // 見出しのID(エポック日数、数万程度)と衝突しないよう、予定は大きな値の範囲に寄せる。
                            is AgendaEntry.Item -> (entry.schedule.id.hashCode().toLong() and 0xFFFFFFFFL) + (1L shl 40)
                        }
                    }) { entry ->
                        when (entry) {
                            is AgendaEntry.DayHeader -> Text(
                                text = agendaDayLabel(entry.date, today),
                                style = TextStyle(
                                    color = if (entry.date == today) WidgetAccent else WidgetSubText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                            is AgendaEntry.Item -> Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable(openAppAction),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.schedule.startTime.format(commonTimeFormatter),
                                    style = TextStyle(color = WidgetSubText, fontSize = 12.sp),
                                    modifier = GlanceModifier.width(44.dp)
                                )
                                Text(
                                    text = entry.schedule.title,
                                    style = TextStyle(color = WidgetOnBackground, fontSize = 13.sp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
