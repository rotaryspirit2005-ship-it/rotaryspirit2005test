package com.example.schedulelink.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.schedulelink.BuildConfig
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.WidgetErrorLog
import com.example.schedulelink.ui.common.AppFab
import com.example.schedulelink.ui.common.TodayBadge
import com.example.schedulelink.ui.list.FlowLegend
import com.example.schedulelink.ui.list.ScheduleEmptyState
import com.example.schedulelink.ui.list.ScheduleFlowList
import com.example.schedulelink.ui.theme.weekendColor
import com.example.schedulelink.ui.theme.Motion
import com.example.schedulelink.ui.theme.directionalSlide
import com.example.schedulelink.ui.theme.fadeThrough
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import androidx.compose.material3.HorizontalDivider

private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN)
private val selectedDateFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
private val weekdayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

/** 2本指のつまみ拡大でこの値を超えたら「その日を含む週」へドリルダウンする。 */
private const val ZOOM_IN_THRESHOLD = 1.3f
/** 月切り替えスワイプの判定に使う移動量(指1本での横ドラッグ)。 */
private const val SWIPE_THRESHOLD_DP = 72

/**
 * トップ画面。月全体を俯瞰し、日付をタップするか、その付近をピンチアウトすると
 * その日を含む週の表示(WeekScreen)へ進める。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MonthScreen(
    viewModel: MonthViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDayClick: (LocalDate) -> Unit,
    onScheduleClick: (String) -> Unit,
    onAddScheduleClick: (LocalDate) -> Unit,
    onGoalMapClick: () -> Unit,
    onFamilySettingsClick: () -> Unit,
    onImportIcsClick: () -> Unit,
    onImportCalendarClick: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isPhotoFeatureEnabled: Boolean,
    onTogglePhotoFeature: (Boolean) -> Unit
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val schedulesByDate by viewModel.schedulesByDate.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showPhotoFeatureWarning by remember { mutableStateOf(false) }
    var showVersionInfo by remember { mutableStateOf(false) }
    var showWidgetLog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                // 前月・次月の矢印を月の表示の両側にまとめ、右側は「今日」「目的マップ」「その他」に整理する。
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.goToPreviousMonth() }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "前の月")
                        }
                        AnimatedContent(
                            targetState = currentMonth,
                            transitionSpec = {
                                val direction = if (targetState > initialState) 1 else -1
                                (
                                    slideInVertically(tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)) { direction * it / 3 } +
                                        fadeIn(tween(Motion.DurationMedium))
                                    ) togetherWith (
                                    slideOutVertically(tween(Motion.DurationShort, easing = Motion.EmphasizedAccelerate)) { -direction * it / 3 } +
                                        fadeOut(tween(Motion.DurationShort))
                                    )
                            },
                            label = "monthTitle",
                            // fill = falseだと月によって文字幅が変わるたびに矢印ボタンの位置が
                            // ずれていた(例: 「9月」と「10月」で幅が違う)。maxLines/ellipsisで
                            // 幅超過は既に吸収しているので、幅を固定するfill = trueにする。
                            modifier = Modifier.weight(1f)
                        ) { month ->
                            Text(month.format(monthFormatter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { viewModel.goToNextMonth() }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "次の月")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToToday() }) {
                        Icon(Icons.Default.Today, contentDescription = "今日")
                    }
                    IconButton(onClick = onGoalMapClick) {
                        Icon(Icons.Default.Flag, contentDescription = "目的マップ")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "その他")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("家族グループの設定") },
                            onClick = { showMenu = false; onFamilySettingsClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(".icsファイルから読み込む") },
                            onClick = { showMenu = false; onImportIcsClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("端末のカレンダーから読み込む") },
                            onClick = { showMenu = false; onImportCalendarClick() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (isDarkTheme) "ホワイトモードにする" else "ブラックモードにする") },
                            onClick = { showMenu = false; onToggleTheme() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isPhotoFeatureEnabled) "写真添付機能をオフにする" else "写真添付機能をオンにする") },
                            onClick = {
                                showMenu = false
                                if (isPhotoFeatureEnabled) {
                                    onTogglePhotoFeature(false)
                                } else {
                                    showPhotoFeatureWarning = true
                                }
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("バージョン情報") },
                            onClick = { showMenu = false; showVersionInfo = true }
                        )
                        DropdownMenuItem(
                            text = { Text("ウィジェットログ") },
                            onClick = { showMenu = false; showWidgetLog = true }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            AppFab(
                onClick = { onAddScheduleClick(selectedDate) },
                icon = Icons.Default.Add,
                contentDescription = "予定を追加"
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WeekdayHeaderRow()
            AnimatedContent(
                targetState = currentMonth,
                transitionSpec = { directionalSlide(forward = targetState > initialState) },
                label = "monthGrid"
            ) { month ->
                MonthGrid(
                    // ピンチ判定などは、遷移中も各グリッド自身の月(=ラムダ引数)で行う。
                    month = month,
                    schedulesByDate = schedulesByDate,
                    selectedDate = selectedDate,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    // タップ = その場でカレンダーの下に予定を表示、ピンチズーム = 週表示へドリルダウン。
                    onDayClick = { date -> viewModel.selectDate(date) },
                    onPinchZoomDate = onDayClick,
                    onSwipePrevious = { viewModel.goToPreviousMonth() },
                    onSwipeNext = { viewModel.goToNextMonth() }
                )
            }
            HorizontalDivider()
            // 見出し・件数・一覧を「日付」単位でまとめて切り替える。同じ日のデータ更新では動かさない。
            AnimatedContent(
                targetState = selectedDay,
                contentKey = { it.date },
                transitionSpec = { fadeThrough() },
                modifier = Modifier.weight(1f),
                label = "selectedDay"
            ) { day ->
                Column(modifier = Modifier.fillMaxSize()) {
                    SelectedDayHeading(day)
                    if (day.items.any { it.linkedSchedules.isNotEmpty() }) {
                        FlowLegend()
                    }
                    when {
                        // 取得中は何も出さない(「予定はありません」が一瞬見えるのを避ける)。
                        !day.loaded -> Unit
                        day.items.isEmpty() -> {
                            // 月グリッドの下の残り領域は狭いため、アイコンなしの小さい表示にする。
                            // 追加は右下のFABと同じ操作になるので、ここにはボタンを重ねて出さない。
                            ScheduleEmptyState(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                compact = true
                            )
                        }
                        else -> ScheduleFlowList(
                            items = day.items,
                            onItemClick = onScheduleClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showPhotoFeatureWarning) {
        AlertDialog(
            onDismissRequest = { showPhotoFeatureWarning = false },
            title = { Text("写真添付機能を有効にしますか?") },
            text = {
                Text(
                    "写真の保存にはFirebase Storageを使用します。無料枠を超えて利用した場合、" +
                        "料金が発生する可能性があります。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoFeatureWarning = false
                    onTogglePhotoFeature(true)
                }) { Text("有効にする") }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoFeatureWarning = false }) { Text("キャンセル") }
            }
        )
    }

    if (showVersionInfo) {
        val buildTimeText = remember {
            DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm", Locale.JAPAN)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(BuildConfig.BUILD_TIME))
        }
        AlertDialog(
            onDismissRequest = { showVersionInfo = false },
            title = { Text("バージョン情報") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("コミット: ${BuildConfig.GIT_SHA}")
                    Text("ビルド日時: $buildTimeText")
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionInfo = false }) { Text("閉じる") }
            }
        )
    }

    if (showWidgetLog) {
        var logText by remember { mutableStateOf(WidgetErrorLog.readAll(context)) }
        AlertDialog(
            onDismissRequest = { showWidgetLog = false },
            title = { Text("ウィジェットログ") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logText.ifBlank { "ログはまだありません。ウィジェットの更新ボタンや日付をタップしてから開いてください。" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWidgetLog = false }) { Text("閉じる") }
            },
            dismissButton = {
                TextButton(onClick = {
                    WidgetErrorLog.clear(context)
                    logText = WidgetErrorLog.readAll(context)
                }) { Text("クリア") }
            }
        )
    }
}

@Composable
private fun SelectedDayHeading(day: DaySchedules) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${day.date.format(selectedDateFormatter)}の予定",
            style = MaterialTheme.typography.titleMedium
        )
        if (day.date == LocalDate.now()) {
            Spacer(modifier = Modifier.width(8.dp))
            TodayBadge()
        }
        Spacer(modifier = Modifier.weight(1f))
        if (day.loaded && day.items.isNotEmpty()) {
            Text(
                text = "${day.items.size}件",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        weekdayLabels.forEachIndexed { index, label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = weekendColor(index).takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
                )
            }
        }
    }
}

/** 前後月の空マスも含めた、7列×n行のカレンダーマス目を組み立てる。 */
private fun buildMonthGrid(month: YearMonth): List<List<LocalDate?>> {
    val firstOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val firstDowIndex = firstOfMonth.dayOfWeek.value % 7 // SUNDAY(7)->0, MONDAY(1)->1, ...
    val totalCells = firstDowIndex + daysInMonth
    val rowCount = ceil(totalCells / 7.0).toInt()
    val cells = MutableList<LocalDate?>(rowCount * 7) { null }
    for (d in 1..daysInMonth) {
        cells[firstDowIndex + d - 1] = month.atDay(d)
    }
    return cells.chunked(7)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MonthGrid(
    month: YearMonth,
    schedulesByDate: Map<LocalDate, List<ScheduleEntity>>,
    selectedDate: LocalDate,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDayClick: (LocalDate) -> Unit,
    onPinchZoomDate: (LocalDate) -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit
) {
    val weeks = remember(month) { buildMonthGrid(month) }
    var accumulatedZoom by remember(month) { mutableFloatStateOf(1f) }
    val today = remember { LocalDate.now() }
    // スワイプ中、しきい値に達するまで何も動かないと「カクつく」ため、指の動きに
    // そのまま追従させる。スワイプが不成立(離した/ピンチに切り替わった)ときは0へ戻す。
    // Animatableの更新はawaitEachGesture内部の制限付きスコープ(@RestrictsSuspension)からは
    // 直接呼べないため、rememberCoroutineScopeで取った通常のスコープ経由でlaunchする。
    val dragOffset = remember(month) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // グリッド全体でピンチとスワイプの両方を検知する。指2本ならピンチズーム(週表示への
            // ドリルダウン)、指1本の横ドラッグなら月送り。1ジェスチャー(指を置いてから離すまで)
            // ごとに状態をリセットするため、awaitEachGestureで自前ループを組む。
            .pointerInput(month) {
                val swipeThresholdPx = SWIPE_THRESHOLD_DP.dp.toPx()
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var horizontalDrag = 0f
                    var handled = false
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount > 1) {
                            // ピンチ中に横ドラッグ分だけ誤って月送りしないよう、指を追加した時点で捨てる。
                            horizontalDrag = 0f
                            coroutineScope.launch { dragOffset.snapTo(0f) }
                            val zoom = event.calculateZoom()
                            accumulatedZoom = (accumulatedZoom * zoom).coerceIn(0.3f, 4f)
                            if (!handled && accumulatedZoom > ZOOM_IN_THRESHOLD) {
                                val centroid = event.calculateCentroid()
                                val col = (centroid.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                                val row = (centroid.y / (size.height / weeks.size.toFloat())).toInt()
                                    .coerceIn(0, weeks.size - 1)
                                weeks.getOrNull(row)?.getOrNull(col)?.let { onPinchZoomDate(it) }
                                accumulatedZoom = 1f
                                handled = true
                            }
                        } else if (!handled) {
                            horizontalDrag += event.calculatePan().x
                            coroutineScope.launch { dragOffset.snapTo(horizontalDrag) }
                            when {
                                horizontalDrag <= -swipeThresholdPx -> {
                                    onSwipeNext()
                                    handled = true
                                }
                                horizontalDrag >= swipeThresholdPx -> {
                                    onSwipePrevious()
                                    handled = true
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (handled) {
                        // 月が切り替わった直後。以降の見た目はAnimatedContent側のスライドに任せる。
                        coroutineScope.launch { dragOffset.snapTo(0f) }
                    } else {
                        coroutineScope.launch {
                            dragOffset.animateTo(0f, tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate))
                        }
                    }
                }
            }
    ) {
        Column(modifier = Modifier.graphicsLayer { translationX = dragOffset.value }) {
            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEachIndexed { columnIndex, date ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                // 2dpずつ削ると小型端末で48dpのタップ推奨サイズを
                                // わずかに下回ることがあるため、1dpに減らして確保する。
                                .padding(1.dp)
                        ) {
                            if (date != null) {
                                DayCell(
                                    date = date,
                                    columnIndex = columnIndex,
                                    isToday = date == today,
                                    isSelected = date == selectedDate,
                                    scheduleCount = schedulesByDate[date]?.size ?: 0,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = { onDayClick(date) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    columnIndex: Int,
    isToday: Boolean,
    isSelected: Boolean,
    scheduleCount: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit
) {
    // このマスを起点に、週表示の同じ日付のカードへ「コンテナごと」滑らかに
    // 拡大していく(Material Design で言うコンテナ変形)。日付ごとに同じキーを
    // 使うことで、タップ/ピンチした日だけがWeekScreen側の該当カードと結びつく。
    // Color.Transparent(透明な黒)との補間だと途中が濁るため、同じ色のalpha 0と補間する。
    val selectedContainer = MaterialTheme.colorScheme.primaryContainer
    val selectedFill by animateColorAsState(
        targetValue = if (isSelected) selectedContainer else selectedContainer.copy(alpha = 0f),
        animationSpec = tween(Motion.DurationSelect, easing = Motion.Standard),
        label = "dayCellFill"
    )
    val description = "${date.monthValue}月${date.dayOfMonth}日" +
        if (scheduleCount > 0) " 予定${scheduleCount}件" else ""
    with(sharedTransitionScope) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .sharedBounds(
                    rememberSharedContentState(key = "day-$date"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
                .clip(MaterialTheme.shapes.medium)
                // 選択中の日はセル全体をprimaryContainerで塗る(枠線は使わない)。
                .background(selectedFill)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    selected = isSelected
                }
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .then(
                        if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        isSelected -> weekendColor(columnIndex).takeOrElse { MaterialTheme.colorScheme.onPrimaryContainer }
                        else -> weekendColor(columnIndex).takeOrElse { MaterialTheme.colorScheme.onSurface }
                    }
                )
            }
            // 件数の数字は小さいセルに収まらず切れていたため、最大3個の点で表す
            // (正確な件数は読み上げ用のcontentDescriptionと、下の一覧の見出しで分かる)。
            if (scheduleCount > 0) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(minOf(scheduleCount, 3)) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
