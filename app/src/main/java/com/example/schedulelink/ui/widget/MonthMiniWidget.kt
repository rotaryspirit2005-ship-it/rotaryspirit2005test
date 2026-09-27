package com.example.schedulelink.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import kotlinx.coroutines.CancellationException
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
/** 「更新」やアプリ側からの再取得要求のたびに書き換え、表示中のセッションにも再取得させるための値。 */
private val REFRESH_TOKEN_KEY = longPreferencesKey("refresh_token")
private val DATE_PARAM = ActionParameters.Key<String>("date")

/** ウィジェット1回分の表示内容。どの選択日・どの再取得要求に対して取得したものかも持つ。 */
private data class MonthWidgetData(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val refreshToken: Long,
    val scheduleCounts: Map<LocalDate, Int>,
    val selectedDaySchedules: List<ScheduleWithLinks>,
    val fetchError: String?,
    val isSignedIn: Boolean
)

private fun parseSelectedDate(raw: String?): LocalDate =
    raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()

/**
 * Firestoreから月の件数と選択日の予定を単発取得する。キャンセル(タイムアウト・選択日の変更)は
 * 握りつぶさずに伝播させる: 以前はrunCatchingがタイムアウトの例外まで捕まえてしまい、
 * タイムアウト時の表示が一度も出ない状態になっていた。
 */
private suspend fun loadMonthWidgetData(context: Context, selectedDate: LocalDate, refreshToken: Long): MonthWidgetData {
    val month = YearMonth.now()
    var scheduleCounts: Map<LocalDate, Int> = emptyMap()
    var selectedDaySchedules: List<ScheduleWithLinks> = emptyList()
    var fetchError: String? = null
    var isSignedIn = false
    try {
        val app = context.applicationContext as ScheduleLinkApplication
        val familyId = WidgetPreferences(context).familyId
        isSignedIn = familyId != null
        if (familyId != null) {
            val completed = withTimeoutOrNull(8_000) {
                try {
                    val repo = ScheduleRepository(app.firestore, familyId)
                    scheduleCounts = repo.schedulesInRangeOnce(month.atDay(1), month.atEndOfMonth())
                        .groupingBy { it.date }
                        .eachCount()
                    selectedDaySchedules = repo.schedulesForDateWithLinksOnce(selectedDate)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fetchError = "${e::class.simpleName}: ${e.message}"
                    WidgetErrorLog.record(context, "MonthMiniWidget.fetch", e)
                }
                true
            }
            if (completed == null) {
                fetchError = "データ取得がタイムアウトしました(通信状況をご確認ください)"
                WidgetErrorLog.recordInfo(context, "MonthMiniWidget", "データ取得タイムアウト")
            }
        }
        WidgetErrorLog.recordInfo(
            context,
            "MonthMiniWidget",
            "データ取得完了: $selectedDate schedules=${selectedDaySchedules.size}件, error=$fetchError"
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        WidgetErrorLog.record(context, "MonthMiniWidget.load", e)
        fetchError = "致命的エラー: ${e::class.simpleName}: ${e.message}"
    }
    return MonthWidgetData(month, selectedDate, refreshToken, scheduleCounts, selectedDaySchedules, fetchError, isSignedIn)
}

/** 表示中のウィジェットに再取得させる(「更新」ボタン・アプリ側での予定変更時・定期更新)。 */
internal suspend fun requestMonthMiniRefresh(context: Context, glanceId: GlanceId) {
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[REFRESH_TOKEN_KEY] = System.currentTimeMillis()
    }
    MonthMiniWidget().update(context, glanceId)
}

