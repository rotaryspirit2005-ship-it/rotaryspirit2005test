package com.example.schedulelink.ui.plan

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.schedulelink.ui.tag.LocalTagController
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.Motion
import com.example.schedulelink.ui.theme.TodayLineColorDark
import com.example.schedulelink.ui.theme.TodayLineColorLight
import com.example.schedulelink.ui.theme.tagColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

// 「表で計画」の見える化パネル。書くそばから、構造(ツリー)・日程(ミニ日程表)・分担(週×人)が育っていく。

enum class VisualTab(val label: String) { Structure("構造"), Schedule("日程"), Share("分担") }

/**
 * 見える化パネル。[onRowTap]は行へ移動(表をその行まで送る)、[onDateRequest]は日付を選ぶダイアログ、
 * [onRowDragged]は日程表で帯を動かし終えたとき(変更前と変更後)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanVisualPanel(
    rows: List<PlanRow>,
    editingId: String?,
    onRowTap: (String) -> Unit,
    onGhostTap: (String) -> Unit,
    onDateRequest: (String) -> Unit,
    onRowDragged: (before: PlanRow, after: PlanRow) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(VisualTab.Structure) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    // キーボードが出ているときは、入力欄を隠さないよう低くする
    val height = if (WindowInsets.isImeVisible) 120.dp else 240.dp

    Column(modifier = Modifier.fillMaxWidth().animateContentSize(tween(Motion.DurationMedium))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisualTabs(tab, onSelect = { tab = it }, modifier = Modifier.weight(1f))
            IconButton(onClick = { fullScreen = true }) {
                Icon(Icons.Default.Fullscreen, contentDescription = "見える化を全画面で開く")
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp, vertical = 4.dp)) {
            VisualContent(tab, rows, editingId, onRowTap, onGhostTap, onDateRequest, onRowDragged)
        }
    }

    if (fullScreen) {
        Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VisualTabs(tab, onSelect = { tab = it }, modifier = Modifier.weight(1f))
                        IconButton(onClick = { fullScreen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "全画面を閉じる")
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                        VisualContent(
                            tab, rows, editingId,
                            onRowTap = { fullScreen = false; onRowTap(it) },
                            onGhostTap = { fullScreen = false; onGhostTap(it) },
                            onDateRequest = { fullScreen = false; onDateRequest(it) },
                            onRowDragged = onRowDragged
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualTabs(selected: VisualTab, onSelect: (VisualTab) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        VisualTab.entries.forEachIndexed { index, item ->
            SegmentedButton(
                selected = item == selected,
                onClick = { onSelect(item) },
                shape = SegmentedButtonDefaults.itemShape(index, VisualTab.entries.size),
                label = { Text(item.label) }
            )
        }
    }
}

@Composable
private fun VisualContent(
    tab: VisualTab,
    rows: List<PlanRow>,
    editingId: String?,
    onRowTap: (String) -> Unit,
    onGhostTap: (String) -> Unit,
    onDateRequest: (String) -> Unit,
    onRowDragged: (PlanRow, PlanRow) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .clipToBounds()
    ) {
        when (tab) {
            VisualTab.Structure -> StructureMap(rows, editingId, onRowTap, onGhostTap)
            VisualTab.Schedule -> MiniGantt(rows, editingId, onDateRequest, onRowDragged)
            VisualTab.Share -> ShareHeatmap(rows, onRowTap)
        }
    }
}

@Composable
private fun EmptyVisual(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- A1 構造マップ ----

/** ツリーの1つの札。[placeholderFor]が入っていれば、予定のない中日程の「？」。 */
private data class MapNode(val id: String, val row: PlanRow?, val column: Int, val y: Float, val parentId: String?, val placeholderFor: String? = null)

