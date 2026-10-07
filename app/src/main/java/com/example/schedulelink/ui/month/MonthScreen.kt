package com.example.schedulelink.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
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
import com.example.schedulelink.ui.tag.LocalTagController
import com.example.schedulelink.ui.tag.NO_TAG_FILTER
import com.example.schedulelink.ui.tag.TagFilterRow
import com.example.schedulelink.ui.tag.matchesTagFilter
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.tagColor
import com.example.schedulelink.ui.theme.weekendColor
import com.example.schedulelink.ui.theme.Motion
import com.example.schedulelink.ui.theme.fadeThrough
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.HorizontalDivider

private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN)
private val selectedDateFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
private val weekdayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

/** 2本指のつまみ拡大でこの値を超えたら「その日を含む週」へドリルダウンする。 */
private const val ZOOM_IN_THRESHOLD = 1.3f

/** 月ページャーのページ番号 = 1970年1月からの月数。「今」に依存しない固定の対応にする。 */
private val PAGE_BASE_MONTH: YearMonth = YearMonth.of(1970, 1)
private const val MONTH_PAGE_COUNT = 12 * 200

private fun pageOf(month: YearMonth): Int =
    ((month.year - PAGE_BASE_MONTH.year) * 12 + month.monthValue - 1).coerceIn(0, MONTH_PAGE_COUNT - 1)

private fun monthOf(page: Int): YearMonth = PAGE_BASE_MONTH.plusMonths(page.toLong())

/**
 * トップ画面。月全体を俯瞰し、日付をタップするか、その付近をピンチアウトすると
 * その日を含む週の表示(WeekScreen)へ進める。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun MonthScreen(
    viewModel: MonthViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDayClick: (LocalDate) -> Unit,
    onScheduleClick: (String) -> Unit,
    onAddScheduleClick: (LocalDate) -> Unit,
    onGoalMapClick: () -> Unit,
    onTodoListClick: () -> Unit,
    onFamilySettingsClick: () -> Unit,
    onTagManageClick: () -> Unit,
    onImportIcsClick: () -> Unit,
    onImportCalendarClick: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isPhotoFeatureEnabled: Boolean,
    onTogglePhotoFeature: (Boolean) -> Unit
) {
    val schedulesByDate by viewModel.schedulesByDate.collectAsState()
    // タグでの絞り込み(「パパの予定だけ」など)。削除済みのタグが残っていても無視する。
    val tagController = LocalTagController.current
    var tagFilter by rememberSaveable { mutableStateOf(listOf<String>()) }
    val knownTagIds = remember(tagController) { tagController?.byId?.keys.orEmpty() }
    val activeTagFilter = remember(tagFilter, knownTagIds) {
        tagFilter.filter { it == NO_TAG_FILTER || it in knownTagIds }.toSet()
    }
    val shownSchedulesByDate = remember(schedulesByDate, activeTagFilter, knownTagIds) {
        if (activeTagFilter.isEmpty()) {
            schedulesByDate
        } else {
            schedulesByDate
                .mapValues { (_, list) -> list.filter { matchesTagFilter(it.tagIds, activeTagFilter, knownTagIds) } }
                .filterValues { it.isNotEmpty() }
        }
    }
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val openTodoCount by viewModel.openTodoCount.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showPhotoFeatureWarning by remember { mutableStateOf(false) }
    var showVersionInfo by remember { mutableStateOf(false) }
    var showWidgetLog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // どの月を表示しているかはページャーが正。ViewModelへはページが止まってから伝える
    // (途中で伝えると、選択日のリセットや予定の再取得がスワイプ中に走ってカクつく)。
    val pagerState = rememberPagerState(initialPage = pageOf(viewModel.currentMonth.value)) { MONTH_PAGE_COUNT }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.showMonth(monthOf(it)) }
    }
    // 見出しはスワイプの途中(半分を越えた時点)で切り替えて、指の動きに遅れないようにする。
    val displayedMonth by remember { derivedStateOf { monthOf(pagerState.currentPage) } }

    Scaffold(
        topBar = {
            TopAppBar(
                // 前月・次月の矢印を月の表示の両側にまとめ、右側は「今日」「目的マップ」「その他」に整理する。
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.targetPage - 1) } }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "前の月")
                        }
                        // 最も幅の広い表記で箱の幅を固定し、実際の年月はその中央に置く。月によって
                        // 文字幅が変わっても矢印や文字の位置が動かないようにするため。切り替えはフェードのみ。
                        Box(modifier = Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                            Text(
                                "8888年88月",
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.alpha(0f).clearAndSetSemantics {}
                            )
                            AnimatedContent(
                                targetState = displayedMonth,
                                transitionSpec = { fadeThrough() },
                                contentAlignment = Alignment.Center,
                                label = "monthTitle",
                                modifier = Modifier.matchParentSize()
                            ) { month ->
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        month.format(monthFormatter),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.targetPage + 1) } }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "次の月")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.selectDate(LocalDate.now())
                        scope.launch { pagerState.animateScrollToPage(pageOf(YearMonth.now())) }
                    }) {
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
                            text = { Text("やることリスト") },
                            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                            onClick = { showMenu = false; onTodoListClick() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("家族グループの設定") },
                            onClick = { showMenu = false; onFamilySettingsClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("タグの管理") },
                            onClick = { showMenu = false; onTagManageClick() }
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
            TagFilterRow(
                selected = activeTagFilter,
                onChange = { tagFilter = it.toList() },
                modifier = Modifier.padding(vertical = 4.dp)
            )
            WeekdayHeaderRow()
            // 横スワイプで月送り。指に追従し、隣の月を見せながら慣性で止まる。
            // 前後の月も先に組み立てておき(beyondViewportPageCount)、ドラッグ開始時の引っかかりを防ぐ。
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                pageSpacing = 8.dp,
                verticalAlignment = Alignment.Top
            ) { page ->
                MonthGrid(
                    month = monthOf(page),
                    isSettledPage = page == pagerState.settledPage,
                    schedulesByDate = shownSchedulesByDate,
                    selectedDate = selectedDate,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    // タップ = その場でカレンダーの下に予定を表示、ピンチズーム = 週表示へドリルダウン。
                    onDayClick = { date -> viewModel.selectDate(date) },
                    onPinchZoomDate = onDayClick
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
            ) { dayAll ->
                // タグで絞り込んでいるときは、その日の一覧もタグに合う予定だけにする。
                val day = if (activeTagFilter.isEmpty()) {
                    dayAll
                } else {
                    dayAll.copy(items = dayAll.items.filter { matchesTagFilter(it.schedule.tagIds, activeTagFilter, knownTagIds) })
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    SelectedDayHeading(day, openTodoCount = openTodoCount, onTodoClick = onTodoListClick)
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
private fun SelectedDayHeading(day: DaySchedules, openTodoCount: Int, onTodoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${day.date.format(selectedDateFormatter)}の予定",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (day.date == LocalDate.now()) {
                Spacer(modifier = Modifier.width(8.dp))
                TodayBadge()
            }
            if (day.loaded && day.items.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${day.items.size}件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // やること一覧への入口。上部バーは矢印付きの年月で幅に余裕がないため、ここに置く。
        AssistChip(
            onClick = onTodoClick,
            label = { Text(if (openTodoCount > 0) "やること $openTodoCount" else "やること") },
            leadingIcon = {
                Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        )
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

/**
 * 前後月の空マスも含めた、7列×6行のカレンダーマス目を組み立てる。
 * 5週の月も6行にそろえ、月を送るたびにグリッドと下の予定一覧の高さが変わらないようにする。
 */
