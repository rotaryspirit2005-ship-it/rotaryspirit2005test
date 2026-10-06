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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val timeline by viewModel.timeline.collectAsState()
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
                        val state = timeline
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (state != null && state.tree.nodes.isNotEmpty()) {
                                TreeLegend()
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (state == null) {
                                    // 配線を計算している間(バックグラウンド)。
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                } else if (state.tree.nodes.isEmpty()) {
                                    EmptyState(
                                        icon = Icons.Outlined.Flag,
                                        message = "まだ大目的がありません",
                                        modifier = Modifier.fillMaxSize(),
                                        actionLabel = "大目的を追加",
                                        onAction = onAddGoalClick
                                    )
                                } else {
                                    WholeTreeCanvas(
                                        state = state,
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
private fun WholeTreeCanvas(state: TimelineState, onNodeClick: (TreeNode) -> Unit) {
    val tree = state.tree
    val layout = state.layout
    val marks = remember(tree) { monthMarks(tree.minDate, tree.maxDate) }
    val dayMarksList = remember(tree) { dayMarks(tree.minDate, tree.maxDate) }
    // 長押しで選んだノードに関わる線だけをくっきり見せ、他は薄くして見やすくする。
    var selectedNodeId by remember(tree) { mutableStateOf<String?>(null) }

    val isDark = LocalIsDarkTheme.current
    // 目盛りはonSurfaceをうっすら(低いalpha)使い、ライト/ダークどちらでも背景に馴染ませる。
    // 接続線は親の階層色で描く(描画部分を参照)。
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val rulerLineColor = onSurfaceColor.copy(alpha = RULER_LINE_ALPHA)
    val peerLinkColor = if (isDark) PeerLinkColorDark else PeerLinkColorLight
    val todayLineColor = if (isDark) TodayLineColorDark else TodayLineColorLight

    fun dateToX(date: LocalDate): Dp = timelineX(tree.minDate, date).dp

    val contentWidth = layout.width.dp
    val contentHeight = layout.height.dp
    val rulerHeight = RULER_HEIGHT.dp
    val today = remember { LocalDate.now() }
    val todayInRange = !today.isBefore(tree.minDate) && !today.isAfter(tree.maxDate)

    ZoomPanBox(
        modifier = Modifier.fillMaxSize(),
        initialFocusX = if (todayInRange) dateToX(today) else null
    ) { scale ->
        // 拡大率に応じて0〜1で滑らかに変化する進捗値。日の目盛りは拡大するほど
        // グレーから前景色(onSurface)へ、じわじわ濃くなっていく
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
                        start = Offset(x, rulerHeight.toPx()),
                        end = Offset(x, contentHeight.toPx()),
                        strokeWidth = hairline
                    )
                }

                if (tickProgress > 0f) dayMarksList.forEach { date ->
                    val x = dateToX(date).toPx()
                    drawLine(
                        color = dayTickColor,
                        start = Offset(x, rulerHeight.toPx()),
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

                // 接続線。配置のときに、カードを避けて別の線と重ならない道を計算してある。
                // 木の線は親の階層色、予定どうしのリンクは破線で描く。
                layout.routes.forEach { route ->
                    if (route.points.size < 2) return@forEach
                    val isPeer = route.kind == RouteKind.PEER
                    val baseColor = if (isPeer) peerLinkColor else route.parentTier.color(isDark)
                    val highlighted = selectedNodeId == null ||
                        selectedNodeId == route.fromId ||
                        selectedNodeId == route.toId
                    val color = when {
                        !highlighted -> baseColor.copy(alpha = DIMMED_LINE_ALPHA)
                        selectedNodeId != null || isPeer -> baseColor
                        else -> baseColor.copy(alpha = CONNECTOR_ALPHA)
                    }
                    val path = Path().apply {
                        val first = route.points.first()
                        moveTo(first.x.dp.toPx(), first.y.dp.toPx())
                        for (i in 1 until route.points.size) {
                            val p = route.points[i]
                            lineTo(p.x.dp.toPx(), p.y.dp.toPx())
                        }
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = if (selectedNodeId != null && highlighted) edgeStroke * 1.6f else edgeStroke,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (isPeer) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                        )
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
                val box = layout.boxes[node.id] ?: return@forEach
                TreeNodeCard(
                    node = node,
                    scale = scale,
                    isSelected = selectedNodeId == node.id,
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
