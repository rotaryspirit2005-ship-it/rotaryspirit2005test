package com.example.schedulelink.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.tag.LocalTagController
import com.example.schedulelink.ui.tag.matchesTagFilter
import com.example.schedulelink.ui.theme.DarkOnError
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.PeerLinkColorDark
import com.example.schedulelink.ui.theme.PeerLinkColorLight
import com.example.schedulelink.ui.theme.SaturdayColorDark
import com.example.schedulelink.ui.theme.SaturdayColorLight
import com.example.schedulelink.ui.theme.SundayColorDark
import com.example.schedulelink.ui.theme.SundayColorLight
import com.example.schedulelink.ui.theme.TodayLineColorDark
import com.example.schedulelink.ui.theme.TodayLineColorLight
import com.example.schedulelink.ui.theme.connectorColor
import com.example.schedulelink.ui.theme.tagColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// タイムライン(全体マップ)の画面。大目的ごとの帯に中日程のバーと小日程の点を並べる(配置は
// SwimlaneLayout.kt)。ズームは横(時間)方向だけで、文字の大きさは変わらない。縦は普通に
// スクロールする。すべて1枚のCanvasに画面の座標で描き、タップは同じ計算で当たり判定する。

/** 1日の幅(dp)の範囲。 */
private const val MIN_DAY_DP = 1.5f
private const val MAX_DAY_DP = 64f

/** 絞り込みに合わない・選んだ項目と関係のない要素の濃さ。 */
private const val DIMMED_ALPHA = 0.28f

/** 画面の座標と、帯・バー・点の位置の対応。描画とタップの当たり判定で同じ計算を使う。 */
private class SwimGeometry(
    val layout: SwimLayout,
    scale: Float,
    offset: Offset,
    val density: Float,
    rulerPx: Float
) {
    val dayPx = PX_PER_DAY * density * scale
    private val originX = offset.x + MARGIN * density * scale
    private val top = rulerPx + 8f * density + offset.y

    fun xOf(date: LocalDate): Float = originX + ChronoUnit.DAYS.between(layout.minDate, date) * dayPx
    fun yOf(dp: Float): Float = top + dp * density
    fun px(dp: Float): Float = dp * density

    fun headerRect(band: SwimBand, width: Float) = Rect(0f, yOf(band.top), width, yOf(band.top + layout.headerDp))
    fun bandRect(band: SwimBand, width: Float) = Rect(0f, yOf(band.top), width, yOf(band.top + band.height))

    fun barTopDp(band: SwimBand, bar: SwimBar): Float = band.top + layout.headerDp + band.trackTops[bar.track]

    fun barRect(band: SwimBand, bar: SwimBar): Rect {
        val t = yOf(barTopDp(band, bar))
        return Rect(xOf(bar.start), t, xOf(bar.end.plusDays(1)), t + px(layout.barDp))
    }

    fun chipCenter(band: SwimBand, bar: SwimBar?, chip: SwimChip): Offset {
        val rowTopDp = if (bar != null) {
            barTopDp(band, bar) + layout.barDp
        } else {
            band.top + layout.headerDp
        } + chip.row * layout.rowDp
        return Offset(xOf(chip.schedule.date) + dayPx / 2f, yOf(rowTopDp + layout.rowDp / 2f))
    }

    fun chipRect(center: Offset, chip: SwimChip): Rect = Rect(
        center.x - px(8f),
        center.y - px(layout.rowDp / 2f),
        center.x + px(chip.widthDp),
        center.y + px(layout.rowDp / 2f)
    )
}

/** タップした位置にあるもの。 */
private sealed interface SwimHit {
    data class Goal(val id: String) : SwimHit
    data class Milestone(val id: String) : SwimHit
    data class Schedule(val id: String) : SwimHit
}