/** 行の並びから、左(大目的)→右(予定)のツリーの配置を求める。葉を上から順に1段ずつ並べ、親は子の真ん中に置く。 */
private fun layoutTree(rows: List<PlanRow>): List<MapNode> {
    val parents = parentsOf(rows)
    val children = rows.groupBy { parents[it.id]?.id }
    val nodes = mutableListOf<MapNode>()
    var leaf = 0f
    fun place(row: PlanRow, parentId: String?): Float {
        val kids = children[row.id].orEmpty()
        val y = when {
            kids.isNotEmpty() -> kids.map { place(it, row.id) }.let { (it.first() + it.last()) / 2f }
            row.level == PlanLevel.How -> {
                // 予定のない中日程: 右に「？」を出して、まだ空いていることを見せる
                val placeholderY = leaf++
                nodes += MapNode("?${row.id}", null, 2, placeholderY, row.id, placeholderFor = row.id)
                placeholderY
            }
            else -> leaf++
        }
        nodes += MapNode(row.id, row, row.level.depth, y, parentId)
        return y
    }
    rows.filter { parents[it.id] == null }.forEach { place(it, null) }
    return nodes
}

@Composable
private fun StructureMap(rows: List<PlanRow>, editingId: String?, onRowTap: (String) -> Unit, onGhostTap: (String) -> Unit) {
    if (rows.none { it.title.isNotBlank() }) {
        EmptyVisual("なぜ(大目的)を書くと、ここに地図が育ちます")
        return
    }
    val nodes = remember(rows) { layoutTree(rows) }
    val leafCount = (nodes.maxOfOrNull { it.y } ?: 0f) + 1f
    val slot = 36.dp
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val summary = remember(rows) {
        val goals = rows.filter { it.level == PlanLevel.Why }.joinToString("、") { it.title.ifBlank { "(未入力)" } }
        val empty = nodes.count { it.placeholderFor != null }
        "構造マップ。大目的 $goals、中日程${rows.count { it.level == PlanLevel.How }}件、予定${rows.count { it.level == PlanLevel.What }}件" +
            if (empty > 0) "、予定のない中日程${empty}件" else ""
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).semantics { contentDescription = summary }) {
        val columnWidth = (maxWidth - 8.dp) / 3
        val nodeWidth = columnWidth - 14.dp
        val totalHeight = slot * leafCount + 8.dp
        val byId = nodes.associateBy { it.id }
        Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                fun centerLeft(node: MapNode) = Offset(
                    (4.dp + columnWidth * node.column).toPx(),
                    (4.dp + slot * node.y + 14.dp).toPx()
                )
                nodes.forEach { node ->
                    val parent = node.parentId?.let { byId[it] } ?: return@forEach
                    val from = Offset(centerLeft(parent).x + nodeWidth.toPx(), centerLeft(parent).y)
                    val to = centerLeft(node)
                    val mid = (from.x + to.x) / 2
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        cubicTo(mid, from.y, mid, to.y, to.x, to.y)
                    }
                    drawPath(
                        path,
                        color = lineColor,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = if (node.placeholderFor != null) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                        )
                    )
                }
            }
            nodes.forEach { node ->
                MapNodeChip(
                    node = node,
                    width = nodeWidth,
                    highlighted = node.row != null && node.id == editingId,
                    modifier = Modifier.offset(x = 4.dp + columnWidth * node.column, y = 4.dp + slot * node.y),
                    onClick = {
                        if (node.placeholderFor != null) onGhostTap(node.placeholderFor) else onRowTap(node.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun MapNodeChip(node: MapNode, width: Dp, highlighted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // 新しい札は、ふわっと大きくなりながら出てくる
    val appear = remember(node.id) { Animatable(0f) }
    LaunchedEffect(node.id) { appear.animateTo(1f, tween(Motion.DurationLong, easing = Motion.EmphasizedDecelerate)) }
    val row = node.row
    val color = if (row != null) levelColor(row.level) else MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = appear.value
                scaleX = 0.8f + 0.2f * appear.value
                scaleY = 0.8f + 0.2f * appear.value
            }
            .width(width)
            .height(28.dp)
            .then(
                if (row == null) {
                    Modifier.dashedOutline(MaterialTheme.colorScheme.outline)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface, shape)
                }
            )
            .then(if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row != null) {
            Box(Modifier.width(4.dp).height(28.dp).background(color, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
        }
        Text(
            text = row?.title?.ifBlank { "(${row.level.label})" } ?: "？ 予定を足す",
            style = MaterialTheme.typography.labelMedium,
            color = if (row == null || row.title.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

private fun Modifier.dashedOutline(color: Color): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    )
}

// ---- A2 ミニ日程表 ----

private enum class DragMode { Move, ResizeStart, ResizeEnd, Pan }

private data class GanttLane(val row: PlanRow, val start: LocalDate, val end: LocalDate, val warning: String?)

private data class GanttGap(val laneIndex: Int, val from: LocalDate, val to: LocalDate) {
    val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1
}

private data class GanttDrag(val laneIndex: Int, val mode: DragMode, val accumulated: Float = 0f, val deltaDays: Long = 0)

private val shortMonthDay = java.time.format.DateTimeFormatter.ofPattern("M/d")

/** 日付のある行を、1行1段の帯にする。予定が中日程の期間から外れていれば警告を付ける。 */
private fun ganttLanes(rows: List<PlanRow>, today: LocalDate): Pair<List<GanttLane>, List<GanttGap>> {
    val resolved = resolvePlan(rows, emptyList(), emptyList()).associateBy { it.row.id }
    val parents = parentsOf(rows)
    val lanes = rows.mapNotNull { row ->
        val r = resolved[row.id] ?: return@mapNotNull null
        val start = r.start ?: return@mapNotNull null
        val end = if (row.level == PlanLevel.What) start else (r.end ?: start)
        val parent = parents[row.id]?.let { resolved[it.id] }
        val warning = when {
            row.level == PlanLevel.What && parent?.start != null && parent.end != null &&
                (start.isBefore(parent.start) || start.isAfter(parent.end)) -> "中日程の期間外"
            end.isBefore(today) -> "過去"
            else -> null
        }
        GanttLane(row, start, end, warning)
    }
    // 同じ大目的の中日程どうしの間に、何もない日が続いているところ
    val gaps = mutableListOf<GanttGap>()
    lanes.filter { it.row.level == PlanLevel.How }
        .groupBy { parents[it.row.id]?.id }
        .forEach { (goalId, milestones) ->
            val goalLane = lanes.indexOfFirst { it.row.id == goalId }
            if (goalLane < 0) return@forEach
            milestones.sortedBy { it.start }.zipWithNext().forEach { (a, b) ->
                if (b.start.isAfter(a.end.plusDays(1))) gaps += GanttGap(goalLane, a.end.plusDays(1), b.start.minusDays(1))
            }
        }
    return lanes to gaps
}

@Composable
private fun MiniGantt(
    rows: List<PlanRow>,
    editingId: String?,
    onDateRequest: (String) -> Unit,
    onRowDragged: (PlanRow, PlanRow) -> Unit
) {
    val today = remember { LocalDate.now() }
    val (lanes, gaps) = remember(rows) { ganttLanes(rows, today) }
    if (lanes.isEmpty()) {
        EmptyVisual("いつ？を決めると、ここに線が引かれます")
        return
    }
    val minDate = minOf(lanes.minOf { it.start }, today).minusDays(2)
    val maxDate = maxOf(lanes.maxOf { it.end }, today).plusDays(2)
    val totalDays = ChronoUnit.DAYS.between(minDate, maxDate) + 1

    val isDark = LocalIsDarkTheme.current
    val todayColor = if (isDark) TodayLineColorDark else TodayLineColorLight
    val colors = MaterialTheme.colorScheme
    val laneColors = PlanLevel.entries.associateWith { levelColor(it) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = colors.onSurface)
    val rulerStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val currentLanes by rememberUpdatedState(lanes)
    val currentOnDrag by rememberUpdatedState(onRowDragged)
    val currentOnDate by rememberUpdatedState(onDateRequest)

    val warningCount = lanes.count { it.warning == "中日程の期間外" } + gaps.size
    val description = "日程表。${minDate.plusDays(2).format(shortMonthDay)}から${maxDate.minusDays(2).format(shortMonthDay)}まで、" +
        "${lanes.size}行" + if (warningCount > 0) "、気をつける所${warningCount}件" else ""

    BoxWithConstraints(modifier = Modifier.fillMaxSize().semantics { contentDescription = description }) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val rulerPx = with(density) { 22.dp.toPx() }
        val lanePx = with(density) { 24.dp.toPx() }
        // 1日の幅は、全体が収まるように10〜24dpの間で決める
        val dayPx = (widthPx / totalDays).coerceIn(with(density) { 10.dp.toPx() }, with(density) { 24.dp.toPx() })
        val contentWidth = dayPx * totalDays
        val contentHeight = rulerPx + lanePx * lanes.size + lanePx / 2
        val maxPanX = (contentWidth - widthPx).coerceAtLeast(0f)
        val maxPanY = (contentHeight - heightPx).coerceAtLeast(0f)
        var panX by remember { mutableFloatStateOf(-1f) }
        var panY by remember { mutableFloatStateOf(0f) }
        if (panX < 0f) {
            // 最初は今日が左から1/3あたりに来るようにする
            panX = (ChronoUnit.DAYS.between(minDate, today) * dayPx - widthPx / 3).coerceIn(0f, maxPanX)
        }
        panX = panX.coerceIn(0f, maxPanX)
        panY = panY.coerceIn(0f, maxPanY)
        var drag by remember { mutableStateOf<GanttDrag?>(null) }

        fun xOf(date: LocalDate): Float = ChronoUnit.DAYS.between(minDate, date) * dayPx - panX
        fun laneAt(y: Float): Int = ((y - rulerPx + panY) / lanePx).toInt()
        fun previewOf(lane: GanttLane, d: GanttDrag?): Pair<LocalDate, LocalDate> {
            if (d == null || currentLanes.getOrNull(d.laneIndex)?.row?.id != lane.row.id) return lane.start to lane.end
            return when (d.mode) {
                DragMode.Move -> lane.start.plusDays(d.deltaDays) to lane.end.plusDays(d.deltaDays)
                DragMode.ResizeStart -> minOf(lane.start.plusDays(d.deltaDays), lane.end) to lane.end
                DragMode.ResizeEnd -> lane.start to maxOf(lane.end.plusDays(d.deltaDays), lane.start)
                DragMode.Pan -> lane.start to lane.end
            }
        }
        fun hit(position: Offset): Pair<Int, DragMode>? {
            if (position.y < rulerPx) return null
            val index = laneAt(position.y)
            val lane = currentLanes.getOrNull(index) ?: return null
            val edge = with(density) { 14.dp.toPx() }
            return if (lane.row.level == PlanLevel.What) {
                val cx = xOf(lane.start) + dayPx / 2
                if (kotlin.math.abs(position.x - cx) < with(density) { 18.dp.toPx() }) index to DragMode.Move else null
            } else {
                val left = xOf(lane.start)
                val right = xOf(lane.end) + dayPx
                when {
                    kotlin.math.abs(position.x - left) < edge -> index to DragMode.ResizeStart
                    kotlin.math.abs(position.x - right) < edge -> index to DragMode.ResizeEnd
                    position.x in left..right -> index to DragMode.Move
                    else -> null
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(minDate, totalDays, dayPx, lanes.size, maxWidth) {
                    detectTapGestures { position ->
                        val target = hit(position)?.first ?: laneAt(position.y).takeIf { position.y >= rulerPx }
                        currentLanes.getOrNull(target ?: -1)?.let { currentOnDate(it.row.id) }
                    }
                }
                .pointerInput(minDate, totalDays, dayPx, lanes.size, maxWidth) {
                    detectDragGestures(
                        onDragStart = { position ->
                            val target = hit(position)
                            drag = if (target == null || currentLanes[target.first].row.existingId != null) {
                                GanttDrag(-1, DragMode.Pan)
                            } else {
                                GanttDrag(target.first, target.second)
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val d = drag ?: return@detectDragGestures
                            if (d.mode == DragMode.Pan) {
                                panX = (panX - amount.x).coerceIn(0f, maxPanX)
                                panY = (panY - amount.y).coerceIn(0f, maxPanY)
                            } else {
                                val accumulated = d.accumulated + amount.x
                                val days = (accumulated / dayPx).roundToInt().toLong()
                                if (days != d.deltaDays) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                drag = d.copy(accumulated = accumulated, deltaDays = days)
                            }
                        },
                        onDragEnd = {
                            val d = drag
                            drag = null
                            val lane = d?.let { currentLanes.getOrNull(it.laneIndex) } ?: return@detectDragGestures
                            if (d.deltaDays == 0L) return@detectDragGestures
                            val (start, end) = previewOf(lane, d)
                            val before = lane.row
                            currentOnDrag(
                                before,
                                before.copy(
                                    start = start,
                                    end = if (before.level == PlanLevel.What) null else end,
                                    autoTokens = before.autoTokens - "when"
                                )
                            )
                        },
                        onDragCancel = { drag = null }
                    )
                }
        ) {
            val d = drag
            // 週の区切り(月曜)と月の見出し
            var day = minDate
            while (!day.isAfter(maxDate)) {
                val x = xOf(day)
                if (day.dayOfWeek == DayOfWeek.MONDAY) {
                    drawLine(colors.outlineVariant, Offset(x, rulerPx), Offset(x, size.height), 1f)
                }
                if (day.dayOfMonth == 1 || day == minDate) {
                    drawText(measurer, "${day.monthValue}月", Offset(x + 3f, 3f), rulerStyle)
                }
                day = day.plusDays(1)
            }
            // 何もない日が続く所(同じ大目的の中日程のすき間)
            gaps.forEach { gap ->
                val top = rulerPx + gap.laneIndex * lanePx - panY
                val left = xOf(gap.from)
                val width = xOf(gap.to) + dayPx - left
                drawRect(colors.errorContainer, Offset(left, top + 2f), Size(width, lanePx - 4f))
                drawText(
                    measurer, "${gap.days}日空き", Offset(left + 3f, top + 4f),
                    labelStyle.copy(color = colors.onErrorContainer, fontSize = 10.sp),
                    overflow = TextOverflow.Clip, maxLines = 1,
                    size = Size(width.coerceAtLeast(1f), lanePx)
                )
            }
            lanes.forEachIndexed { index, lane ->
                val top = rulerPx + index * lanePx - panY
                val centerY = top + lanePx / 2
                if (lane.row.id == editingId) {
                    drawRect(colors.primary.copy(alpha = 0.08f), Offset(0f, top), Size(size.width, lanePx))
                }
                val (start, end) = previewOf(lane, d)
                val color = laneColors.getValue(lane.row.level)
                val alpha = if (lane.warning == "過去") 0.4f else 1f
                val left = xOf(start)
                val right = xOf(end) + dayPx
                val title = lane.row.title.ifBlank { lane.row.level.label }
                when (lane.row.level) {
                    PlanLevel.Why -> {
                        val h = 6.dp.toPx()
                        drawRoundRect(color.copy(alpha = alpha), Offset(left, centerY - h / 2), Size(right - left, h), CornerRadius(h / 2))
                        drawText(measurer, title, Offset(left + 2f, top), labelStyle.copy(fontSize = 9.sp, color = color), maxLines = 1)
                    }
                    PlanLevel.How -> {
                        val h = 16.dp.toPx()
                        val corner = CornerRadius(4.dp.toPx())
                        drawRoundRect(color.copy(alpha = 0.3f * alpha), Offset(left, centerY - h / 2), Size(right - left, h), corner)
                        drawRoundRect(color.copy(alpha = alpha), Offset(left, centerY - h / 2), Size(right - left, h), corner, style = Stroke(1.5.dp.toPx()))
                        drawText(
                            measurer, title, Offset(left + 4f, centerY - h / 2 + 1f), labelStyle,
                            overflow = TextOverflow.Ellipsis, maxLines = 1,
                            size = Size((right - left - 6f).coerceAtLeast(1f), h)
                        )
                    }
                    PlanLevel.What -> {
                        val cx = left + dayPx / 2
                        val dot = if (lane.warning == "中日程の期間外") colors.error else color.copy(alpha = alpha)
                        drawCircle(dot, 5.dp.toPx(), Offset(cx, centerY))
                        val label = if (lane.warning == "中日程の期間外") "$title(期間外)" else title
                        drawText(
                            measurer, label, Offset(cx + 8.dp.toPx(), centerY - 8.dp.toPx()),
                            labelStyle.copy(color = if (lane.warning == "中日程の期間外") colors.error else colors.onSurface.copy(alpha = alpha)),
                            maxLines = 1
                        )
                    }
                }
                // 動かしている帯の上に、決まる日付を出す
                if (d != null && d.laneIndex == index && d.mode != DragMode.Pan) {
                    val text = if (lane.row.level == PlanLevel.What) start.format(shortMonthDay) else "${start.format(shortMonthDay)}〜${end.format(shortMonthDay)}"
                    val layout = measurer.measure(text, labelStyle.copy(color = colors.inverseOnSurface, fontWeight = FontWeight.Bold))
                    val bubbleLeft = left.coerceIn(0f, (size.width - layout.size.width - 8f).coerceAtLeast(0f))
                    val bubbleTop = (top - layout.size.height - 6f).coerceAtLeast(rulerPx)
                    drawRoundRect(
                        colors.inverseSurface, Offset(bubbleLeft, bubbleTop),
                        Size(layout.size.width + 8f, layout.size.height + 4f), CornerRadius(6f)
                    )
                    drawText(layout, topLeft = Offset(bubbleLeft + 4f, bubbleTop + 2f))
                }
            }
            // 今日の線(点線)
            val tx = xOf(today) + dayPx / 2
            drawLine(
                todayColor, Offset(tx, rulerPx), Offset(tx, size.height), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
            // 目盛りの背景は最後に重ね、上へ流れた帯を隠す
            drawRect(colors.surfaceContainerLow, Offset.Zero, Size(size.width, rulerPx))
            day = minDate
            while (!day.isAfter(maxDate)) {
                if (day.dayOfMonth == 1 || day == minDate) {
                    drawText(measurer, "${day.monthValue}月", Offset(xOf(day) + 3f, 3f), rulerStyle)
                } else if (day.dayOfWeek == DayOfWeek.MONDAY && dayPx >= 12.dp.toPx()) {
                    drawText(measurer, "${day.dayOfMonth}", Offset(xOf(day) + 2f, 5f), rulerStyle.copy(fontWeight = FontWeight.Normal))
                }
                day = day.plusDays(1)
            }
            drawText(measurer, "今日", Offset(tx - 10.dp.toPx(), rulerPx - 12.dp.toPx()), rulerStyle.copy(color = todayColor, fontSize = 9.sp))
        }
    }
}

// ---- A4 分担ヒートマップ ----

@Composable
private fun ShareHeatmap(rows: List<PlanRow>, onRowTap: (String) -> Unit) {
    val controller = LocalTagController.current
    val isDark = LocalIsDarkTheme.current
    val schedules = rows.filter { it.level == PlanLevel.What && it.start != null }
    if (schedules.none { it.tagIds.isNotEmpty() }) {
        EmptyVisual("誰が？を付けると、週ごとの負担が見えます")
        return
    }
    fun weekOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = schedules.map { weekOf(it.start!!) }.distinct().sorted()
    val tags = controller?.tags.orEmpty().filter { tag -> schedules.any { tag.id in it.tagIds } }
    data class HeatRow(val label: String, val color: Color, val matches: (PlanRow) -> Boolean)
    val heatRows = tags.map { tag ->
        HeatRow(tag.name, tagColor(tag.colorIndex, isDark)) { row -> tag.id in row.tagIds }
    } + listOfNotNull(
        HeatRow("未定", MaterialTheme.colorScheme.outline) { row -> row.tagIds.isEmpty() }
            .takeIf { schedules.any { it.tagIds.isEmpty() } }
    )
    val cell = 30.dp
    Row(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        Column {
            Spacer(Modifier.height(20.dp))
            heatRows.forEach { heat ->
                Row(modifier = Modifier.height(cell).width(76.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(heat.color, RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text(heat.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            weeks.forEach { week ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${week.format(shortMonthDay)}週",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(20.dp).padding(horizontal = 2.dp)
                    )
                    heatRows.forEach { heat ->
                        val matched = schedules.filter { heat.matches(it) && weekOf(it.start!!) == week }
                        val count = matched.size
                        val alpha = when (count) {
                            0 -> 0f
                            1 -> 0.25f
                            2 -> 0.5f
                            else -> 0.85f
                        }
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(cell - 4.dp)
                                .background(heat.color.copy(alpha = alpha), RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                .clickable(enabled = count > 0, onClickLabel = "その予定へ移動") { matched.firstOrNull()?.let { onRowTap(it.id) } }
                                .semantics { contentDescription = "${heat.label}、${week.format(shortMonthDay)}の週、${count}件" },
                            contentAlignment = Alignment.Center
                        ) {
                            if (count > 0) {
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (count >= 3) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
