package com.example.schedulelink.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPAN)
private val weekdayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

/** 2本指のつまみ拡大でこの値を超えたら「その日を含む週」へドリルダウンする。 */
private const val ZOOM_IN_THRESHOLD = 1.3f

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
    onGoalMapClick: () -> Unit,
    onFamilySettingsClick: () -> Unit,
    onImportIcsClick: () -> Unit,
    onImportCalendarClick: () -> Unit
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val schedulesByDate by viewModel.schedulesByDate.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentMonth.format(monthFormatter)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goToPreviousMonth() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "前の月")
                    }
                },
                actions = {
                    IconButton(onClick = onGoalMapClick) {
                        Icon(Icons.Default.Flag, contentDescription = "目的マップ")
                    }
                    IconButton(onClick = { viewModel.goToToday() }) {
                        Icon(Icons.Default.Today, contentDescription = "今月")
                    }
                    IconButton(onClick = { viewModel.goToNextMonth() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "次の月")
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
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WeekdayHeaderRow()
            MonthGrid(
                month = currentMonth,
                schedulesByDate = schedulesByDate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onDayClick = onDayClick,
                onPinchZoomDate = onDayClick
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        weekdayLabels.forEachIndexed { index, label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = weekdayColor(index)
                )
            }
        }
    }
}

private fun weekdayColor(columnIndex: Int): Color = when (columnIndex) {
    0 -> Color(0xFFE5484D) // 日曜
    6 -> Color(0xFF3B82F6) // 土曜
    else -> Color.Unspecified
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
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDayClick: (LocalDate) -> Unit,
    onPinchZoomDate: (LocalDate) -> Unit
) {
    val weeks = remember(month) { buildMonthGrid(month) }
    var accumulatedZoom by remember(month) { mutableFloatStateOf(1f) }
    val today = remember { LocalDate.now() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // グリッド全体でピンチを検知し、指の中心(centroid)がどのマスの上にあるかで
            // ズームインしたい日付を判定する。マス自体が小さく指2本を収めにくいための工夫。
            .pointerInput(month) {
                detectTransformGestures { centroid, _, zoom, _ ->
                    accumulatedZoom = (accumulatedZoom * zoom).coerceIn(0.3f, 4f)
                    if (accumulatedZoom > ZOOM_IN_THRESHOLD) {
                        val col = (centroid.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                        val row = (centroid.y / (size.height / weeks.size.toFloat())).toInt()
                            .coerceIn(0, weeks.size - 1)
                        weeks.getOrNull(row)?.getOrNull(col)?.let { onPinchZoomDate(it) }
                        accumulatedZoom = 1f
                    }
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
                            .padding(2.dp)
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                columnIndex = columnIndex,
                                isToday = date == today,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    columnIndex: Int,
    isToday: Boolean,
    scheduleCount: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit
) {
    // このマスを起点に、週表示の同じ日付のカードへ「コンテナごと」滑らかに
    // 拡大していく(Material Design で言うコンテナ変形)。日付ごとに同じキーを
    // 使うことで、タップ/ピンチした日だけがWeekScreen側の該当カードと結びつく。
    with(sharedTransitionScope) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .sharedBounds(
                    rememberSharedContentState(key = "day-$date"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .then(
                        if (isToday) {
                            Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        weekdayColor(columnIndex).takeOrElse { MaterialTheme.colorScheme.onSurface }
                    }
                )
            }
            if (scheduleCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                if (scheduleCount > 1) {
                    Text(
                        text = scheduleCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
