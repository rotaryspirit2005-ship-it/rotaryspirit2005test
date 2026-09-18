package com.example.schedulelink.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
private val RULER_HEIGHT = 48.dp
private val MARGIN = 24.dp

/** 日の目盛り(線)は、この拡大率から常にうっすらグレーで見えている。 */
private const val DAY_TICK_FADE_START = 0.4f
/** 日の数字は線より少し遅れて、この拡大率あたりから見え始める。 */
private const val DAY_LABEL_FADE_START = 1.5f
/** ここまで拡大すると、線・数字とも真っ白(完全に見える状態)になる。 */
private const val DAY_DETAIL_FADE_END = 4f
/** 期間が長すぎる場合、日の目盛りは大量になりすぎるので出さない。 */
private const val MAX_DAY_MARKS = 1000

private val TREE_LINE_COLOR = Color(0xFFD8D4C9)
private val PEER_LINK_COLOR = Color(0xFFD946EF)
private val RULER_LINE_COLOR = Color(0xFFE7E3D8)
private val DAY_DETAIL_GRAY = Color(0xFF6B6B6B)
private val GOAL_COLOR = Color(0xFFFF6B4A)
private val MILESTONE_COLOR = Color(0xFF2EC4B6)
private val SCHEDULE_COLOR = Color(0xFF4D96FF)
/** カードの塗りに階層色をどれだけ混ぜるか(0=無地、1=階層色そのまま)。 */
private const val CARD_TINT_RATIO = 0.30f

/** 日の目盛りが常に「ちらっと見える」最低限の透明度。 */
private const val DAY_TICK_MIN_ALPHA = 0.12f

private fun TreeTier.color(): Color = when (this) {
    TreeTier.GOAL -> GOAL_COLOR
    TreeTier.MILESTONE -> MILESTONE_COLOR
    TreeTier.SCHEDULE -> SCHEDULE_COLOR
}

/**
 * 標準の大きさ(scale=1)より拡大されている分だけを打ち消す係数。
 * 縮小方向(scale<1、俯瞰して見るとき)はそのまま自然に小さくなってよいので触らず、
 * 拡大方向(scale>1、日の目盛りを出すためにつまんで拡大したとき)だけ、
 * カードや文字・線が際限なく大きくならないよう抑える。
 */
private fun capScale(scale: Float): Float = if (scale <= 1f) 1f else 1f / scale

/**
 * 地図のピンのように、大きく拡大しても画面上の見た目のサイズが際限なく
 * 大きくならないようにする。実際の位置はズームに合わせて広がるが、
 * カードや文字自体は標準の大きさ以上には育たない。
 */
private fun Modifier.counterScale(scale: Float): Modifier {
    val factor = capScale(scale)
    return this.graphicsLayer(scaleX = factor, scaleY = factor)
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

/** ズームインしたときに月の目盛りの間へ差し込む、日ごとの目盛り。 */
private fun dayMarks(minDate: LocalDate, maxDate: LocalDate): List<LocalDate> {
    if (ChronoUnit.DAYS.between(minDate, maxDate) > MAX_DAY_MARKS) return emptyList()
    val marks = mutableListOf<LocalDate>()
    var cursor = minDate
    while (!cursor.isAfter(maxDate)) {
        marks += cursor
        cursor = cursor.plusDays(1)
    }
    return marks
}

@Composable
private fun WholeTreeCanvas(tree: WholeTree, onNodeClick: (TreeNode) -> Unit) {
    val nodesById = remember(tree) { tree.nodes.associateBy { it.id } }
    val nodesByTier = remember(tree) { tree.nodes.groupBy { it.tier } }
    val marks = remember(tree) { monthMarks(tree.minDate, tree.maxDate) }
    val dayMarksList = remember(tree) { dayMarks(tree.minDate, tree.maxDate) }

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

    ZoomPanBox(modifier = Modifier.fillMaxSize()) { scale ->
        // 拡大率に応じて0〜1で滑らかに変化する進捗値。日の目盛りは常に薄く見えており、
        // 拡大するほどグレーから白へ、じわじわ濃く・明るくなっていく(数字は線より少し遅れて追いつく)。
        val tickProgress = ((scale - DAY_TICK_FADE_START) / (DAY_DETAIL_FADE_END - DAY_TICK_FADE_START)).coerceIn(0f, 1f)
        val labelProgress = ((scale - DAY_LABEL_FADE_START) / (DAY_DETAIL_FADE_END - DAY_LABEL_FADE_START)).coerceIn(0f, 1f)
        val dayTickColor = lerp(DAY_DETAIL_GRAY, Color.White, tickProgress)
            .copy(alpha = DAY_TICK_MIN_ALPHA + (1f - DAY_TICK_MIN_ALPHA) * tickProgress)
        val dayLabelColor = lerp(DAY_DETAIL_GRAY, Color.White, labelProgress).copy(alpha = labelProgress)

        Box(modifier = Modifier.width(contentWidth).height(contentHeight)) {
            Canvas(modifier = Modifier.width(contentWidth).height(contentHeight)) {
                // 線の太さも、拡大しすぎたときだけ際限なく太くならないよう抑える。
                val strokeFactor = capScale(scale)
                val hairline = (1.dp * strokeFactor).toPx()
                val edgeStroke = (1.5.dp * strokeFactor).toPx()

                marks.forEach { (date, _) ->
                    val x = dateToX(date).toPx()
                    drawLine(
                        color = RULER_LINE_COLOR,
                        start = Offset(x, RULER_HEIGHT.toPx()),
                        end = Offset(x, contentHeight.toPx()),
                        strokeWidth = hairline
                    )
                }

                dayMarksList.forEach { date ->
                    val x = dateToX(date).toPx()
                    drawLine(
                        color = dayTickColor,
                        start = Offset(x, RULER_HEIGHT.toPx()),
                        end = Offset(x, contentHeight.toPx()),
                        strokeWidth = hairline
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
                        strokeWidth = edgeStroke
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
                        strokeWidth = edgeStroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                }
            }

            marks.forEach { (date, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .offset(x = dateToX(date) + 4.dp, y = 6.dp)
                        .counterScale(scale)
                )
            }

            if (labelProgress > 0f) {
                dayMarksList.forEach { date ->
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = dayLabelColor,
                        modifier = Modifier
                            .offset(x = dateToX(date) + 2.dp, y = 26.dp)
                            .counterScale(scale)
                    )
                }
            }

            tree.nodes.forEach { node ->
                val topLeft = topLeftOf(node)
                TreeNodeCard(
                    node = node,
                    scale = scale,
                    modifier = Modifier.offset(x = topLeft.x.dp, y = topLeft.y.dp),
                    onClick = { onNodeClick(node) }
                )
            }
        }
    }
}

@Composable
private fun TreeNodeCard(node: TreeNode, scale: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // 無地ではなく階層色をうっすら混ぜた塗り(不透明)にして、ポップな印象にする。
    // 線を完全に隠すため透過なしで塗る点は変えない。
    val fillColor = lerp(MaterialTheme.colorScheme.surfaceVariant, node.tier.color(), CARD_TINT_RATIO)
    Column(
        modifier = modifier
            .width(NODE_WIDTH)
            .counterScale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(fillColor)
            .border(2.dp, node.tier.color(), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(node.tier.color())
            )
            Text(
                text = node.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (node.subtitle.isNotBlank()) {
            Text(
                text = node.subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = node.tier.color(),
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}
