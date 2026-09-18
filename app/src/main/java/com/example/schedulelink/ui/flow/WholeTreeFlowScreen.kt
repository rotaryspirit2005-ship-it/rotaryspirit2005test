package com.example.schedulelink.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
/** 「警告」っぽく見える赤系ではなく、大きな目標らしい高揚感のある金色にする。 */
private val GOAL_COLOR = Color(0xFFFFB300)
private val MILESTONE_COLOR = Color(0xFF2EC4B6)
private val SCHEDULE_COLOR = Color(0xFF4D96FF)
/** 「今日」を示す縦線。他のどの階層色とも被らない赤にして、カレンダーの定番配色に合わせる。 */
private val TODAY_LINE_COLOR = Color(0xFFFF3B30)
/** カードの塗りに階層色をどれだけ混ぜるか(0=無地、1=階層色そのまま)。 */
private const val CARD_TINT_RATIO = 0.30f

/** 日の目盛りが常に「ちらっと見える」最低限の透明度。 */
private const val DAY_TICK_MIN_ALPHA = 0.12f

/**
 * 同じ階層の間(親子の隙間)に、線の高さをずらして重なりを減らすための段数。
 * 隣り合う親どうしに別の段を割り当てることで、水平線が同じ高さに集まりにくくする。
 */
private const val BUS_SLOT_COUNT = 3

