package com.example.schedulelink.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.schedulelink.MainActivity
import com.example.schedulelink.ScheduleLinkApplication
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.WidgetPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val monthWidgetFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN)
private val weekdayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

/** ホーム画面ウィジェット: 今月のミニカレンダー(予定がある日にドット表示)。 */
class MonthMiniWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ScheduleLinkApplication
        val familyId = WidgetPreferences(context).familyId
        val month = YearMonth.now()
        val scheduleDates: Set<LocalDate> = if (familyId != null) {
            runCatching {
                val repo = ScheduleRepository(app.firestore, familyId)
                repo.schedulesInRange(month.atDay(1), month.atEndOfMonth()).first()
                    .map { it.date }
                    .toSet()
            }.getOrDefault(emptySet())
        } else {
            emptySet()
        }

        provideContent {
            MonthMiniWidgetContent(month = month, scheduleDates = scheduleDates, isSignedIn = familyId != null)
        }
    }
}

class MonthMiniWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthMiniWidget()
}

class MonthMiniRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MonthMiniWidget().update(context, glanceId)
    }
}

/** MonthScreenのbuildMonthGridと同じロジック(前後月の空マスも含めた7列グリッド)。 */
private fun buildMonthGrid(month: YearMonth): List<List<LocalDate?>> {
    val firstOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val firstDowIndex = firstOfMonth.dayOfWeek.value % 7
    val totalCells = firstDowIndex + daysInMonth
    val rowCount = ceil(totalCells / 7.0).toInt()
    val cells = MutableList<LocalDate?>(rowCount * 7) { null }
    for (d in 1..daysInMonth) {
        cells[firstDowIndex + d - 1] = month.atDay(d)
    }
    return cells.chunked(7)
}

@Composable
private fun MonthMiniWidgetContent(month: YearMonth, scheduleDates: Set<LocalDate>, isSignedIn: Boolean) {
    val weeks = buildMonthGrid(month)
    val today = LocalDate.now()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.format(monthWidgetFormatter),
                style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "更新",
                style = TextStyle(color = WidgetAccent),
                modifier = GlanceModifier.clickable(actionRunCallback<MonthMiniRefreshAction>())
            )
        }
        Spacer(modifier = GlanceModifier.height(4.dp))

        if (!isSignedIn) {
            Text(
                text = "タップしてアプリでサインインしてください",
                style = TextStyle(color = WidgetSubText)
            )
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Text(text = label, style = TextStyle(color = WidgetSubText, textAlign = TextAlign.Center))
                    }
                }
            }
            weeks.forEach { week ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(
                            modifier = GlanceModifier.defaultWeight().padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = TextStyle(
                                            color = if (date == today) WidgetAccent else WidgetOnBackground,
                                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    if (date in scheduleDates) {
                                        Box(
                                            modifier = GlanceModifier
                                                .size(4.dp)
                                                .background(WidgetAccent)
                                                .cornerRadius(2.dp)
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
