package com.example.schedulelink.ui.flow

import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalType
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.ScheduleEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

// タイムライン(全体マップ)の配置。大目的ごとの横の帯(スイムレーン)に、中日程を期間の長さの
// バー(ガントチャート)で、小日程をそのバーの下の点で並べる。所属は「帯に入っている」
// 「バーの直下にある」ことで表すので、親子をつなぐ線は引かない(長い線や交差が出ない)。
// 単位: 横は日数、縦はdp(帯の中の位置)。画面に依存しない純粋な計算。

/** 拡大率の段階。段階ごとに、点の横に出す文字の量を変える。 */
enum class ZoomTier { OVERVIEW, NORMAL, DETAIL, DAY }

/** 1日の画面上の幅(dp)から段階を決める。 */
fun zoomTierOf(dayDp: Float): ZoomTier = when {
    dayDp < 3f -> ZoomTier.OVERVIEW
    dayDp < 10f -> ZoomTier.NORMAL
    dayDp < 24f -> ZoomTier.DETAIL
    else -> ZoomTier.DAY
}

/** 帯の見出しの高さ。 */
const val SWIM_HEADER_DP = 34f
/** 中日程のバーの高さ。 */
const val SWIM_BAR_DP = 24f
/** 小日程の点(と名前)の1行の高さ。 */
const val SWIM_CHIP_ROW_DP = 24f
/** 小日程の名前の最大の幅(これより長い名前は「…」で切る)。 */
const val SWIM_CHIP_LABEL_MAX_DP = 170f
/** 1つの中日程の下に並べる小日程の行の上限。超えた分は点だけにして最後の行に置く。 */
private const val MAX_CHIP_ROWS = 4
/** 中日程のまとまりどうしの縦の間隔。 */
const val SWIM_GROUP_GAP_DP = 8f
/** 帯の下の余白。 */
const val SWIM_BAND_PAD_DP = 6f

enum class SwimBandKind { GOAL, UNASSIGNED }

/** 小日程1件。[row]はバーの下の何行目か。 */
class SwimChip(
    val schedule: ScheduleEntity,
    val row: Int,
    val label: String,
    val past: Boolean,
    /** 点・名前・タグの点を合わせた幅の見積もり(dp)。当たり判定にも使う。 */
    val widthDp: Float
)

/** 中日程のバー1本と、その下の小日程。[track]は帯の中の何段目か。 */
class SwimBar(
    val milestone: MilestoneEntity,
    val start: LocalDate,
    /** 終了日(この日を含む)。 */
    val end: LocalDate,
    /** 日付が未設定で、配下の小日程などから推定した期間か。 */
    val estimated: Boolean,
    val status: NodeStatus,
    val track: Int,
    val chips: List<SwimChip>,
    val chipRows: Int
)

class SwimBand(
    val id: String,
    val kind: SwimBandKind,
    val goal: GoalEntity?,
    val title: String,
    val subtitle: String,
    /** 帯の色(接続線用のパレットの番号)。 */
    val colorSlot: Int,
    val status: NodeStatus,
    /** 大目的の期間(帯の地を薄く塗る範囲)。習慣型・期間を出せないときはnull。 */
    val spanStart: LocalDate?,
    val spanEnd: LocalDate?,
    val spanEstimated: Boolean,
    val bars: List<SwimBar>,
    /** 中日程に属さない小日程(「中日程なしの予定」の帯で使う)。 */
    val looseChips: List<SwimChip>,
    val looseRows: Int,
    /** 各段の上端(見出しの下からのdp)。 */
    val trackTops: List<Float>,
    /** 帯全体の上端(タイムライン全体の上からのdp)と高さ。 */
    val top: Float,
    val height: Float
)

class SwimLink(val fromId: String, val toId: String)

class SwimLayout(
    val bands: List<SwimBand>,
    val links: List<SwimLink>,
    val minDate: LocalDate,
    val maxDate: LocalDate,
    val totalHeight: Float,
    val tier: ZoomTier,
    /** 文字の大きさの設定に合わせた、見出し・バー・点の行の高さ(dp)。 */
    val headerDp: Float,
    val barDp: Float,
    val rowDp: Float
) {
    val isEmpty: Boolean get() = bands.isEmpty()
}

/** 文字の幅のおおよその見積もり(dp)。日本語は1文字12dp前後、英数字は半分程度。 */
internal fun estimateTextWidthDp(text: String, charDp: Float = 11.5f): Float =
    text.sumOf { c -> if (c.code < 0x2E80) (charDp * 0.55f).toDouble() else charDp.toDouble() }.toFloat()

