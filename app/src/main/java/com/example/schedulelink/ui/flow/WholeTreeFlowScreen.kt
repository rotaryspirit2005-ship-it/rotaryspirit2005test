package com.example.schedulelink.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.schedulelink.ui.common.AppFab
import com.example.schedulelink.ui.common.EmptyState
import com.example.schedulelink.ui.tag.LocalTagController
import com.example.schedulelink.ui.tag.NO_TAG_FILTER
import com.example.schedulelink.ui.tag.TagDots
import com.example.schedulelink.ui.tag.TagFilterRow
import com.example.schedulelink.ui.tag.matchesTagFilter
import com.example.schedulelink.ui.theme.DarkOnError
import com.example.schedulelink.ui.theme.GoalColorDark
import com.example.schedulelink.ui.theme.GoalColorLight
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.MilestoneColorDark
import com.example.schedulelink.ui.theme.MilestoneColorLight
import com.example.schedulelink.ui.theme.PeerLinkColorDark
import com.example.schedulelink.ui.theme.PeerLinkColorLight
import com.example.schedulelink.ui.theme.SaturdayColorDark
import com.example.schedulelink.ui.theme.SaturdayColorLight
import com.example.schedulelink.ui.theme.ScheduleColorDark
import com.example.schedulelink.ui.theme.ScheduleColorLight
import com.example.schedulelink.ui.theme.SundayColorDark
import com.example.schedulelink.ui.theme.SundayColorLight
import com.example.schedulelink.ui.theme.TodayLineColorDark
import com.example.schedulelink.ui.theme.TodayLineColorLight
import com.example.schedulelink.ui.theme.connectorColor
import com.example.schedulelink.ui.theme.fadeThrough
import java.time.LocalDate

/**
 * 階層ごとのアクセント色。白背景では明るい色は文字として読みにくくなるため、
 * ライト/ダークで別の濃さを使う([isDark]は[LocalIsDarkTheme]から取得する)。
 */
internal fun TreeTier.color(isDark: Boolean): Color = when (this) {
    TreeTier.GOAL -> if (isDark) GoalColorDark else GoalColorLight
    TreeTier.MILESTONE -> if (isDark) MilestoneColorDark else MilestoneColorLight
    TreeTier.SCHEDULE -> if (isDark) ScheduleColorDark else ScheduleColorLight
}

