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
import androidx.compose.material.icons.outlined.Flag
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

/** カードの塗りに階層色をどれだけ混ぜるか(0=無地、1=階層色そのまま)。 */
private const val CARD_TINT_RATIO = 0.12f

/** 接続線の色の濃さ(親ごとの色をこの透明度で使う。暗い背景でも濁らないよう高めにする)。 */
private const val CONNECTOR_ALPHA = 0.9f
/** 色を付けない線(子が1つだけの親から出る線)の濃さ。 */
private const val NEUTRAL_CONNECTOR_ALPHA = 0.7f
/** 子カードの左端につける、親の線と同じ色の帯の幅。 */
private val BRANCH_STRIPE_WIDTH = 5.dp

/** 選択中のノードと無関係な線を薄くする際の透明度。 */
private const val DIMMED_LINE_ALPHA = 0.22f

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
    val outline by viewModel.outline.collectAsState()
    // 0 = ツリー(既定)、1 = タイムライン。
    var viewMode by rememberSaveable { mutableStateOf(0) }
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
                viewStateHolder.SaveableStateProvider(mode) {
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
                        // 配線の計算は重いので、タイムライン表示のときだけ購読する。
        val state by viewModel.timeline.collectAsState()
                        Column(modifier = Modifier.fillMaxSize()) {
                            val current = state
                            if (current != null && current.tree.nodes.isNotEmpty()) {
                                TreeLegend()
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (current == null) {
                                    // 配線を計算している間(バックグラウンド)。
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                } else if (current.tree.nodes.isEmpty()) {
                                    EmptyState(
                                        icon = Icons.Outlined.Flag,
                                        message = "まだ大目的がありません",
                                        modifier = Modifier.fillMaxSize(),
                                        actionLabel = "大目的を追加",
                                        onAction = onAddGoalClick
                                    )
                                } else {
                                    WholeTreeCanvas(
                                        state = current,
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
}

/** 色の意味(階層)が初見でも分かるよう、目的マップ上部に出す簡易凡例。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeLegend() {
    val isDark = LocalIsDarkTheme.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TreeLegendItem("大目的") { LegendDot(TreeTier.GOAL.color(isDark)) }
        TreeLegendItem("中日程") { LegendDot(TreeTier.MILESTONE.color(isDark)) }
        TreeLegendItem("小日程") { LegendDot(TreeTier.SCHEDULE.color(isDark)) }
        // 線の色は階層ではなく「どの親か」を表す。
        TreeLegendItem("同じ色の線=同じ親") {
            LegendLine(listOf(connectorColor(2, isDark), connectorColor(5, isDark)), dashed = false)
        }
        TreeLegendItem("予定どうしのリンク") {
            LegendLine(listOf(if (isDark) PeerLinkColorDark else PeerLinkColorLight), dashed = true)
        }
        TreeLegendItem("今日") {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(if (isDark) TodayLineColorDark else TodayLineColorLight)
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WholeTreeCanvas(state: TimelineState, onNodeClick: (TreeNode) -> Unit) {
    val tree = state.tree
    val layout = state.layout
    val gridModel = remember(tree) { GridModel(tree.minDate, tree.maxDate, monthMarks(tree.minDate, tree.maxDate)) }
    // 長押しで選んだノードに関わる線だけをくっきり見せ、他は薄くして見やすくする。
    var selectedNodeId by remember(tree) { mutableStateOf<String?>(null) }

    val isDark = LocalIsDarkTheme.current
    val peerLinkColor = if (isDark) PeerLinkColorDark else PeerLinkColorLight
    // 背景の月日の線と上端のルーラーは、画面の座標で描く(TimelineGrid.kt)。
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val gridColors = remember(isDark, colorScheme) {
        GridColors(
            isDark = isDark,
            onSurface = colorScheme.onSurface,
            onSurfaceVariant = colorScheme.onSurfaceVariant,
            surface = colorScheme.surface,
            outlineVariant = colorScheme.outlineVariant,
            today = if (isDark) TodayLineColorDark else TodayLineColorLight,
            onToday = if (isDark) DarkOnError else Color.White,
            saturday = if (isDark) SaturdayColorDark else SaturdayColorLight,
            sunday = if (isDark) SundayColorDark else SundayColorLight
        )
    }
    val rulerStyles = remember(typography) {
        RulerStyles(
            month = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            day = typography.labelSmall,
            pill = typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)

    fun dateToX(date: LocalDate): Dp = timelineX(tree.minDate, date).dp

    // 同じ親から出る線は途中で重なる。1本ずつ半透明で塗ると重なりが濃くなるので、
    // 親ごと(強調する/しないごと)に1つの経路にまとめて塗る。強調する線は最後に描く。
    val routeGroups = remember(layout, selectedNodeId) {
        layout.routes.filter { it.points.size >= 2 }.groupBy { route ->
            val highlighted = selectedNodeId == null ||
                selectedNodeId == route.fromId ||
                selectedNodeId == route.toId
            Triple(
                if (route.kind == RouteKind.PEER) "peer:${route.fromId}|${route.toId}" else "tree:${route.fromId}",
                route.kind,
                highlighted
            )
        }.entries.sortedBy { it.key.third }
    }
    // 子カードの左端につける帯の色(色を付けた線の子だけ)。
    val branchSlotByChild = remember(layout) {
        layout.routes.filter { it.kind == RouteKind.TREE && it.colorSlot >= 0 }.associate { it.toId to it.colorSlot }
    }
    val neutralLineColor = MaterialTheme.colorScheme.onSurfaceVariant

    val contentWidth = layout.width.dp
    val contentHeight = layout.height.dp
    val today = remember { LocalDate.now() }
    val todayInRange = !today.isBefore(tree.minDate) && !today.isAfter(tree.maxDate)

    ZoomPanBox(
        modifier = Modifier.fillMaxSize(),
        initialFocusX = if (todayInRange) dateToX(today) else null,
        backdrop = { zoomPan ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawGridBackdrop(zoomPan, gridModel, gridColors, if (todayInRange) today else null)
            }
        },
        overlay = { zoomPan ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawGridRuler(zoomPan, gridModel, gridColors, if (todayInRange) today else null, textMeasurer, rulerStyles)
            }
        }
    ) { scale ->
        Box(modifier = Modifier.width(contentWidth).height(contentHeight)) {
            Canvas(modifier = Modifier.width(contentWidth).height(contentHeight)) {
                // 線の太さも、拡大しすぎたときだけ際限なく太くならないよう抑える。
                val strokeFactor = capScale(scale)
                val edgeStroke = (1.5.dp * strokeFactor).toPx()

                // 接続線。配置のときに、カードを避けて別の線と重ならない道を計算してある。
                // 木の線は親ごとの色(子が1つだけの親は無彩色)、予定どうしのリンクは破線で描く。
                routeGroups.forEach { (key, routes) ->
                    val isPeer = key.second == RouteKind.PEER
                    val highlighted = key.third
                    val slot = routes.first().colorSlot
                    val baseColor = when {
                        isPeer -> peerLinkColor
                        slot >= 0 -> connectorColor(slot, isDark)
                        else -> neutralLineColor
                    }
                    val color = when {
                        !highlighted -> baseColor.copy(alpha = DIMMED_LINE_ALPHA)
                        selectedNodeId != null || isPeer -> baseColor
                        slot >= 0 -> baseColor.copy(alpha = CONNECTOR_ALPHA)
                        else -> baseColor.copy(alpha = NEUTRAL_CONNECTOR_ALPHA)
                    }
                    val path = Path().apply {
                        routes.forEach { route ->
                            val first = route.points.first()
                            moveTo(first.x.dp.toPx(), first.y.dp.toPx())
                            for (i in 1 until route.points.size) {
                                val p = route.points[i]
                                lineTo(p.x.dp.toPx(), p.y.dp.toPx())
                            }
                        }
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = if (selectedNodeId != null && highlighted) edgeStroke * 1.6f else edgeStroke,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (isPeer) {
                                PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx() * strokeFactor, 5.dp.toPx() * strokeFactor))
                            } else {
                                null
                            }
                        )
                    )
                    // 線の両端に丸をつけ、どのカードのどこから出てどこへ入るのかを分かりやすくする。
                    if (!isPeer) {
                        val radius = (if (selectedNodeId != null && highlighted) 4.dp else 2.5.dp).toPx() * strokeFactor
                        // 拡大するとカードは中心に向かって縮むので、丸もカードの縁に合わせて中心へ寄せる。
                        fun edgeOf(anchor: Offset, id: String): Offset {
                            val box = layout.boxes[id] ?: return Offset(anchor.x.dp.toPx(), anchor.y.dp.toPx())
                            return Offset(
                                (box.centerX + (anchor.x - box.centerX) * strokeFactor).dp.toPx(),
                                (box.centerY + (anchor.y - box.centerY) * strokeFactor).dp.toPx()
                            )
                        }
                        routes.distinctBy { it.startAnchor }.forEach { route ->
                            drawCircle(color, radius, edgeOf(route.startAnchor, route.fromId))
                        }
                        routes.forEach { route ->
                            drawCircle(color, radius, edgeOf(route.endAnchor, route.toId))
                        }
                    }
                }
            }

            tree.nodes.forEach { node ->
                val box = layout.boxes[node.id] ?: return@forEach
                TreeNodeCard(
                    node = node,
                    scale = scale,
                    isSelected = selectedNodeId == node.id,
                    branchColor = branchSlotByChild[node.id]?.let { connectorColor(it, isDark) },
                    modifier = Modifier.offset(x = box.left.dp, y = box.top.dp),
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
    /** 親から来る線の色。左端に同じ色の帯をつけて、どの線につながるカードかを分かりやすくする。 */
    branchColor: Color?,
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
            .width(NODE_WIDTH.dp)
            .height(NODE_HEIGHT.dp)
            .counterScale(scale)
            .clip(shape)
            .background(fillColor)
            .then(
                if (branchColor == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        drawRect(color = branchColor, size = Size(BRANCH_STRIPE_WIDTH.toPx(), size.height))
                    }
                }
            )
            // 長押しで選ぶと、そのノードに関わる線だけが強調されるので、
            // 選択中であることが分かるよう枠を太くする。
            .border(if (isSelected) 3.dp else 1.5.dp, borderColor, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        // 配線は高さNODE_HEIGHTのカードとして計算しているので、副題がなくても高さは変えない。
        verticalArrangement = Arrangement.Center
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