/** 選択中のノードと無関係な線を薄くする際の透明度。 */
private const val DIMMED_LINE_ALPHA = 0.15f

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WholeTreeCanvas(tree: WholeTree, onNodeClick: (TreeNode) -> Unit) {
    val nodesById = remember(tree) { tree.nodes.associateBy { it.id } }
    val nodesByTier = remember(tree) { tree.nodes.groupBy { it.tier } }
    val marks = remember(tree) { monthMarks(tree.minDate, tree.maxDate) }
    val dayMarksList = remember(tree) { dayMarks(tree.minDate, tree.maxDate) }
    // 兄弟(同じ親を持つ子)をまとめて1本の共通の縦線+横のバスでつなぐことで、
    // 子1つずつに水平線を引いていたときの重なりを減らす(組織図と同じ考え方)。
    val parentGroups = remember(tree) {
        tree.treeEdges.groupBy({ it.first }, { it.second })
            .toList()
            .sortedBy { (parentId, _) -> nodesById[parentId]?.date ?: LocalDate.MIN }
    }
    // 隣り合う親どうしのバスの高さが同じ位置に集まらないよう、段をずらして割り当てる。
    val busSlotByParent = remember(parentGroups) {
        parentGroups.mapIndexed { index, (parentId, _) -> parentId to (index % BUS_SLOT_COUNT) }.toMap()
    }
    // 長押しで選んだノードに関わる線だけをくっきり見せ、他は薄くして見やすくする。
    var selectedNodeId by remember(tree) { mutableStateOf<String?>(null) }

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

    // 親の階層と子の階層の間の隙間の中で、段(slot)に応じた高さにバスを置く。
    // この隙間はどのレーンの組み合わせでも必ず親より下・子より上になるので、
    // 線が上下逆になることはない。
    fun busY(parentTier: TreeTier, slot: Int): Dp {
        val (gapTop, gapBottom) = when (parentTier) {
            TreeTier.GOAL -> goalBaseY + goalTierHeight to milestoneBaseY
            TreeTier.MILESTONE -> milestoneBaseY + milestoneTierHeight to scheduleBaseY
            TreeTier.SCHEDULE -> scheduleBaseY to scheduleBaseY
        }
        val fraction = (slot + 1f) / (BUS_SLOT_COUNT + 1f)
        return gapTop + (gapBottom - gapTop) * fraction
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
    val today = remember { LocalDate.now() }
    val todayInRange = !today.isBefore(tree.minDate) && !today.isAfter(tree.maxDate)

    ZoomPanBox(
        modifier = Modifier.fillMaxSize(),
        initialFocusX = if (todayInRange) dateToX(today) else null
    ) { scale ->
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

                parentGroups.forEach { (parentId, childIds) ->
                    val parent = nodesById[parentId] ?: return@forEach
                    val children = childIds.mapNotNull { nodesById[it] }
                    if (children.isEmpty()) return@forEach

                    val slot = busSlotByParent[parentId] ?: 0
                    val bus = busY(parent.tier, slot).toPx()
                    val parentCenter = centerOf(parent)
                    val parentPx = Offset(parentCenter.x.dp.toPx(), parentCenter.y.dp.toPx())
                    val childPxList = children.map { child ->
                        val c = centerOf(child)
                        Triple(child.id, Offset(c.x.dp.toPx(), c.y.dp.toPx()), child)
                    }
                    val minX = minOf(parentPx.x, childPxList.minOf { it.second.x })
                    val maxX = maxOf(parentPx.x, childPxList.maxOf { it.second.x })

                    val isParentSelected = selectedNodeId != null && selectedNodeId == parentId
                    val groupHighlighted = selectedNodeId == null ||
                        isParentSelected ||
                        childPxList.any { it.first == selectedNodeId }
                    val trunkColor = if (groupHighlighted) TREE_LINE_COLOR else TREE_LINE_COLOR.copy(alpha = DIMMED_LINE_ALPHA)
                    val trunkStroke = if (selectedNodeId != null && groupHighlighted) edgeStroke * 1.4f else edgeStroke

                    // 幹: 親から、複数の子をまとめる共通のバス(横線)まで一本で下ろす。
                    drawLine(color = trunkColor, start = parentPx, end = Offset(parentPx.x, bus), strokeWidth = trunkStroke)
                    // バス: 兄弟をまとめる横線。子1つずつに水平線を引かないことで重なりを減らす。
                    drawLine(color = trunkColor, start = Offset(minX, bus), end = Offset(maxX, bus), strokeWidth = trunkStroke)
                    // 枝: バスから各子へ下ろす。
                    childPxList.forEach { (childId, childPx, _) ->
                        val isChildHighlighted = selectedNodeId == null || isParentSelected || selectedNodeId == childId
                        val branchColor = if (isChildHighlighted) TREE_LINE_COLOR else TREE_LINE_COLOR.copy(alpha = DIMMED_LINE_ALPHA)
                        val branchStroke = if (selectedNodeId != null && isChildHighlighted) edgeStroke * 1.4f else edgeStroke
                        drawLine(color = branchColor, start = Offset(childPx.x, bus), end = childPx, strokeWidth = branchStroke)
                    }
                }
                if (todayInRange) {
                    val todayX = dateToX(today).toPx()
                    drawLine(
                        color = TODAY_LINE_COLOR,
                        start = Offset(todayX, 0f),
                        end = Offset(todayX, contentHeight.toPx()),
                        strokeWidth = edgeStroke * 1.6f
                    )
                }
                tree.peerLinks.forEach { link ->
                    val from = nodesById[link.fromId] ?: return@forEach
                    val to = nodesById[link.toId] ?: return@forEach
                    val a = centerOf(from)
                    val b = centerOf(to)
                    val isHighlighted = selectedNodeId == null ||
                        selectedNodeId == link.fromId ||
                        selectedNodeId == link.toId
                    val color = if (isHighlighted) PEER_LINK_COLOR else PEER_LINK_COLOR.copy(alpha = DIMMED_LINE_ALPHA)
                    drawLine(
                        color = color,
                        start = Offset(a.x.dp.toPx(), a.y.dp.toPx()),
                        end = Offset(b.x.dp.toPx(), b.y.dp.toPx()),
                        strokeWidth = if (isHighlighted && selectedNodeId != null) edgeStroke * 1.4f else edgeStroke,
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
                    isSelected = selectedNodeId == node.id,
                    modifier = Modifier.offset(x = topLeft.x.dp, y = topLeft.y.dp),
                    onClick = { onNodeClick(node) },
                    onLongClick = {
                        selectedNodeId = if (selectedNodeId == node.id) null else node.id
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeNodeCard(
    node: TreeNode,
    scale: Float,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 無地ではなく階層色をうっすら混ぜた塗り(不透明)にして、ポップな印象にする。
    // 線を完全に隠すため透過なしで塗る点は変えない。
    val fillColor = lerp(MaterialTheme.colorScheme.surfaceVariant, node.tier.color(), CARD_TINT_RATIO)
    val isDone = node.status == NodeStatus.DONE
    Column(
        modifier = modifier
            .width(NODE_WIDTH)
            .counterScale(scale)
            // 完了したものは少し暗くして、進み具合が一目でわかるようにする。
            .graphicsLayer(alpha = if (isDone) 0.55f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(fillColor)
            // 長押しで選ぶと、そのノードに関わる線だけが強調されるので、
            // 選択中であることが分かるよう枠を太くする。
            .border(if (isSelected) 4.dp else 2.dp, node.tier.color(), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "完了",
                    tint = node.tier.color(),
                    modifier = Modifier.size(10.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(node.tier.color())
                )
            }
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