private val chipDateFormat = java.time.format.DateTimeFormatter.ofPattern("M/d")
private val chipTimeFormat = java.time.format.DateTimeFormatter.ofPattern("H:mm")

/** 段階に応じた、小日程の点の横に出す文字。 */
internal fun chipLabelOf(schedule: ScheduleEntity, tier: ZoomTier): String = when (tier) {
    ZoomTier.OVERVIEW -> ""
    ZoomTier.NORMAL -> schedule.title
    ZoomTier.DETAIL -> "${schedule.title} ${schedule.date.format(chipDateFormat)}"
    ZoomTier.DAY -> "${schedule.title} ${schedule.startTime.format(chipTimeFormat)}"
}

/** 点(8dp)+間隔+文字の幅+タグの点の幅(dp)。[textScale]は文字の大きさの設定。 */
internal fun chipWidthDp(label: String, tagCount: Int, textScale: Float): Float {
    if (label.isEmpty()) return 10f
    val text = min(estimateTextWidthDp(label, 11f) * textScale, SWIM_CHIP_LABEL_MAX_DP * textScale)
    val tags = if (tagCount > 0) 4f + min(tagCount, 3) * 10f else 0f
    return 14f + text + tags
}

/**
 * 区間の貪欲な割り当て(左から順に、空いている一番上の段へ入れる)。区間グラフなので段数は最少になる。
 * [intervals]は(開始, 終了)の日数(終了は含まない)。戻り値は各区間の段番号。
 */
private fun assignRows(intervals: List<Pair<Double, Double>>): IntArray {
    val order = intervals.indices.sortedBy { intervals[it].first }
    val rowEnds = mutableListOf<Double>()
    val result = IntArray(intervals.size)
    for (i in order) {
        val (s, e) = intervals[i]
        var row = rowEnds.indexOfFirst { it <= s }
        if (row == -1) {
            row = rowEnds.size
            rowEnds.add(e)
        } else {
            rowEnds[row] = e
        }
        result[i] = row
    }
    return result
}

/**
 * 帯を組み立てる。[dayDp]は1日の画面上の幅(文字が隣と重ならないよう、段の割り当てに使う。
 * ズーム中に頻繁に組み替えないよう、呼び出し側で段階的な値にしておく)。
 */