private fun hitTest(geometry: SwimGeometry, width: Float, position: Offset): SwimHit? {
    val layout = geometry.layout
    // 点(小日程)→バー(中日程)→見出し(大目的)の順に、上に描いているものから調べる。
    for (band in layout.bands) {
        if (!geometry.bandRect(band, width).contains(position)) continue
        for (bar in band.bars) {
            for (chip in bar.chips) {
                if (geometry.chipRect(geometry.chipCenter(band, bar, chip), chip).contains(position)) {
                    return SwimHit.Schedule(chip.schedule.id)
                }
            }
        }
        for (chip in band.looseChips) {
            if (geometry.chipRect(geometry.chipCenter(band, null, chip), chip).contains(position)) {
                return SwimHit.Schedule(chip.schedule.id)
            }
        }
        for (bar in band.bars) {
            val r = geometry.barRect(band, bar)
            // 細い・短いバーでも押しやすいよう、上下と左右を少し広げて判定する。
            val hit = Rect(r.left - geometry.px(6f), r.top - geometry.px(4f), max(r.right, r.left + geometry.px(24f)), r.bottom + geometry.px(4f))
            if (hit.contains(position)) return SwimHit.Milestone(bar.milestone.id)
        }
        if (band.goal != null) {
            // 見出しは名前のあたりだけを押せるようにする(右側の空いたところでは開かない)。
            val header = geometry.headerRect(band, width)
            val textScale = geometry.layout.headerDp / SWIM_HEADER_DP
            val right = min(width, geometry.px(40f + estimateTextWidthDp(band.title, 15f) * textScale + 120f))
            if (Rect(header.left, header.top, right, header.bottom).contains(position)) return SwimHit.Goal(band.goal.id)
        }
    }
    return null
}

