package com.example.schedulelink.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SLOT_WIDTH = 72.dp
private val MARGIN = 24.dp
private val GOAL_Y = 32.dp
private val MILESTONE_Y = 172.dp
private val SCHEDULE_Y = 312.dp
private val CONTENT_HEIGHT = 420.dp

private val TREE_LINE_COLOR = Color(0xFFD8D4C9)
private val PEER_LINK_COLOR = Color(0xFF8B5CF6)
private val GOAL_COLOR = Color(0xFFC08A2E)
private val MILESTONE_COLOR = Color(0xFF2F8F7A)
private val SCHEDULE_COLOR = Color(0xFF3B5BDB)

private fun TreeTier.color(): Color = when (this) {
    TreeTier.GOAL -> GOAL_COLOR
    TreeTier.MILESTONE -> MILESTONE_COLOR
    TreeTier.SCHEDULE -> SCHEDULE_COLOR
}

private fun TreeTier.dotSize(): Dp = when (this) {
    TreeTier.GOAL -> 18.dp
    TreeTier.MILESTONE -> 13.dp
    TreeTier.SCHEDULE -> 9.dp
}

private fun TreeTier.centerY(): Dp = when (this) {
    TreeTier.GOAL -> GOAL_Y
    TreeTier.MILESTONE -> MILESTONE_Y
    TreeTier.SCHEDULE -> SCHEDULE_Y
}

/**
 * 大目的〜小日程までを1本の木として描画し、さらに小日程どうしの横のリンクも
 * 同じキャンバス上に重ねて表示する「全体マップ」。[ZoomPanBox]でピンチズーム・
 * パンできるので、全体を俯瞰しても、指でつまんで特定の枝を拡大しても見られる。
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

@Composable
private fun WholeTreeCanvas(tree: WholeTree, onNodeClick: (TreeNode) -> Unit) {
    val contentWidth = MARGIN * 2 + SLOT_WIDTH * (tree.maxSlot + 1f)
    val nodesById = remember(tree) { tree.nodes.associateBy { it.id } }

    fun centerOf(node: TreeNode): Offset = Offset(
        x = (MARGIN + SLOT_WIDTH * node.slot).value,
        y = node.tier.centerY().value
    )

    ZoomPanBox(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(contentWidth).height(CONTENT_HEIGHT)) {
            Canvas(modifier = Modifier.width(contentWidth).height(CONTENT_HEIGHT)) {
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

            tree.nodes.forEach { node ->
                val center = centerOf(node)
                TreeNodeChip(
                    node = node,
                    modifier = Modifier.offset(x = center.x.dp - 40.dp, y = center.y.dp - node.tier.dotSize() / 2),
                    onClick = { onNodeClick(node) }
                )
            }
        }
    }
}

@Composable
private fun TreeNodeChip(node: TreeNode, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.width(80.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(node.tier.dotSize())
                .clip(CircleShape)
                .background(node.tier.color())
        )
        Text(
            text = node.title,
            style = if (node.tier == TreeTier.GOAL) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            maxLines = if (node.tier == TreeTier.GOAL) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
