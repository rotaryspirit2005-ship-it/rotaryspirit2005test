package com.example.schedulelink.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val PX_PER_DAY = 8f
private val NODE_WIDTH = 120.dp
private val NODE_HEIGHT = 52.dp
private val LANE_HEIGHT = NODE_HEIGHT + 14.dp
private val TIER_GAP = 36.dp
private val RULER_HEIGHT = 40.dp
private val MARGIN = 24.dp

private val TREE_LINE_COLOR = Color(0xFFD8D4C9)
private val PEER_LINK_COLOR = Color(0xFF8B5CF6)
private val RULER_LINE_COLOR = Color(0xFFE7E3D8)
private val GOAL_COLOR = Color(0xFFC08A2E)
private val MILESTONE_COLOR = Color(0xFF2F8F7A)
private val SCHEDULE_COLOR = Color(0xFF3B5BDB)

private fun TreeTier.color(): Color = when (this) {
    TreeTier.GOAL -> GOAL_COLOR
    TreeTier.MILESTONE -> MILESTONE_COLOR
    TreeTier.SCHEDULE -> SCHEDULE_COLOR
}

/**
 * 大目的〜小日程までを1本の木として、実際の日付に沿ったタイムライン上に描画し、
 * さらに小日程どうしの横のリンクも同じキャンバス上に重ねて表示する「全体マップ」。
 * [ZoomPanBox]でピンチズーム・パンできるので、全体を俯瞰しても、
 * 指でつまんで特定の時期・枝を拡大しても見られる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WholeTreeFlowScreen(
    viewModel: WholeTreeViewModel,
    onGoalClick: (String) -> Unit,
    onMilestoneClick: (String) -> Unit,
    onScheduleClick: (String) -> Unit,
    onAddGoalClick: () -> Unit,
    onGoalListClick: () -> Unit,
    onBack: () -> Unit
) {
    val tree by viewModel.tree.collectAsState()

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
                    IconButton(onClick = onGoalListClick) {
                        Icon(Icons.Default.FormatListBulleted, contentDescription = "大目的をリストで見る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGoalClick) {
                Icon(Icons.Default.Add, contentDescription = "大目的を追加")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tree.nodes.isEmpty()) {
                Text(
                    text = "まだ大目的がありません。右下の + から作成しましょう",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                WholeTreeCanvas(
                    tree = tree,
                    onNodeClick = { node ->
                        when (node.tier) {
                            TreeTier.GOAL -> onGoalClick(node.id)
                            TreeTier.MILESTONE -> onMilestoneClick(node.id)
                            TreeTier.SCHEDULE -> onScheduleClick(node.id)
                        }
                    }
                )
            }
        }
    }
}

private fun monthMarks(minDate: LocalDate, maxDate: LocalDate): List<Pair<LocalDate, String>> {
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

@Composable
private fun WholeTreeCanvas(tree: WholeTree, onNodeClick: (TreeNode) -> Unit) {
    val nodesById = remember(tree) { tree.nodes.associateBy { it.id } }
    val nodesByTier = remember(tree) { tree.nodes.groupBy { it.tier } }
    val marks = remember(tree) { monthMarks(tree.minDate, tree.maxDate) }

    fun dateToX(date: LocalDate): Dp =
        MARGIN + (ChronoUnit.DAYS.between(tree.minDate, date) * PX_PER_DAY).dp

    fun maxLane(tier: TreeTier): Int = nodesByTier[tier]?.maxOfOrNull { it.lane } ?: -1

    val goalBaseY = RULER_HEIGHT + TIER_GAP
    val goalTierHeight = LANE_HEIGHT * (maxLane(TreeTier.GOAL) + 1).coerceAtLeast(0)
    val milestoneBaseY = goalBaseY + goalTierHeight + TIER_GAP
    val milestoneTierHeight = LANE_HEIGHT * (maxLane(TreeTier.MILESTONE) + 1).coerceAtLeast(0)
    val scheduleBaseY = milestoneBaseY + milestoneTierHeight + TIER_GAP
    val scheduleTierHeight = LANE_HEIGHT * (maxLane(TreeTier.SCHEDULE) + 1).coerceAtLeast(0)
    val contentHeight = scheduleBaseY + scheduleTierHeight + TIER_GAP

    fun baseYOf(tier: TreeTier): Dp = when (tier) {
        TreeTier.GOAL -> goalBaseY
        TreeTier.MILESTONE -> milestoneBaseY
        TreeTier.SCHEDULE -> scheduleBaseY
    }

    fun topLeftOf(node: TreeNode): Offset = Offset(
        x = dateToX(node.date).value,
        y = (baseYOf(node.tier) + LANE_HEIGHT * node.lane).value
    )

    fun centerOf(node: TreeNode): Offset {
        val topLeft = topLeftOf(node)
        return Offset(topLeft.x + (NODE_WIDTH / 2).value, topLeft.y + (NODE_HEIGHT / 2).value)
    }

    val contentWidth = dateToX(tree.maxDate) + NODE_WIDTH + MARGIN

    ZoomPanBox(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(contentWidth).height(contentHeight)) {
            Canvas(modifier = Modifier.width(contentWidth).height(contentHeight)) {
                marks.forEach { (date, _) ->
                    val x = dateToX(date).toPx()
                    drawLine(
                        color = RULER_LINE_COLOR,
                        start = Offset(x, RULER_HEIGHT.toPx()),
                        end = Offset(x, contentHeight.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                tree.treeEdges.forEach { (parentId, childId) ->
                    val parent = nodesById[parentId] ?: return@forEach
                    val child = nodesById[childId] ?: return@forEach
                    val from = centerOf(parent)
                    val to = centerOf(child)
                    drawLine(
                        color = TREE_LINE_COLOR,
                        start = Offset(from.x.dp.toPx(), from.y.dp.toPx()),
                        end = Offset(to.x.dp.toPx(), to.y.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                tree.peerLinks.forEach { link ->
                    val from = nodesById[link.fromId] ?: return@forEach
                    val to = nodesById[link.toId] ?: return@forEach
                    val a = centerOf(from)
                    val b = centerOf(to)
                    drawLine(
                        color = PEER_LINK_COLOR,
                        start = Offset(a.x.dp.toPx(), a.y.dp.toPx()),
                        end = Offset(b.x.dp.toPx(), b.y.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                }
            }

            marks.forEach { (date, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = dateToX(date) + 4.dp, y = 8.dp)
                )
            }

            tree.nodes.forEach { node ->
                val topLeft = topLeftOf(node)
                TreeNodeCard(
                    node = node,
                    modifier = Modifier.offset(x = topLeft.x.dp, y = topLeft.y.dp),
                    onClick = { onNodeClick(node) }
                )
            }
        }
    }
}

@Composable
private fun TreeNodeCard(node: TreeNode, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .width(NODE_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .background(node.tier.color().copy(alpha = 0.14f))
            .border(1.5.dp, node.tier.color(), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = node.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (node.tier == TreeTier.GOAL) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (node.subtitle.isNotBlank()) {
            Text(
                text = node.subtitle,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = node.tier.color()
            )
        }
    }
}