@Composable
fun SwimlaneTimeline(
    goals: List<GoalEntity>,
    milestones: List<MilestoneEntity>,
    schedules: List<ScheduleEntity>,
    tagFilter: Set<String>,
    knownTagIds: Set<String>,
    onGoalClick: (String) -> Unit,
    onMilestoneClick: (String) -> Unit,
    onScheduleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // scale = 1日の幅 / PX_PER_DAY、offset.x = 横の移動量、offset.y = 縦のスクロール量(0以下)。
    val zp = remember { ZoomPanState() }
    val today = remember { LocalDate.now() }
    // 段の割り当てはズームの段階が変わったときだけ組み直す(連続したズームでは組み替えない)。
    val layoutDayDp by remember { derivedStateOf { quantizeDayDp(PX_PER_DAY * zp.scale) } }
    // 文字を大きくする設定のときは、名前の幅や行の高さもその分広げる。
    val textScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    val layout = remember(goals, milestones, schedules, layoutDayDp, textScale) {
        buildSwimlanes(goals, milestones, schedules, layoutDayDp, textScale, today)
    }
    val currentOnGoalClick by rememberUpdatedState(onGoalClick)
    val currentOnMilestoneClick by rememberUpdatedState(onMilestoneClick)
    val currentOnScheduleClick by rememberUpdatedState(onScheduleClick)
    val currentLayout by rememberUpdatedState(layout)
    val gridModel = remember(layout.minDate, layout.maxDate) {
        GridModel(layout.minDate, layout.maxDate, monthMarks(layout.minDate, layout.maxDate))
    }
    val scheduleById = remember(schedules) { schedules.associateBy { it.id } }
    val milestoneById = remember(milestones) { milestones.associateBy { it.id } }

    var selectedId by remember { mutableStateOf<String?>(null) }
    // 長押しで選んだ項目と関係のあるもの(親・子・リンク先)。それ以外は薄くする。
    val related = remember(selectedId, layout) {
        val id = selectedId ?: return@remember emptySet<String>()
        buildSet {
            add(id)
            val schedule = scheduleById[id]
            if (schedule != null) {
                addAll(schedule.linkedIds)
                schedule.milestoneId?.let { mid -> add(mid); milestoneById[mid]?.let { add(it.goalId) } }
            }
            milestoneById[id]?.let { m ->
                add(m.goalId)
                schedules.filter { it.milestoneId == m.id }.forEach { add(it.id) }
            }
            if (goals.any { it.id == id }) {
                milestones.filter { it.goalId == id }.forEach { m ->
                    add(m.id)
                    schedules.filter { it.milestoneId == m.id }.forEach { add(it.id) }
                }
            }
        }
    }

    val isDark = LocalIsDarkTheme.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val tagController = LocalTagController.current
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
    val headerStyle = remember(typography) { typography.titleSmall.copy(fontWeight = FontWeight.Bold) }
    val subStyle = remember(typography) { typography.labelSmall }
    val barStyle = remember(typography) { typography.labelMedium.copy(fontWeight = FontWeight.SemiBold) }
    val chipStyle = remember(typography) { typography.labelSmall }
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val peerColor = if (isDark) PeerLinkColorDark else PeerLinkColorLight
    val todayInRange = !today.isBefore(layout.minDate) && !today.isAfter(layout.maxDate)

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var initialized by remember { mutableStateOf(false) }
    val localDensity = androidx.compose.ui.platform.LocalDensity.current
    // 最初は今日が左から1/3の位置に来るようにする。
    LaunchedEffect(viewSize, layout.minDate) {
        if (!initialized && viewSize.width > 0) {
            val todayX = with(localDensity) { (MARGIN + ChronoUnit.DAYS.between(layout.minDate, today) * PX_PER_DAY).dp.toPx() }
            zp.offset = Offset(viewSize.width / 3f - todayX * zp.scale, 0f)
            initialized = true
        }
    }

    fun Density.maxScrollPx(): Float {
        val content = timelineRulerHeightPx() + 8.dp.toPx() + currentLayout.totalHeight.dp.toPx() + 96.dp.toPx()
        return max(0f, content - viewSize.height)
    }

    // データが変わって表示範囲の始まり(minDate)が動いたら、見ている日付が横に飛ばないよう補正する。
    var lastMinDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(layout.minDate) {
        val last = lastMinDate
        if (last != null && last != layout.minDate) {
            val shiftDays = ChronoUnit.DAYS.between(layout.minDate, last)
            val dayPx = with(localDensity) { PX_PER_DAY.dp.toPx() } * zp.scale
            zp.offset = Offset(zp.offset.x - shiftDays * dayPx, zp.offset.y)
        }
        lastMinDate = layout.minDate
    }
    // 帯が低くなった(段の組み直し・データの減少・回転)ときは、縦のスクロールを範囲に戻す。
    LaunchedEffect(layout, viewSize) {
        val limit = with(localDensity) { maxScrollPx() }
        if (zp.offset.y < -limit) zp.offset = Offset(zp.offset.x, -limit)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // 拡大縮小は横(時間)方向だけ。指の中心の日付が動かないように位置を合わせる。
                    val old = zp.scale
                    val new = (old * zoom).coerceIn(MIN_DAY_DP / PX_PER_DAY, MAX_DAY_DP / PX_PER_DAY)
                    val x = centroid.x - (centroid.x - zp.offset.x) * (new / old) + pan.x
                    val y = (zp.offset.y + pan.y).coerceIn(-maxScrollPx(), 0f)
                    zp.scale = new
                    zp.offset = Offset(x, y)
                }
            }
            .pointerInput(Unit) {
                fun hitAt(position: Offset): SwimHit? {
                    val geometry = SwimGeometry(currentLayout, zp.scale, zp.offset, density, timelineRulerHeightPx())
                    if (position.y < timelineRulerHeightPx()) return null
                    return hitTest(geometry, size.width.toFloat(), position)
                }
                detectTapGestures(
                    onTap = { position ->
                        when (val hit = hitAt(position)) {
                            is SwimHit.Goal -> currentOnGoalClick(hit.id)
                            is SwimHit.Milestone -> currentOnMilestoneClick(hit.id)
                            is SwimHit.Schedule -> currentOnScheduleClick(hit.id)
                            null -> selectedId = null
                        }
                    },
                    onLongPress = { position ->
                        val id = when (val hit = hitAt(position)) {
                            is SwimHit.Goal -> hit.id
                            is SwimHit.Milestone -> hit.id
                            is SwimHit.Schedule -> hit.id
                            null -> null
                        }
                        selectedId = if (id == null || id == selectedId) null else id
                    }
                )
            }
    ) {
        val width = size.width
        val geometry = SwimGeometry(layout, zp.scale, zp.offset, density, timelineRulerHeightPx())

        drawGridBackdrop(zp, gridModel, gridColors, if (todayInRange) today else null)

        fun alphaOf(id: String, tagIds: List<String>): Float = when {
            selectedId != null && id !in related -> DIMMED_ALPHA
            tagFilter.isNotEmpty() && !matchesTagFilter(tagIds, tagFilter, knownTagIds) -> DIMMED_ALPHA
            else -> 1f
        }

        fun bandColorOf(band: SwimBand): Color =
            if (band.colorSlot >= 0) connectorColor(band.colorSlot, isDark) else colorScheme.primary

        fun measure(text: String, style: TextStyle): TextLayoutResult =
            measurer.measure(text, style, softWrap = false, maxLines = 1)

        fun drawTagDots(tagIds: List<String>, startX: Float, centerY: Float, alpha: Float) {
            val tags = tagController?.resolve(tagIds).orEmpty().take(3)
            var x = startX
            tags.forEach { tag ->
                drawCircle(tagColor(tag.colorIndex, isDark).copy(alpha = alpha), geometry.px(3.5f), Offset(x + geometry.px(3.5f), centerY))
                x += geometry.px(10f)
            }
        }

        // 小日程の点の位置(リンクの線を引くため、画面外のものも求めておく)。
        val chipCenters = HashMap<String, Pair<Int, Offset>>()
        layout.bands.forEachIndexed { index, band ->
            band.bars.forEach { bar -> bar.chips.forEach { chipCenters[it.schedule.id] = index to geometry.chipCenter(band, bar, it) } }
            band.looseChips.forEach { chipCenters[it.schedule.id] = index to geometry.chipCenter(band, null, it) }
        }

        layout.bands.forEachIndexed { index, band ->
            val bandRect = geometry.bandRect(band, width)
            if (bandRect.bottom < 0f || bandRect.top > size.height) return@forEachIndexed
            val color = bandColorOf(band)
            // 見出しは、大目的自身か帯の中のどれかが絞り込みに合えば濃く出す。
            val bandMatchesTags = tagFilter.isEmpty() ||
                matchesTagFilter(band.goal?.tagIds.orEmpty(), tagFilter, knownTagIds) ||
                band.bars.any { bar ->
                    matchesTagFilter(bar.milestone.tagIds, tagFilter, knownTagIds) ||
                        bar.chips.any { matchesTagFilter(it.schedule.tagIds, tagFilter, knownTagIds) }
                } ||
                band.looseChips.any { matchesTagFilter(it.schedule.tagIds, tagFilter, knownTagIds) }
            val bandAlpha = when {
                selectedId != null && band.id !in related -> DIMMED_ALPHA
                !bandMatchesTags -> DIMMED_ALPHA
                else -> 1f
            }

            // 帯の地。1つおきにごく薄く色を敷き、帯の切れ目を分かりやすくする。
            if (index % 2 == 1) {
                drawRect(colorScheme.onSurface.copy(alpha = 0.035f), bandRect.topLeft, bandRect.size)
            }
            // 大目的の期間を、帯の色で薄く塗る(習慣型は帯全体)。
            val bodyTop = geometry.yOf(band.top + layout.headerDp)
            val spanStart = band.spanStart
            val spanEnd = band.spanEnd
            if (band.kind == SwimBandKind.GOAL) {
                if (spanStart != null && spanEnd != null) {
                    val left = geometry.xOf(spanStart)
                    val right = geometry.xOf(spanEnd.plusDays(1))
                    drawRect(
                        color.copy(alpha = (if (band.spanEstimated) 0.06f else 0.12f) * bandAlpha),
                        Offset(left, bandRect.top),
                        Size(right - left, bandRect.bottom - bandRect.top)
                    )
                    // 期間の始まりと終わりに細い縦線。
                    drawLine(color.copy(alpha = 0.5f * bandAlpha), Offset(left, bandRect.top), Offset(left, bandRect.bottom), geometry.px(1f))
                    drawLine(color.copy(alpha = 0.5f * bandAlpha), Offset(right, bandRect.top), Offset(right, bandRect.bottom), geometry.px(1f))
                } else {
                    drawRect(color.copy(alpha = 0.05f * bandAlpha), Offset(0f, bodyTop), Size(width, bandRect.bottom - bodyTop))
                }
            }
            // 帯の下の区切り線。
            drawLine(colorScheme.outlineVariant, Offset(0f, bandRect.bottom), Offset(width, bandRect.bottom), geometry.px(1f))

            // 中日程のバー。
            band.bars.forEach { bar ->
                val r = geometry.barRect(band, bar)
                val visible = r.right > -geometry.px(200f) && r.left < width
                if (!visible) return@forEach
                val alpha = alphaOf(bar.milestone.id, bar.milestone.tagIds)
                val done = bar.status == NodeStatus.DONE
                val barWidth = max(r.width, geometry.px(6f))
                val radius = CornerRadius(geometry.px(6f))
                val fill = when {
                    done -> colorScheme.surfaceContainerHighest
                    bar.estimated -> color.copy(alpha = 0.3f)
                    else -> color
                }
                drawRoundRect(fill.copy(alpha = fill.alpha * alpha), r.topLeft, Size(barWidth, r.height), radius)
                if (bar.estimated) {
                    drawRoundRect(
                        color.copy(alpha = alpha),
                        r.topLeft,
                        Size(barWidth, r.height),
                        radius,
                        style = Stroke(width = geometry.px(1.2f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(geometry.px(4f), geometry.px(3f))))
                    )
                }
                val label = (if (done) "✓ " else "") + bar.milestone.title
                val text = measure(label, barStyle)
                val textW = text.size.width.toFloat()
                val textY = r.top + (r.height - text.size.height) / 2f
                val pad = geometry.px(6f)
                if (textW + pad * 2 <= barWidth) {
                    // バーの中に入る名前は、バーの始まりが画面の左外にあっても左端に寄せて見せる。
                    val x = max(r.left + pad, pad).coerceAtMost(r.left + barWidth - pad - textW)
                    val inkColor = when {
                        done -> colorScheme.onSurfaceVariant
                        bar.estimated -> colorScheme.onSurface
                        // 白と黒のどちらがよく見えるかの境目(輝度0.18前後)で切り替える。
                        color.luminance() > 0.18f -> Color(0xFF1A1C19)
                        else -> Color.White
                    }
                    clipRect(r.left, r.top, r.left + barWidth, r.bottom) {
                        drawText(text, color = inkColor.copy(alpha = alpha), topLeft = Offset(x, textY))
                    }
                } else {
                    drawText(text, color = colorScheme.onSurface.copy(alpha = alpha), topLeft = Offset(r.left + barWidth + pad, textY))
                }
            }

            // 小日程の点と名前。
            fun drawChip(bar: SwimBar?, chip: SwimChip) {
                val center = geometry.chipCenter(band, bar, chip)
                if (center.x < -geometry.px(220f) || center.x > width + geometry.px(10f)) return
                val schedule = chip.schedule
                val alpha = alphaOf(schedule.id, schedule.tagIds)
                val radius = geometry.px(4.5f)
                if (chip.past) {
                    drawCircle(colorScheme.onSurfaceVariant.copy(alpha = alpha), radius, center, style = Stroke(width = geometry.px(1.5f)))
                } else {
                    drawCircle(color.copy(alpha = alpha), radius, center)
                }
                if (schedule.id == selectedId) {
                    drawCircle(colorScheme.onSurface, radius + geometry.px(3f), center, style = Stroke(width = geometry.px(1.5f)))
                }
                if (chip.label.isNotEmpty()) {
                    val text = measurer.measure(
                        chip.label,
                        chipStyle,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = geometry.px(SWIM_CHIP_LABEL_MAX_DP * textScale).toInt())
                    )
                    val textX = center.x + geometry.px(9f)
                    drawText(
                        text,
                        color = (if (chip.past) colorScheme.onSurfaceVariant else colorScheme.onSurface).copy(alpha = alpha),
                        topLeft = Offset(textX, center.y - text.size.height / 2f)
                    )
                    drawTagDots(schedule.tagIds, textX + text.size.width + geometry.px(4f), center.y, alpha)
                }
            }
            band.bars.forEach { bar -> bar.chips.forEach { drawChip(bar, it) } }
            band.looseChips.forEach { drawChip(null, it) }

            // 見出し(大目的の名前)。横に動かしても常に左端に見えるよう、画面の座標で描く。
            val header = geometry.headerRect(band, width)
            val title = measure(band.title, headerStyle)
            val sub = measure(
                listOf(band.subtitle, if (band.status == NodeStatus.DONE && band.kind == SwimBandKind.GOAL) "完了" else "")
                    .filter { it.isNotEmpty() }.joinToString(" · "),
                subStyle
            )
            val dotX = geometry.px(16f)
            val centerY = header.top + header.height / 2f
            val titleX = dotX + geometry.px(10f)
            val subX = titleX + title.size.width + geometry.px(8f)
            val tagsX = subX + (if (sub.size.width > 0) sub.size.width + geometry.px(6f) else 0f)
            val tagCount = tagController?.resolve(band.goal?.tagIds.orEmpty())?.size?.coerceAtMost(3) ?: 0
            val backingRight = tagsX + tagCount * geometry.px(10f) + geometry.px(6f)
            // 下を通る月日の線や期間の塗りに文字が埋もれないよう、見出しの後ろに面を敷く。
            drawRoundRect(
                colorScheme.surface.copy(alpha = 0.9f),
                Offset(geometry.px(8f), header.top + geometry.px(4f)),
                Size(backingRight - geometry.px(8f), header.height - geometry.px(8f)),
                CornerRadius(geometry.px(8f))
            )
            drawCircle(color.copy(alpha = bandAlpha), geometry.px(5f), Offset(dotX, centerY))
            drawText(title, color = colorScheme.onSurface.copy(alpha = bandAlpha), topLeft = Offset(titleX, centerY - title.size.height / 2f))
            drawText(sub, color = colorScheme.onSurfaceVariant.copy(alpha = bandAlpha), topLeft = Offset(subX, centerY - sub.size.height / 2f))
            drawTagDots(band.goal?.tagIds.orEmpty(), tagsX, centerY, bandAlpha)
        }

        // 小日程どうしのリンク。同じ帯の中は薄い破線の曲線で常に、帯をまたぐものは選んだときだけ引く。
        layout.links.forEach { link ->
            val (bandA, a) = chipCenters[link.fromId] ?: return@forEach
            val (bandB, b) = chipCenters[link.toId] ?: return@forEach
            val highlighted = selectedId != null && (link.fromId == selectedId || link.toId == selectedId)
            if (bandA != bandB && !highlighted) return@forEach
            val (p, q) = if (a.x <= b.x) a to b else b to a
            val dx = max(geometry.px(24f), abs(q.x - p.x) / 2f)
            val path = Path().apply {
                moveTo(p.x, p.y)
                cubicTo(p.x + dx, p.y, q.x - dx, q.y, q.x, q.y)
            }
            drawPath(
                path,
                color = peerColor.copy(alpha = if (highlighted) 1f else if (selectedId != null) 0.15f else 0.5f),
                style = Stroke(
                    width = geometry.px(if (highlighted) 2f else 1.2f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(geometry.px(5f), geometry.px(4f)))
                )
            )
        }

        drawGridRuler(zp, gridModel, gridColors, if (todayInRange) today else null, measurer, rulerStyles)
    }
}