/** ホーム画面ウィジェット: 上に今月のミニカレンダー、下にタップした日の予定(簡易フロー風)。 */
class MonthMiniWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetErrorLog.recordInfo(context, "MonthMiniWidget", "provideGlance開始")

        // 最初の描画ですぐ中身が出るよう、表示前に一度取得しておく。
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadMonthWidgetData(
            context,
            parseSelectedDate(prefs[SELECTED_DATE_KEY]),
            prefs[REFRESH_TOKEN_KEY] ?: 0L
        )

        provideContent {
            // Glanceは表示中のセッション(最大45秒)を使い回すため、provideGlanceは再実行されない
            // ことがある。選択日・再取得要求はここで毎回ウィジェットの状態から読み、変わったら
            // その場で取り直す(provideGlanceで取得した値だけに頼ると古い日の予定が残る)。
            val selectedDate = parseSelectedDate(currentState(SELECTED_DATE_KEY))
            val refreshToken = currentState(REFRESH_TOKEN_KEY) ?: 0L
            val data by produceState(initial, selectedDate, refreshToken) {
                if (value.selectedDate != selectedDate || value.refreshToken != refreshToken) {
                    value = loadMonthWidgetData(context, selectedDate, refreshToken)
                }
            }
            MonthMiniWidgetContent(data = data, selectedDate = selectedDate)
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
            requestMonthMiniRefresh(context, glanceId)
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

// Glanceは1つのColumn/Row/Boxにつき子を10個までしか描画せず、11個目以降はエラーもなく捨てる。
// 以前はルート直下に「タイトル・曜日・週×5〜6・見出し・一覧…」を並べていたため、一覧が
// 11個目以降になって常に消えていた。直下の子は3つのまとまりにし、一覧はLazyColumnにする。
@Composable
private fun MonthMiniWidgetContent(data: MonthWidgetData, selectedDate: LocalDate) {
    val weeks = buildMonthGrid(data.month)
    val today = LocalDate.now()
    val context = LocalContext.current
    val openAppAction = actionStartActivity(Intent(context, MainActivity::class.java))
    // 選択日を変えた直後は、新しい日の予定を取り終えるまで前の日の予定を出さない。
    val isLoading = data.selectedDate != selectedDate

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 1. ヘッダー
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.month.format(monthWidgetFormatter),
                style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                modifier = GlanceModifier.defaultWeight().clickable(openAppAction)
            )
            Text(
                text = "更新",
                style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Medium),
                // 文字そのものだけだとタップ判定が狭いため、周囲のpadding込みでタップ領域を広げる。
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<MonthMiniRefreshAction>())
            )
        }

        if (!data.isSignedIn) {
            Text(
                text = "タップしてアプリでサインインしてください",
                style = TextStyle(color = WidgetSubText),
                modifier = GlanceModifier.clickable(openAppAction)
            )
        } else {
            // 2. ミニカレンダー(曜日行 + 最大6週 = 子は最大7個)。行の高さを固定して、
            //    6週の月でも下の予定欄の場所が必ず残るようにする。
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(modifier = GlanceModifier.fillMaxWidth().height(16.dp)) {
                    weekdayLabels.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            style = TextStyle(
                                color = widgetWeekdayColor(index, WidgetSubText),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
                weeks.forEach { week ->
                    Row(modifier = GlanceModifier.fillMaxWidth().height(26.dp)) {
                        week.forEachIndexed { columnIndex, date ->
                            val cellModifier = if (date != null) {
                                GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .cornerRadius(6.dp)
                                    .background(if (date == selectedDate) WidgetSelectedContainer else WidgetBackground)
                                    .clickable(
                                        actionRunCallback<SelectDateAction>(actionParametersOf(DATE_PARAM to date.toString()))
                                    )
                            } else {
                                GlanceModifier.defaultWeight().fillMaxHeight()
                            }
                            Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    DayCellContent(
                                        date = date,
                                        columnIndex = columnIndex,
                                        isToday = date == today,
                                        isSelected = date == selectedDate,
                                        hasSchedules = (data.scheduleCounts[date] ?: 0) > 0
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 選択日の予定(残りの高さをすべて使い、はみ出す分はスクロール)
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(top = 4.dp)) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetOutlineVariant)
                ) {}
                val count = data.selectedDaySchedules.size
                Text(
                    text = selectedDate.format(selectedDateWidgetFormatter) + "の予定" +
                        if (!isLoading && count > 0) " · ${count}件" else "",
                    style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp)
                )
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.TopStart
                ) {
                    val message = when {
                        isLoading -> "読み込み中…"
                        data.fetchError != null -> "取得エラー: ${data.fetchError}"
                        count == 0 -> "予定はありません"
                        else -> null
                    }
                    if (message != null) {
                        Text(
                            text = message,
                            style = TextStyle(color = WidgetSubText, fontSize = 13.sp),
                            maxLines = 2,
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(openAppAction)
                        )
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(data.selectedDaySchedules, itemId = { it.schedule.id.hashCode().toLong() }) { item ->
                                ScheduleFlowRowSimple(item = item, onClick = openAppAction)
                            }
                            item { Spacer(modifier = GlanceModifier.height(4.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCellContent(date: LocalDate, columnIndex: Int, isToday: Boolean, isSelected: Boolean, hasSchedules: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isToday) {
            // アプリ本体と同じく、今日は緑の丸に白抜き数字。
            // cornerRadiusはAndroid 12未満では効かず四角になるが、表示は崩れない。
            Box(
                modifier = GlanceModifier.size(20.dp).cornerRadius(10.dp).background(WidgetAccent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = TextStyle(color = WidgetOnAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                )
            }
        } else {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    color = widgetWeekdayColor(columnIndex, if (isSelected) WidgetOnSelected else WidgetOnBackground),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            )
        }
        if (hasSchedules) {
            Box(modifier = GlanceModifier.size(4.dp).cornerRadius(2.dp).background(WidgetAccent)) {}
        }
    }
}

/** 実際の分岐カーブ描画(Canvas)はGlanceでは使えないため、矢印記号で簡易的にフローを表現する。 */
@Composable
private fun ScheduleFlowRowSimple(item: ScheduleWithLinks, onClick: Action) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.schedule.startTime.format(timeWidgetFormatter),
                style = TextStyle(color = WidgetSubText, fontSize = 12.sp),
                modifier = GlanceModifier.width(44.dp)
            )
            Text(
                text = item.schedule.title,
                style = TextStyle(color = WidgetOnBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                maxLines = 1
            )
        }
        item.linkedSchedules.firstOrNull()?.let { linked ->
            Row {
                Spacer(modifier = GlanceModifier.width(44.dp))
                Text(
                    text = "→ ${linked.startTime.format(timeWidgetFormatter)} ${linked.title}",
                    style = TextStyle(color = WidgetSubText, fontSize = 12.sp),
                    maxLines = 1
                )
            }
        }
    }
}
