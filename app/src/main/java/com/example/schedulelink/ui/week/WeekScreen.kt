package com.example.schedulelink.ui.week

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rangeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)
private val dayHeaderFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)

/** 2本指のつまみ拡大でこの値を超えたら、その日のフロー表示へドリルダウンする。 */
private const val ZOOM_IN_THRESHOLD = 1.3f

/**
 * 週表示。日ごとにその日の予定を要約したカードを縦に並べ、
 * タップまたはカード上でのピンチアウトでその日のフロー表示(ScheduleListScreen)へ進める。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun WeekScreen(
    viewModel: WeekViewModel,
    initialDate: LocalDate,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDayClick: (LocalDate) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(initialDate) { viewModel.initWeek(initialDate) }

    val weekStart by viewModel.weekStart.collectAsState()
    val schedulesByDate by viewModel.schedulesByDate.collectAsState()
    val today = remember { LocalDate.now() }
    val days = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${days.first().format(rangeFormatter)} 〜 ${days.last().format(rangeFormatter)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "月表示に戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToPreviousWeek() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "前の週")
                    }
                    IconButton(onClick = { viewModel.goToToday() }) {
                        Icon(Icons.Default.Today, contentDescription = "今週")
                    }
                    IconButton(onClick = { viewModel.goToNextWeek() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "次の週")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(days, key = { it.toString() }) { date ->
                WeekDayCard(
                    date = date,
                    isToday = date == today,
                    schedules = schedulesByDate[date].orEmpty(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = { onDayClick(date) },
                    onZoomIn = { onDayClick(date) }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WeekDayCard(
    date: LocalDate,
    isToday: Boolean,
    schedules: List<ScheduleEntity>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onZoomIn: () -> Unit
) {
    var accumulatedZoom by remember(date) { mutableFloatStateOf(1f) }
    val cardModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxWidth()
            .sharedBounds(
                rememberSharedContentState(key = "day-$date"),
                animatedVisibilityScope = animatedVisibilityScope
            )
    }
    Card(
        onClick = onClick,
        modifier = cardModifier
            .pointerInput(date) {
                detectTransformGestures { _, _, zoom, _ ->
                    accumulatedZoom = (accumulatedZoom * zoom).coerceIn(0.3f, 4f)
                    if (accumulatedZoom > ZOOM_IN_THRESHOLD) {
                        onZoomIn()
                        accumulatedZoom = 1f
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = date.format(dayHeaderFormatter),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (schedules.isEmpty()) {
                Text(
                    text = "予定はありません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                schedules.take(3).forEach { schedule ->
                    Text(
                        text = "${schedule.startTime} ${schedule.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (schedules.size > 3) {
                    Text(
                        text = "ほか${schedules.size - 3}件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
