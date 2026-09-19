package com.example.schedulelink.ui.list

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleWithLinks
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.collectAsState

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
private val timeFormatter = DateTimeFormatter.ofPattern("H:mm")

/** つながる予定の枝(ブランチ)チップの色。時刻の流れと見分けられるよう緑系にする。 */
private val BRANCH_COLOR = Color(0xFF2E7D32)
private val ARROW_COLOR = Color(0xFFB0B0B0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ScheduleListScreen(
    viewModel: ScheduleListViewModel,
    initialDate: LocalDate,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val schedules by viewModel.schedules.collectAsState()

    // 週表示の該当カードから、この画面全体がコンテナ変形でせり出してくるように見せる。
    // ここで使うキーは、画面に入った時点の日付(initialDate)で固定しておく
    // (この画面の中で前後の日付に移動しても、遷移アニメーション用のキーは変えない)。
    val screenModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = "day-$initialDate"),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }

    Scaffold(
        modifier = screenModifier,
        topBar = {
            TopAppBar(
                title = { Text(selectedDate.format(dateFormatter)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "週表示に戻る", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToPreviousDay() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "前の日", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.goToToday() }) {
                        Icon(Icons.Default.Today, contentDescription = "今日", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.goToNextDay() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "次の日", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "予定を追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FlowLegend()
            if (schedules.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                ScheduleFlowList(
                    items = schedules,
                    onItemClick = onItemClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** 「時刻の流れ」と「つながる予定(分岐)」の意味を示す、控えめな凡例。 */
@Composable
private fun FlowLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = ARROW_COLOR,
                modifier = Modifier.size(16.dp)
            )
            Text("時刻の流れ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(BRANCH_COLOR)
            )
            Text("つながる予定(分岐)", style = MaterialTheme.typography.labelSmall, color = BRANCH_COLOR)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "この日の予定はありません",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isLater(candidate: ScheduleEntity, reference: ScheduleEntity): Boolean {
    val dateCompare = candidate.date.compareTo(reference.date)
    if (dateCompare != 0) return dateCompare > 0
    val timeCompare = candidate.startTime.compareTo(reference.startTime)
    if (timeCompare != 0) return timeCompare > 0
    return candidate.id > reference.id
}

/** その予定が持つリンクのうち、時系列で後になるものだけ(=まだ画面上で示されていないもの)。 */
private fun ScheduleWithLinks.forwardLinks(): List<ScheduleEntity> =
    linkedSchedules.filter { isLater(it, schedule) }

@Composable
private fun ScheduleFlowList(
    items: List<ScheduleWithLinks>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 分岐チップを表示する行だけを数えて左右交互に振り分ける
    // (分岐のない行を挟んでも、見た目上のジグザグが崩れないようにする)。
    var chipSideCounter = 0
    val chipSides = items.map { item ->
        val forward = item.forwardLinks()
        if (forward.size == 1) {
            val isRight = chipSideCounter % 2 == 0
            chipSideCounter++
            isRight
        } else {
            null
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        items(items.size) { index ->
            val item = items[index]
            val forward = item.forwardLinks()
            val chipTarget = forward.singleOrNull()
            val showPill = chipTarget == null && item.linkedSchedules.size >= 2

            ScheduleFlowRow(
                item = item,
                chipTarget = chipTarget,
                chipOnRight = chipSides[index] ?: true,
                showLinkCountPill = showPill,
                onClick = { onItemClick(item.schedule.id) },
                onChipClick = { chipTarget?.let { onItemClick(it.id) } }
            )
            if (index != items.lastIndex) {
                FlowArrowDown()
            }
        }
    }
}

@Composable
private fun FlowArrowDown() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = ARROW_COLOR,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ScheduleFlowRow(
    item: ScheduleWithLinks,
    chipTarget: ScheduleEntity?,
    chipOnRight: Boolean,
    showLinkCountPill: Boolean,
    onClick: () -> Unit,
    onChipClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // 右端(=メインカードに接する側)に線が来るよう、チップ→線の順で並べる。
            if (chipTarget != null && !chipOnRight) {
                ScheduleBranchChip(target = chipTarget, onClick = onChipClick)
                Spacer(modifier = Modifier.width(4.dp))
                BranchConnector(pointsRight = false)
            }
        }

        MainScheduleCard(
            item = item,
            showLinkCountPill = showLinkCountPill,
            onClick = onClick,
            modifier = Modifier.width(190.dp)
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // 左端(=メインカードに接する側)に線が来るよう、線→チップの順で並べる。
            if (chipTarget != null && chipOnRight) {
                BranchConnector(pointsRight = true)
                Spacer(modifier = Modifier.width(4.dp))
                ScheduleBranchChip(target = chipTarget, onClick = onChipClick)
            }
        }
    }
}

@Composable
private fun BranchConnector(pointsRight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!pointsRight) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = BRANCH_COLOR, modifier = Modifier.size(14.dp))
        }
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(2.dp)
                .background(BRANCH_COLOR)
        )
        if (pointsRight) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BRANCH_COLOR, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun MainScheduleCard(
    item: ScheduleWithLinks,
    showLinkCountPill: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val schedule = item.schedule
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "${schedule.startTime.format(timeFormatter)}〜${schedule.endTime.format(timeFormatter)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = schedule.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showLinkCountPill) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .clip(RoundedCornerShape(50))
                    .background(BRANCH_COLOR)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = "リンク済みの予定数", tint = Color.White, modifier = Modifier.size(10.dp))
                Text(
                    text = item.linkedSchedules.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ScheduleBranchChip(target: ScheduleEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BRANCH_COLOR.copy(alpha = 0.08f))
            .dashedBorder(BRANCH_COLOR, cornerRadius = 12.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (target.date == LocalDate.now()) {
                target.startTime.format(timeFormatter)
            } else {
                "${target.date.dayOfMonth}日 ${target.startTime.format(timeFormatter)}"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = BRANCH_COLOR
        )
        Text(
            text = target.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.5.dp): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
    )
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = CornerRadius(cornerRadius.toPx())
    )
}
