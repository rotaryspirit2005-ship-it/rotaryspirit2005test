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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.schedulelink.ui.common.AppFab
import com.example.schedulelink.ui.common.EmptyState
import com.example.schedulelink.ui.theme.GoalColorDark
import com.example.schedulelink.ui.theme.GoalColorLight
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.MilestoneColorDark
import com.example.schedulelink.ui.theme.MilestoneColorLight
import com.example.schedulelink.ui.theme.PeerLinkColorDark
import com.example.schedulelink.ui.theme.PeerLinkColorLight
import com.example.schedulelink.ui.theme.ScheduleColorDark
import com.example.schedulelink.ui.theme.ScheduleColorLight
import com.example.schedulelink.ui.theme.TodayLineColorDark
import com.example.schedulelink.ui.theme.TodayLineColorLight
import com.example.schedulelink.ui.theme.fadeThrough
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val PX_PER_DAY = 8f
/** 題名が省略されにくいよう広めにとる(WholeTreeLayoutのNODE_WIDTH_DAYSと対応させる)。 */
private val NODE_WIDTH = 144.dp
private val NODE_HEIGHT = 52.dp
private val LANE_HEIGHT = NODE_HEIGHT + 14.dp
private val TIER_GAP = 36.dp
private val RULER_HEIGHT = 48.dp
private val MARGIN = 24.dp

/** 日の目盛り(線)は、この拡大率まで拡大したときだけ見え始める(通常の大きさでは線が多すぎて見づらい)。 */
private const val DAY_TICK_FADE_START = 1.5f
/** 日の数字は線より少し遅れて、この拡大率あたりから見え始める。 */
private const val DAY_LABEL_FADE_START = 1.5f
/** ここまで拡大すると、線・数字とも前景色(完全に見える状態)になる。 */
private const val DAY_DETAIL_FADE_END = 4f
/** 期間が長すぎる場合、日の目盛りは大量になりすぎるので出さない。 */
private const val MAX_DAY_MARKS = 1000

// ツリーの接続線・月の目盛りは、MaterialThemeのonSurfaceを基準に組み立てる。
// 固定の色にすると片方のモードで背景に埋もれたり逆に浮きすぎたりするが、
// onSurfaceは「今の背景に対してよく見える色」を常に指すため、ライト/ダーク
// どちらに切り替えても同じ見え方(接続線ははっきり、目盛りはうっすら)を保てる。
private const val RULER_LINE_ALPHA = 0.12f

/** 日の目盛りのフェード開始色(薄い状態)。中間的な明るさなので、どちらの背景でも視認できる。 */
private val DAY_DETAIL_GRAY = Color(0xFF808080)
/** カードの塗りに階層色をどれだけ混ぜるか(0=無地、1=階層色そのまま)。 */
private const val CARD_TINT_RATIO = 0.12f

/** 日の目盛りの最低限の透明度(0 = 拡大するまでは出さない)。 */
private const val DAY_TICK_MIN_ALPHA = 0f

/** 親子をつなぐ横線(バス)どうしの縦の間隔。横に重なるバスは段を分けてこの間隔で並べる。 */
private val BUS_SPACING = 12.dp
/** 横に重なっているとみなすバスどうしの最小の隙間。 */
private val BUS_MIN_GAP = 8.dp
/** 接続線の色の濃さ(親の階層色をこの透明度で使う)。 */
private const val CONNECTOR_ALPHA = 0.55f
/** 今日の線の濃さ。接続線より下に描き、カードや線を邪魔しない程度にする。 */
private const val TODAY_LINE_ALPHA = 0.7f

/** 選択中のノードと無関係な線を薄くする際の透明度。 */
private const val DIMMED_LINE_ALPHA = 0.15f

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
    val outline by viewModel.outline.collectAsState()
    // 0 = ツリー(既定)、1 = タイムライン。
    var viewMode by rememberSaveable { mutableStateOf(0) }

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
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = { fadeThrough() },
                modifier = Modifier.weight(1f),
                label = "wholeMapMode"
            ) { mode ->
                if (mode == 0) {
                    // 読み込み前(null)は何も出さない。
                    outline?.let {
                        WholeTreeOutlineView(
                            outline = it,
                            onGoalClick = onGoalClick,
                            onMilestoneClick = onMilestoneClick,
                            onScheduleClick = onScheduleClick,
                            onAddGoalClick = onAddGoalClick
                        )
                    } ?: Box(modifier = Modifier.fillMaxSize())
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (tree.nodes.isNotEmpty()) {
                            TreeLegend()
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (tree.nodes.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Outlined.Flag,
                                    message = "まだ大目的がありません",
                                    modifier = Modifier.fillMaxSize(),
                                    actionLabel = "大目的を追加",
                                    onAction = onAddGoalClick
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
            }
        }
    }
}

