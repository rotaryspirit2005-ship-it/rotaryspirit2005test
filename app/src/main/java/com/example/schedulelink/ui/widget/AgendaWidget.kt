package com.example.schedulelink.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.example.schedulelink.data.WidgetPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val agendaDateFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)

/** ホーム画面ウィジェット: 今日から1週間分の直近の予定を最大5件表示する。 */
class AgendaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ScheduleLinkApplication
        val familyId = WidgetPreferences(context).familyId
        val schedules: List<ScheduleEntity> = if (familyId != null) {
            runCatching {
                val repo = ScheduleRepository(app.firestore, familyId)
                val today = LocalDate.now()
                repo.schedulesInRange(today, today.plusDays(6)).first()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        provideContent {
            AgendaWidgetContent(schedules = schedules, isSignedIn = familyId != null)
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

class AgendaRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AgendaWidget().update(context, glanceId)
    }
}

@Composable
private fun AgendaWidgetContent(schedules: List<ScheduleEntity>, isSignedIn: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "直近の予定",
                style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "更新",
                style = TextStyle(color = WidgetAccent),
                modifier = GlanceModifier.clickable(actionRunCallback<AgendaRefreshAction>())
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        when {
            !isSignedIn -> Text(
                text = "タップしてアプリでサインインしてください",
                style = TextStyle(color = WidgetSubText)
            )
            schedules.isEmpty() -> Text(
                text = "直近の予定はありません",
                style = TextStyle(color = WidgetSubText)
            )
            else -> schedules.take(5).forEach { schedule ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = "${schedule.date.format(agendaDateFormatter)} ${schedule.startTime}",
                        style = TextStyle(color = WidgetSubText),
                        modifier = GlanceModifier.width(96.dp)
                    )
                    Text(
                        text = schedule.title,
                        style = TextStyle(color = WidgetOnBackground),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