fun buildSwimlanes(
    goals: List<GoalEntity>,
    milestones: List<MilestoneEntity>,
    schedules: List<ScheduleEntity>,
    dayDp: Float,
    textScale: Float = 1f,
    today: LocalDate = LocalDate.now()
): SwimLayout {
    val tier = zoomTierOf(dayDp)
    val headerDp = SWIM_HEADER_DP * textScale
    val barDp = SWIM_BAR_DP * textScale
    val rowDp = SWIM_CHIP_ROW_DP * textScale
    val goalIds = goals.map { it.id }.toSet()
    val knownMilestones = milestones.filter { it.goalId in goalIds }
    val knownMilestoneIds = knownMilestones.map { it.id }.toSet()
    val schedulesByMilestone = schedules.filter { it.milestoneId != null && it.milestoneId in knownMilestoneIds }
        .groupBy { it.milestoneId!! }
    val loose = schedules.filter { it.milestoneId == null || it.milestoneId !in knownMilestoneIds }
    val milestonesByGoal = knownMilestones.groupBy { it.goalId }
    val scheduleOrder = compareBy<ScheduleEntity>({ it.date }, { it.startTime }, { it.id })

    fun daysOf(date: LocalDate): Double = date.toEpochDay().toDouble()
    fun widthDays(widthDp: Float): Double = (widthDp / dayDp).toDouble()

    /** 小日程の並びに、文字が重ならないよう行を割り当てる。 */
    fun layoutChips(list: List<ScheduleEntity>): Pair<List<SwimChip>, Int> {
        if (list.isEmpty()) return emptyList<SwimChip>() to 0
        val sorted = list.sortedWith(scheduleOrder)
        val labels = sorted.map { chipLabelOf(it, tier) }
        val widths = sorted.mapIndexed { i, s -> chipWidthDp(labels[i], s.tagIds.size, textScale) }
        // 俯瞰(名前なし)のときは1行に点を並べるだけにする。
        if (tier == ZoomTier.OVERVIEW) {
            return sorted.mapIndexed { i, s -> SwimChip(s, 0, "", s.date.isBefore(today), widths[i]) } to 1
        }
        val intervals = sorted.mapIndexed { i, s ->
            // 点は日付の枠の中央に置く。区間はその点の左端から文字の右端まで(+少しの余白)。
            val center = daysOf(s.date) + 0.5
            val left = center - widthDays(5f)
            left to left + widthDays(widths[i] + 8f)
        }
        val rows = assignRows(intervals)
        // 行が多くなりすぎる(毎日の習慣など)ときは、上限を超えた分を点だけにして最後の行に置く。
        val chips = sorted.mapIndexed { i, s ->
            if (rows[i] >= MAX_CHIP_ROWS) {
                SwimChip(s, MAX_CHIP_ROWS - 1, "", s.date.isBefore(today), 10f)
            } else {
                SwimChip(s, rows[i], labels[i], s.date.isBefore(today), widths[i])
            }
        }
        return chips to min((rows.maxOrNull() ?: -1) + 1, MAX_CHIP_ROWS)
    }

    /** 中日程の期間。日付がなければ配下の小日程から推定する。 */
    fun spanOf(m: MilestoneEntity, fallback: LocalDate): Triple<LocalDate, LocalDate, Boolean> {
        val childDates = schedulesByMilestone[m.id].orEmpty().map { it.date }
        val s = m.startDate
        val e = m.endDate
        return when {
            s != null && e != null -> Triple(minOf(s, e), maxOf(s, e), false)
            s != null -> Triple(s, maxOf(s, childDates.maxOrNull() ?: s), true)
            e != null -> Triple(minOf(e, childDates.minOrNull() ?: e), e, true)
            childDates.isNotEmpty() -> Triple(childDates.min(), childDates.max(), true)
            else -> Triple(fallback, fallback, true)
        }
    }

    class GoalDraft(val goal: GoalEntity, val status: NodeStatus, val bars: List<SwimBar>, val trackHeights: List<Float>)

    val drafts = goals.map { goal ->
        val fallback = goal.startDate ?: today
        val goalMilestones = milestonesByGoal[goal.id].orEmpty().sortedWith(compareBy({ it.orderIndex }, { it.id }))
        // まず各中日程のまとまり(バー+点)の横の範囲を出し、重ならないものは同じ段に詰める。
        class Group(val m: MilestoneEntity, val start: LocalDate, val end: LocalDate, val est: Boolean, val chips: List<SwimChip>, val rows: Int)
        val groups = goalMilestones.map { m ->
            val (s, e, est) = spanOf(m, fallback)
            val (chips, rows) = layoutChips(schedulesByMilestone[m.id].orEmpty())
            Group(m, s, e, est, chips, rows)
        }
        val intervals = groups.map { g ->
            val barStart = daysOf(g.start)
            val barEnd = daysOf(g.end) + 1
            // バーに名前が入りきらないときは右の外に出すので、その分も範囲に含める。
            val nameDays = widthDays(estimateTextWidthDp(g.m.title, 12f) * textScale + 16f)
            val nameEnd = if (nameDays > barEnd - barStart) barEnd + nameDays else barEnd
            val chipEnd = g.chips.maxOfOrNull { c ->
                daysOf(c.schedule.date) + 0.5 - widthDays(5f) + widthDays(c.widthDp + 8f)
            } ?: barEnd
            val chipStart = g.chips.minOfOrNull { c -> daysOf(c.schedule.date) + 0.5 - widthDays(5f) } ?: barStart
            min(barStart, chipStart) to max(max(barEnd, nameEnd), chipEnd) + widthDays(12f)
        }
        val tracks = assignRows(intervals)
        val trackCount = (tracks.maxOrNull() ?: -1) + 1
        val trackHeights = MutableList(trackCount) { 0f }
        groups.forEachIndexed { i, g ->
            val h = barDp + g.rows * rowDp + SWIM_GROUP_GAP_DP
            trackHeights[tracks[i]] = max(trackHeights[tracks[i]], h)
        }
        val bars = groups.mapIndexed { i, g ->
            SwimBar(g.m, g.start, g.end, g.est, g.m.status.toNodeStatus(), tracks[i], g.chips, g.rows)
        }
        val status = goalNodeStatus(goal, goalMilestones.map { it.status.toNodeStatus() }, today)
        GoalDraft(goal, status, bars, trackHeights)
    }

    // 並び順: 進行中(終わりが近い順)→これから(始まりの早い順)→習慣型→済んだもの。
    fun sortKey(d: GoalDraft): Int = when {
        d.status == NodeStatus.DONE -> 3
        d.goal.type == GoalType.HABIT -> 2
        d.status == NodeStatus.ACTIVE -> 0
        else -> 1
    }
    val ordered = drafts.sortedWith(
        compareBy<GoalDraft>({ sortKey(it) }, {
            when (sortKey(it)) {
                0 -> it.goal.endDate ?: it.bars.maxOfOrNull { b -> b.end } ?: LocalDate.MAX
                else -> it.goal.startDate ?: it.bars.minOfOrNull { b -> b.start } ?: LocalDate.MAX
            }
        }, { it.goal.title })
    )

    val bands = mutableListOf<SwimBand>()
    var y = 0f
    ordered.forEachIndexed { index, d ->
        val goal = d.goal
        val trackTops = mutableListOf<Float>()
        var t = 0f
        d.trackHeights.forEach { h -> trackTops += t; t += h }
        val height = headerDp + t + SWIM_BAND_PAD_DP
        val habit = goal.type == GoalType.HABIT
        val childStart = d.bars.minOfOrNull { it.start }
        val childEnd = d.bars.maxOfOrNull { it.end }
        val spanStart = if (habit) null else goal.startDate ?: childStart
        val spanEnd = if (habit) null else goal.endDate ?: childEnd ?: spanStart
        val subtitle = when {
            habit -> "継続する習慣"
            goal.startDate != null || goal.endDate != null -> formatRangeSubtitle(goal.startDate, goal.endDate)
            else -> ""
        }
        bands += SwimBand(
            id = goal.id,
            kind = SwimBandKind.GOAL,
            goal = goal,
            title = goal.title,
            subtitle = subtitle,
            colorSlot = (index * 3) % 8,
            status = d.status,
            spanStart = spanStart,
            spanEnd = if (spanStart != null && spanEnd != null && spanEnd < spanStart) spanStart else spanEnd,
            spanEstimated = goal.startDate == null || goal.endDate == null,
            bars = d.bars,
            looseChips = emptyList(),
            looseRows = 0,
            trackTops = trackTops,
            top = y,
            height = height
        )
        y += height
    }

    if (loose.isNotEmpty()) {
        val (chips, rows) = layoutChips(loose)
        val height = headerDp + rows * rowDp + SWIM_GROUP_GAP_DP + SWIM_BAND_PAD_DP
        bands += SwimBand(
            id = "__unassigned__",
            kind = SwimBandKind.UNASSIGNED,
            goal = null,
            title = "中日程なしの予定",
            subtitle = "${loose.size}件",
            colorSlot = -1,
            status = NodeStatus.UPCOMING,
            spanStart = null,
            spanEnd = null,
            spanEstimated = true,
            bars = emptyList(),
            looseChips = chips,
            looseRows = rows,
            trackTops = emptyList(),
            top = y,
            height = height
        )
        y += height
    }

    val allDates = buildList {
        // 今日も範囲に含める(予定がすべて先・すべて過去でも、最初の画面に目盛りと今日が出るように)。
        add(today)
        bands.forEach { b ->
            b.spanStart?.let { add(it) }
            b.spanEnd?.let { add(it) }
            b.bars.forEach { bar -> add(bar.start); add(bar.end); bar.chips.forEach { add(it.schedule.date) } }
            b.looseChips.forEach { add(it.schedule.date) }
        }
    }
    // 前後にゆとりを持たせ、端まで動かしても目盛りが途切れないようにする。
    val minDate = (allDates.minOrNull() ?: today).minusDays(21)
    val maxDate = (allDates.maxOrNull() ?: today).plusDays(45)

    val present = schedules.map { it.id }.toSet()
    val seen = HashSet<String>()
    val links = buildList {
        schedules.forEach { s ->
            s.linkedIds.forEach { other ->
                if (other in present && other != s.id) {
                    val key = if (s.id < other) "${s.id}|$other" else "$other|${s.id}"
                    if (seen.add(key)) add(SwimLink(s.id, other))
                }
            }
        }
    }
    return SwimLayout(bands, links, minDate, maxDate, y, tier, headerDp, barDp, rowDp)
}

/** 段の割り当てに使う1日の幅を段階的な値にする(ズーム中に何度も組み替わって揺れないように)。 */
fun quantizeDayDp(dayDp: Float): Float {
    // 小さい側に丸める(文字の幅を日数で多めに見積もり、重なりを避ける側に倒す)。
    val steps = kotlin.math.floor(kotlin.math.ln(dayDp.toDouble()) / kotlin.math.ln(1.5)).toInt()
    return Math.pow(1.5, steps.toDouble()).toFloat()
}

/** 期間の日数(両端含む)。 */
internal fun spanDays(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end) + 1
