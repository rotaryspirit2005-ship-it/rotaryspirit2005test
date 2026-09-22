package com.example.schedulelink.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.schedulelink.MainActivity
import com.example.schedulelink.ScheduleLinkApplication
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.ScheduleWithLinks
import com.example.schedulelink.data.WidgetErrorLog
import com.example.schedulelink.data.WidgetPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val monthWidgetFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN)
private val selectedDateWidgetFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
private val timeWidgetFormatter = DateTimeFormatter.ofPattern("H:mm")
private val weekdayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

private val SELECTED_DATE_KEY = stringPreferencesKey("selected_date")
private val DATE_PARAM = ActionParameters.Key<String>("date")

/** ホーム画面ウィジェット: 上半分に今月のミニカレンダー、下半分にタップした日の予定(簡易フロー風)。 */
class MonthMiniWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetErrorLog.recordInfo(context, "MonthMiniWidget", "provideGlance開始")

        var month = YearMonth.now()
        var selectedDate = LocalDate.now()
        var scheduleDates: Set<LocalDate> = emptySet()
        var selectedDaySchedules: List<ScheduleWithLinks> = emptyList()
        var fetchError: String? = null
        var isSignedIn = false

        // getAppWidgetStateなど、この処理全体のどこで失敗しても
        // 原因不明のまま表示が止まらないよう、丸ごと捕まえて記録する。
        try {
            val app = context.applicationContext as ScheduleLinkApplication
            val familyId = WidgetPreferences(context).familyId
            isSignedIn = familyId != null
            month = YearMonth.now()

            // どの日を選んでいるかは、SharedPreferencesを自前でキー管理するのではなく、
            // Glance自身がウィジェットインスタンスごとに正しく紐付けてくれる
            // 標準の状態保存機構(PreferencesGlanceStateDefinition)を使う。
            val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            selectedDate = prefs[SELECTED_DATE_KEY]
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now()
            WidgetErrorLog.recordInfo(context, "MonthMiniWidget", "選択日読み込み完了: $selectedDate")

            if (familyId != null) {
                // ウィジェットの更新処理はOSから実行時間の制約を受けるため、Firestoreへの
                // 通信が遅い・詰まっている場合に無期限に待ち続けると、更新自体が
                // 何も起きないまま失敗する(「更新やタップの反応が不安定」に見える原因)。
                // 明示的にタイムアウトを設け、必ずウィジェットの表示を完了させる。
                val timedOut = withTimeoutOrNull(8_000) {
                    runCatching {
                        val repo = ScheduleRepository(app.firestore, familyId)
                        scheduleDates = repo.schedulesInRange(month.atDay(1), month.atEndOfMonth()).first()
                            .map { it.date }
                            .toSet()
                        selectedDaySchedules = repo.schedulesForDateWithLinks(selectedDate).first()
                    }.onFailure { e ->
                        // データ取得の失敗を握りつぶさず、原因切り分けのため表示する。
                        fetchError = "${e::class.simpleName}: ${e.message}"
                        WidgetErrorLog.record(context, "MonthMiniWidget.fetch", e)
                    }
                } == null
                if (timedOut) {
                    fetchError = "データ取得がタイムアウトしました(通信状況をご確認ください)"
                    WidgetErrorLog.recordInfo(context, "MonthMiniWidget", "データ取得タイムアウト")
                }
            }
            WidgetErrorLog.recordInfo(
                context,
                "MonthMiniWidget",
                "データ取得完了: schedules=${selectedDaySchedules.size}件, error=$fetchError"
            )
        } catch (e: Throwable) {
            WidgetErrorLog.record(context, "MonthMiniWidget.provideGlance", e)
            fetchError = "致命的エラー: ${e::class.simpleName}: ${e.message}"
        }

        provideContent {
            MonthMiniWidgetContent(
                month = month,
                scheduleDates = scheduleDates,
                selectedDate = selectedDate,
                selectedDaySchedules = selectedDaySchedules,
                isSignedIn = isSignedIn,
                fetchError = fetchError
            )
        }
        WidgetErrorLog.recordInfo(context, "MonthMiniWidget", "provideGlance完了(provideContent呼び出し済み)")
    }
}

class MonthMiniWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthMiniWidget()
}

class MonthMiniRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetErrorLog.recordInfo(context, "MonthMiniRefreshAction", "onAction呼び出された")
        try {
            MonthMiniWidget().update(context, glanceId)
            WidgetErrorLog.recordInfo(context, "MonthMiniRefreshAction", "update()完了")
        } catch (e: Throwable) {
            WidgetErrorLog.record(context, "MonthMiniRefreshAction", e)
        }
    }
}

class SelectDateAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetErrorLog.recordInfo(context, "SelectDateAction", "onAction呼び出された: date=${parameters[DATE_PARAM]}")
        try {
            val date = parameters[DATE_PARAM]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (date == null) {
                WidgetErrorLog.recordInfo(context, "SelectDateAction", "dateパラメータが不正または欠落")
                return
            }
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[SELECTED_DATE_KEY] = date.toString()
            }
            WidgetErrorLog.recordInfo(context, "SelectDateAction", "状態書き込み完了: $date")
            MonthMiniWidget().update(context, glanceId)
            WidgetErrorLog.recordInfo(context, "SelectDateAction", "update()完了")
        } catch (e: Throwable) {
            WidgetErrorLog.record(context, "SelectDateAction", e)
        }
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
private fun MonthMiniWidgetContent(
    month: YearMonth,
    scheduleDates: Set<LocalDate>,
    selectedDate: LocalDate,
    selectedDaySchedules: List<ScheduleWithLinks>,
    isSignedIn: Boolean,
    fetchError: String?
) {
    val weeks = buildMonthGrid(month)
    val today = LocalDate.now()
    val context = LocalContext.current
    val openAppAction = actionStartActivity(Intent(context, MainActivity::class.java))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.format(monthWidgetFormatter),
                style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().clickable(openAppAction)
            )
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
                style = TextStyle(color = WidgetSubText),
                modifier = GlanceModifier.clickable(openAppAction)
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
                        val isSelected = date != null && date == selectedDate
                        val isToday = date == today
                        val cellModifier = if (date != null) {
                            GlanceModifier
                                .defaultWeight()
                                .padding(2.dp)
                                .cornerRadius(8.dp)
                                .background(if (isSelected) WidgetAccent else WidgetBackground)
                                .clickable(
                                    actionRunCallback<SelectDateAction>(actionParametersOf(DATE_PARAM to date.toString()))
                                )
                        } else {
                            GlanceModifier.defaultWeight().padding(2.dp)
                        }

                        Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                            if (date != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = TextStyle(
                                            color = when {
                                                isSelected -> WidgetSelectedText
                                                isToday -> WidgetAccent
                                                else -> WidgetOnBackground
                                            },
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    if (date in scheduleDates) {
                                        Box(
                                            modifier = GlanceModifier
                                                .size(4.dp)
                                                .background(if (isSelected) WidgetSelectedText else WidgetAccent)
                                                .cornerRadius(2.dp)
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (fetchError != null) {
                Text(
                    text = "取得エラー: $fetchError",
                    style = TextStyle(color = WidgetSubText)
                )
            }

            Text(
                text = "${selectedDate.format(selectedDateWidgetFormatter)}の予定",
                style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))

            if (selectedDaySchedules.isEmpty()) {
                Text(
                    text = "予定はありません",
                    style = TextStyle(color = WidgetSubText),
                    modifier = GlanceModifier.clickable(openAppAction)
                )
            } else {
                Column(modifier = GlanceModifier.clickable(openAppAction)) {
                    selectedDaySchedules.take(3).forEach { item ->
                        ScheduleFlowRowSimple(item)
                    }
                    if (selectedDaySchedules.size > 3) {
                        Text(
                            text = "ほか${selectedDaySchedules.size - 3}件",
                            style = TextStyle(color = WidgetSubText)
                        )
                    }
                }
            }
        }
    }
}

/** 実際の分岐カーブ描画(Canvas)はGlanceでは使えないため、矢印記号で簡易的にフローを表現する。 */
@Composable
private fun ScheduleFlowRowSimple(item: ScheduleWithLinks) {
    Column(modifier = GlanceModifier.padding(vertical = 2.dp)) {
        Row {
            Text(
                text = item.schedule.startTime.format(timeWidgetFormatter),
                style = TextStyle(color = WidgetSubText),
                modifier = GlanceModifier.width(48.dp)
            )
            Text(
                text = item.schedule.title,
                style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        item.linkedSchedules.take(2).forEach { linked ->
            Row {
                Spacer(modifier = GlanceModifier.width(16.dp))
                Text(text = "→ ", style = TextStyle(color = WidgetAccent))
                Text(
                    text = "${linked.startTime.format(timeWidgetFormatter)} ${linked.title}",
                    style = TextStyle(color = WidgetSubText),
                    maxLines = 1
                )
            }
        }
    }
}