/** 色の意味(階層)が初見でも分かるよう、目的マップ上部に出す簡易凡例。 */
@Composable
private fun TreeLegend() {
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TreeLegendItem(color = TreeTier.GOAL.color(isDark), label = "大目的")
        TreeLegendItem(color = TreeTier.MILESTONE.color(isDark), label = "中日程")
        TreeLegendItem(color = TreeTier.SCHEDULE.color(isDark), label = "小日程")
    }
}

@Composable
private fun TreeLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    // 横方向に重なるバスどうしは別の段(高さ)に割り当てる(区間スケジューリングの貪欲法)。
    // 重ならないバスは同じ段を使い回すので、隙間の高さは必要な段数だけで済む。
    val busLayout = remember(tree) { assignBusSlots(tree, parentGroups, nodesById) }
    // 長押しで選んだノードに関わる線だけをくっきり見せ、他は薄くして見やすくする。
    var selectedNodeId by remember(tree) { mutableStateOf<String?>(null) }

    val isDark = LocalIsDarkTheme.current
    // 目盛りはonSurfaceをうっすら(低いalpha)使い、ライト/ダークどちらでも背景に馴染ませる。
    // 接続線は親の階層色で描く(描画部分を参照)。
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val rulerLineColor = onSurfaceColor.copy(alpha = RULER_LINE_ALPHA)
    val peerLinkColor = if (isDark) PeerLinkColorDark else PeerLinkColorLight
    val todayLineColor = if (isDark) TodayLineColorDark else TodayLineColorLight

    fun dateToX(date: LocalDate): Dp =
        MARGIN + (ChronoUnit.DAYS.between(tree.minDate, date) * PX_PER_DAY).dp

    fun maxLane(tier: TreeTier): Int = nodesByTier[tier]?.maxOfOrNull { it.lane } ?: -1

    fun slotCount(parentTier: TreeTier): Int = busLayout.slotCountByTier[parentTier] ?: 1
    // 親の階層の下の隙間は、バスの段数に応じて広げる(段が多くても線が詰まらないように)。
    fun gapBelow(parentTier: TreeTier): Dp = maxOf(TIER_GAP, BUS_SPACING * (slotCount(parentTier) + 1))

    val goalBaseY = RULER_HEIGHT + TIER_GAP
    val goalTierHeight = LANE_HEIGHT * (maxLane(TreeTier.GOAL) + 1).coerceAtLeast(0)
    val milestoneBaseY = goalBaseY + goalTierHeight + gapBelow(TreeTier.GOAL)
    val milestoneTierHeight = LANE_HEIGHT * (maxLane(TreeTier.MILESTONE) + 1).coerceAtLeast(0)
    val scheduleBaseY = milestoneBaseY + milestoneTierHeight + gapBelow(TreeTier.MILESTONE)
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
        val fraction = (slot + 1f) / (slotCount(parentTier) + 1f)
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
        // 拡大するほどグレーから前景色(onSurface)へ、じわじわ濃くなっていく
        // (数字は線より少し遅れて追いつく。onSurfaceを使うためライト/ダーク両方で成立する)。
        val tickProgress = ((scale - DAY_TICK_FADE_START) / (DAY_DETAIL_FADE_END - DAY_TICK_FADE_START)).coerceIn(0f, 1f)
        val labelProgress = ((scale - DAY_LABEL_FADE_START) / (DAY_DETAIL_FADE_END - DAY_LABEL_FADE_START)).coerceIn(0f, 1f)
        val dayTickColor = lerp(DAY_DETAIL_GRAY, onSurfaceColor, tickProgress)
            .copy(alpha = DAY_TICK_MIN_ALPHA + (1f - DAY_TICK_MIN_ALPHA) * tickProgress)
        val dayLabelColor = lerp(DAY_DETAIL_GRAY, onSurfaceColor, labelProgress).copy(alpha = labelProgress)

        Box(modifier = Modifier.width(contentWidth).height(contentHeight)) {
            Canvas(modifier = Modifier.width(contentWidth).height(contentHeight)) {
                // 線の太さも、拡大しすぎたときだけ際限なく太くならないよう抑える。
                val strokeFactor = capScale(scale)
                val hairline = (1.dp * strokeFactor).toPx()
                val edgeStroke = (1.dp * strokeFactor).toPx()

                marks.forEach { (date, _) ->
                    val x = dateToX(date).toPx()
                    drawLine(
                        color = rulerLineColor,
                        start = Offset(x, RULER_HEIGHT.toPx()),
                        end = Offset(x, contentHeight.toPx()),
                        strokeWidth = hairline
                    )
                }

                if (tickProgress > 0f) dayMarksList.forEach { date ->
                    val x = dateToX(date).toPx()
                    drawLine(
                        color = dayTickColor,
                        start = Offset(x, RULER_HEIGHT.toPx()),
                        end = Offset(x, contentHeight.toPx()),
                        strokeWidth = hairline
                    )
                }

                // 今日の線は接続線より先(下)に描き、線の交差を増やさないようにする。
                if (todayInRange) {
                    val todayX = dateToX(today).toPx()
                    drawLine(
                        color = todayLineColor.copy(alpha = TODAY_LINE_ALPHA),
                        start = Offset(todayX, 0f),
                        end = Offset(todayX, contentHeight.toPx()),
                        strokeWidth = edgeStroke * 1.6f
                    )
                }

                parentGroups.forEach { (parentId, childIds) ->
                    val parent = nodesById[parentId] ?: return@forEach
                    val children = childIds.mapNotNull { nodesById[it] }
                    if (children.isEmpty()) return@forEach

                    val slot = busLayout.slotByParent[parentId] ?: 0
                    val bus = busY(parent.tier, slot).toPx()
                    val parentTopLeft = topLeftOf(parent)
                    // 同じ日付の親(別レーン)どうしの幹が重ならないよう、段ごとにカード内の横位置をずらす。
                    val trunkX = (parentTopLeft.x.dp + NODE_WIDTH * ((slot + 1f) / (slotCount(parent.tier) + 1f))).toPx()
                    val trunkTop = (parentTopLeft.y.dp + NODE_HEIGHT).toPx()
                    val childPxList = children.map { child ->
                        val topLeft = topLeftOf(child)
                        Triple(child.id, Offset((topLeft.x.dp + NODE_WIDTH / 2).toPx(), topLeft.y.dp.toPx()), child)
                    }
                    val minX = minOf(trunkX, childPxList.minOf { it.second.x })
                    val maxX = maxOf(trunkX, childPxList.maxOf { it.second.x })

                    // 線は親の階層色で描き、どの親からの線かを色でも追えるようにする。
                    val baseColor = parent.tier.color(isDark)
                    val isParentSelected = selectedNodeId != null && selectedNodeId == parentId
                    val groupHighlighted = selectedNodeId == null ||
                        isParentSelected ||
                        childPxList.any { it.first == selectedNodeId }
                    val trunkColor = when {
                        !groupHighlighted -> baseColor.copy(alpha = DIMMED_LINE_ALPHA)
                        selectedNodeId != null -> baseColor
                        else -> baseColor.copy(alpha = CONNECTOR_ALPHA)
                    }
                    val trunkStroke = if (selectedNodeId != null && groupHighlighted) edgeStroke * 1.6f else edgeStroke

                    // 幹: 親カードの下端から、兄弟をまとめる共通のバス(横線)まで下ろす。
                    drawLine(color = trunkColor, start = Offset(trunkX, trunkTop), end = Offset(trunkX, bus), strokeWidth = trunkStroke)
                    // バス: 兄弟をまとめる横線。
                    drawLine(color = trunkColor, start = Offset(minX, bus), end = Offset(maxX, bus), strokeWidth = trunkStroke)
                    // 枝: バスから各子カードの上端へ下ろす。
                    childPxList.forEach { (childId, childTop, _) ->
                        val isChildHighlighted = selectedNodeId == null || isParentSelected || selectedNodeId == childId
                        val branchColor = when {
                            !isChildHighlighted -> baseColor.copy(alpha = DIMMED_LINE_ALPHA)
                            selectedNodeId != null -> baseColor
                            else -> baseColor.copy(alpha = CONNECTOR_ALPHA)
                        }
                        val branchStroke = if (selectedNodeId != null && isChildHighlighted) edgeStroke * 1.6f else edgeStroke
                        drawLine(color = branchColor, start = Offset(childTop.x, bus), end = childTop, strokeWidth = branchStroke)
                    }
                }
                tree.peerLinks.forEach { link ->
                    val from = nodesById[link.fromId] ?: return@forEach
                    val to = nodesById[link.toId] ?: return@forEach
                    val a = centerOf(from)
                    val b = centerOf(to)
                    val isHighlighted = selectedNodeId == null ||
                        selectedNodeId == link.fromId ||
                        selectedNodeId == link.toId
                    val color = if (isHighlighted) peerLinkColor else peerLinkColor.copy(alpha = DIMMED_LINE_ALPHA)
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
    val isDark = LocalIsDarkTheme.current
    val tierColor = node.tier.color(isDark)
    val isDone = node.status == NodeStatus.DONE
    // 背景の線を完全に隠すため、透過なしで塗る。階層色はごく薄く混ぜる程度にして
    // フラットな印象を保つ。完了したものは透明度で薄くすると文字が読みにくくなるため、
    // 一段沈んだ面の色と控えめな枠線で「済んだ」ことを表す。
    val fillColor = if (isDone) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        lerp(MaterialTheme.colorScheme.surface, tierColor, CARD_TINT_RATIO)
    }
    val borderColor = if (isDone && !isSelected) MaterialTheme.colorScheme.outlineVariant else tierColor
    val textColor = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .width(NODE_WIDTH)
            .counterScale(scale)
            .clip(shape)
            .background(fillColor)
            // 長押しで選ぶと、そのノードに関わる線だけが強調されるので、
            // 選択中であることが分かるよう枠を太くする。
            .border(if (isSelected) 3.dp else 1.5.dp, borderColor, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "完了",
                    tint = tierColor,
                    modifier = Modifier.size(10.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tierColor)
                )
            }
            Text(
                text = node.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor
            )
        }
        if (node.subtitle.isNotBlank()) {
            Text(
                text = node.subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else tierColor,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}

private data class BusLayout(val slotByParent: Map<String, Int>, val slotCountByTier: Map<TreeTier, Int>)

/**
 * 親ごとのバス(兄弟をまとめる横線)の段を決める。親の階層ごとに、横の範囲が重なるバスには
 * 別の段を、重ならないバスには同じ段を割り当てる(左端の順に、空いている一番上の段へ詰める)。
 * 横位置は日付だけで決まるので、縦の配置を決める前に計算できる。
 */
private fun assignBusSlots(
    tree: WholeTree,
    parentGroups: List<Pair<String, List<String>>>,
    nodesById: Map<String, TreeNode>
): BusLayout {
    fun leftOf(node: TreeNode): Float = ChronoUnit.DAYS.between(tree.minDate, node.date) * PX_PER_DAY
    val slotByParent = mutableMapOf<String, Int>()
    val slotCountByTier = mutableMapOf<TreeTier, Int>()
    parentGroups
        .mapNotNull { (parentId, childIds) ->
            val parent = nodesById[parentId] ?: return@mapNotNull null
            val childCenters = childIds.mapNotNull { nodesById[it] }.map { leftOf(it) + NODE_WIDTH.value / 2 }
            if (childCenters.isEmpty()) return@mapNotNull null
            val left = minOf(leftOf(parent), childCenters.min())
            val right = maxOf(leftOf(parent) + NODE_WIDTH.value, childCenters.max())
            Triple(parent, left, right)
        }
        .groupBy { it.first.tier }
        .forEach { (tier, spans) ->
            val slotRightEdges = mutableListOf<Float>()
            spans.sortedBy { it.second }.forEach { (parent, left, right) ->
                var slot = slotRightEdges.indexOfFirst { it + BUS_MIN_GAP.value < left }
                if (slot == -1) {
                    slot = slotRightEdges.size
                    slotRightEdges.add(right)
                } else {
                    slotRightEdges[slot] = right
                }
                slotByParent[parent.id] = slot
            }
            slotCountByTier[tier] = slotRightEdges.size.coerceAtLeast(1)
        }
    return BusLayout(slotByParent, slotCountByTier)
}