/**
 * 大目的〜小日程までを見渡す「全体マップ」。ツリー(字下げの一覧)と、タイムライン
 * (大目的ごとの帯に中日程の期間のバーと予定の点を並べたもの。[SwimlaneTimeline])を切り替えられる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WholeTreeFlowScreen(
    viewModel: WholeTreeViewModel,
    /** 最初に開く表示(0 = ツリー、1 = タイムライン)。 */
    initialMode: Int = 0,
    onGoalClick: (String) -> Unit,
    onMilestoneClick: (String) -> Unit,
    onScheduleClick: (String) -> Unit,
    onAddGoalClick: () -> Unit,
    onGoalListClick: () -> Unit,
    onBack: () -> Unit
) {
    val outline by viewModel.outline.collectAsState()
    // タグでの絞り込み(ツリーとタイムラインで共通)。削除済みのタグが残っていても無視する。
    val tagController = LocalTagController.current
    var tagFilter by rememberSaveable { mutableStateOf(listOf<String>()) }
    // ツリーを、タグを作った順に並べ替える(タイムラインは日付が横軸なので並べ替えない)。
    var sortByTag by rememberSaveable { mutableStateOf(false) }
    val knownTagIds = remember(tagController) { tagController?.byId?.keys.orEmpty() }
    val activeTagFilter = remember(tagFilter, knownTagIds) {
        tagFilter.filter { it == NO_TAG_FILTER || it in knownTagIds }.toSet()
    }
    // 0 = ツリー(既定)、1 = タイムライン。
    var viewMode by rememberSaveable { mutableStateOf(initialMode) }
    // ツリーに出す内容(タグで絞り込み・並べ替え済み)と開閉状態。まとめて開閉するボタンと共有する。
    val shownOutline = remember(outline, activeTagFilter, knownTagIds, sortByTag, tagController) {
        outline?.let { fullOutline ->
            val filtered = filterOutlineByTags(fullOutline, activeTagFilter, knownTagIds)
            if (sortByTag && tagController != null) {
                sortOutlineByTags(filtered, tagController.tags.withIndex().associate { (index, tag) -> tag.id to index })
            } else {
                filtered
            }
        }
    }
    val expansion = rememberOutlineExpansion()
    // 表示を切り替えて戻ったときも、ツリーの開閉やスクロール位置を保つ。
    val viewStateHolder = rememberSaveableStateHolder()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全体マップ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    // ツリー表示のときだけ、まとめて開く/閉じるボタンを出す。
                    val treeForBulk = shownOutline
                    if (viewMode == 0 && treeForBulk != null && !treeForBulk.isEmpty) {
                        val allOpen = isOutlineFullyExpanded(treeForBulk, expansion, activeTagFilter.isNotEmpty())
                        IconButton(onClick = { setOutlineExpanded(treeForBulk, expansion, !allOpen, activeTagFilter.isNotEmpty()) }) {
                            Icon(
                                if (allOpen) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = if (allOpen) "すべて閉じる" else "すべて開く"
                            )
                        }
                    }
                    IconButton(onClick = onGoalListClick) {
                        Icon(Icons.Default.FormatListBulleted, contentDescription = "大目的をリストで見る")
                    }
                }
            )
        },
        floatingActionButton = {
            AppFab(onClick = onAddGoalClick, icon = Icons.Default.Add, contentDescription = "大目的を追加")
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                listOf("ツリー", "タイムライン").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = viewMode == index,
                        onClick = { viewMode = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                    ) {
                        Text(label)
                    }
                }
            }
            TagFilterRow(
                selected = activeTagFilter,
                onChange = { tagFilter = it.toList() },
                modifier = Modifier.padding(bottom = 4.dp),
                sortByTag = if (viewMode == 0) sortByTag else null,
                onSortByTagChange = { sortByTag = it }
            )
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = { fadeThrough() },
                modifier = Modifier.weight(1f),
                label = "wholeMapMode"
            ) { mode ->
                viewStateHolder.SaveableStateProvider(mode) {
                    if (mode == 0) {
                        // 読み込み前(null)は何も出さない。
                        shownOutline?.let { shown ->
                            if (activeTagFilter.isNotEmpty() && shown.isEmpty) {
                                EmptyState(
                                    icon = Icons.Outlined.LocalOffer,
                                    message = "選んだタグの予定はありません",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                WholeTreeOutlineView(
                                    outline = shown,
                                    expansion = expansion,
                                    expandMatches = activeTagFilter.isNotEmpty(),
                                    onGoalClick = onGoalClick,
                                    onMilestoneClick = onMilestoneClick,
                                    onScheduleClick = onScheduleClick,
                                    onAddGoalClick = onAddGoalClick
                                )
                            }
                        } ?: Box(modifier = Modifier.fillMaxSize())
                    } else {
                        // タイムライン表示のときだけ購読する。
                        val data by viewModel.timelineData.collectAsState()
                        Column(modifier = Modifier.fillMaxSize()) {
                            val current = data
                            if (current != null && !current.isEmpty) {
                                TreeLegend()
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (current == null) {
                                    // 読み込み前は何も出さない。
                                    Box(modifier = Modifier.fillMaxSize())
                                } else if (current.isEmpty) {
                                    EmptyState(
                                        icon = Icons.Outlined.Flag,
                                        message = "まだ大目的がありません",
                                        modifier = Modifier.fillMaxSize(),
                                        actionLabel = "大目的を追加",
                                        onAction = onAddGoalClick
                                    )
                                } else {
                                    SwimlaneTimeline(
                                        goals = current.goals,
                                        milestones = current.milestones,
                                        schedules = current.schedules,
                                        tagFilter = activeTagFilter,
                                        knownTagIds = knownTagIds,
                                        onGoalClick = onGoalClick,
                                        onMilestoneClick = onMilestoneClick,
                                        onScheduleClick = onScheduleClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** タイムラインの見方の簡易凡例(1行に収まる量にする)。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeLegend() {
    val isDark = LocalIsDarkTheme.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TreeLegendItem("帯=大目的") {
            Box(modifier = Modifier.width(14.dp).height(10.dp).background(connectorColor(0, isDark).copy(alpha = 0.25f)))
        }
        TreeLegendItem("中日程の期間") {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(connectorColor(0, isDark))
            )
        }
        TreeLegendItem("予定") { LegendDot(connectorColor(0, isDark)) }
        TreeLegendItem("リンク") {
            LegendLine(listOf(if (isDark) PeerLinkColorDark else PeerLinkColorLight), dashed = true)
        }
        TreeLegendItem("今日") {
            val todayColor = if (isDark) TodayLineColorDark else TodayLineColorLight
            Canvas(modifier = Modifier.width(2.dp).height(12.dp)) {
                drawLine(
                    color = todayColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = size.width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.5.dp.toPx()))
                )
            }
        }
    }
}

@Composable
private fun TreeLegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        swatch()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** 凡例の線の見本。色が複数のときは、同じ長さで色を並べる。 */
@Composable
private fun LegendLine(colors: List<Color>, dashed: Boolean) {
    Canvas(modifier = Modifier.width(24.dp).height(8.dp)) {
        val stroke = 1.5.dp.toPx()
        val segment = size.width / colors.size
        colors.forEachIndexed { index, color ->
            drawLine(
                color = color,
                start = Offset(segment * index, size.height / 2),
                end = Offset(segment * (index + 1), size.height / 2),
                strokeWidth = stroke,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
            )
        }
    }
}

internal fun monthMarks(minDate: LocalDate, maxDate: LocalDate): List<Pair<LocalDate, String>> {
    val marks = mutableListOf<Pair<LocalDate, String>>()
    var cursor = minDate.withDayOfMonth(1)
    var first = true
    while (!cursor.isAfter(maxDate)) {
        val label = if (first || cursor.monthValue == 1) "${cursor.year}年${cursor.monthValue}月" else "${cursor.monthValue}月"
        marks += cursor to label
        first = false
        cursor = cursor.plusMonths(1)
    }
    return marks
}