private fun buildMonthGrid(month: YearMonth): List<List<LocalDate?>> {
    val firstOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val firstDowIndex = firstOfMonth.dayOfWeek.value % 7 // SUNDAY(7)->0, MONDAY(1)->1, ...
    val cells = MutableList<LocalDate?>(6 * 7) { null }
    for (d in 1..daysInMonth) {
        cells[firstDowIndex + d - 1] = month.atDay(d)
    }
    return cells.chunked(7)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MonthGrid(
    month: YearMonth,
    isSettledPage: Boolean,
    schedulesByDate: Map<LocalDate, List<ScheduleEntity>>,
    selectedDate: LocalDate,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDayClick: (LocalDate) -> Unit,
    onPinchZoomDate: (LocalDate) -> Unit
) {
    val weeks = remember(month) { buildMonthGrid(month) }
    val today = remember { LocalDate.now() }
    // 点の色は、その日の予定ごとに「最初のタグの色」(タグがなければ従来のprimary)。
    val tagController = LocalTagController.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    fun dotColorsOf(list: List<ScheduleEntity>): List<Color> = list.take(3).map { schedule ->
        tagController?.resolve(schedule.tagIds)?.firstOrNull()?.let { tagColor(it.colorIndex, isDark) } ?: primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 指1本の横ドラッグは親のHorizontalPagerに任せ、ここでは指2本のピンチだけを扱う。
            // 指の中心(centroid)がどのマスの上にあるかで、ズームインしたい日付を判定する。
            // 2本指の間はイベントを消費して、ピンチ中にページ送りが動かないようにする。
            .pointerInput(month) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var accumulatedZoom = 1f
                    var handled = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            event.changes.forEach { it.consume() }
                            if (!handled) {
                                accumulatedZoom = (accumulatedZoom * event.calculateZoom()).coerceIn(0.3f, 4f)
                                if (accumulatedZoom > ZOOM_IN_THRESHOLD) {
                                    val centroid = event.calculateCentroid()
                                    val col = (centroid.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                                    val row = (centroid.y / (size.height / weeks.size.toFloat())).toInt()
                                        .coerceIn(0, weeks.size - 1)
                                    weeks.getOrNull(row)?.getOrNull(col)?.let { onPinchZoomDate(it) }
                                    handled = true
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
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
                                dotColors = dotColorsOf(schedulesByDate[date].orEmpty()),
                                // 画面外に組み立ててある前後の月のマスが、週表示との共有要素
                                // アニメーションで画面外から飛んでこないよう、表示中の月だけに付ける。
                                enableSharedBounds = isSettledPage,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    columnIndex: Int,
    isToday: Boolean,
    isSelected: Boolean,
    scheduleCount: Int,
    dotColors: List<Color>,
    enableSharedBounds: Boolean,
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
    val sharedBoundsModifier = if (enableSharedBounds) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "day-$date"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(sharedBoundsModifier)
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
                dotColors.forEach { dotColor ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(dotColor, CircleShape)
                    )
                }
            }
        }
    }
}
