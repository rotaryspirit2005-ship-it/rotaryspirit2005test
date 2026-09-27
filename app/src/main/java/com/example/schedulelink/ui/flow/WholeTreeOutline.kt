package com.example.schedulelink.ui.flow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalType
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneStatus
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.common.AppProgressBar
import com.example.schedulelink.ui.common.EmptyState
import com.example.schedulelink.ui.common.TodayBadge
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.Motion
import com.example.schedulelink.ui.theme.PeerLinkColorDark
import com.example.schedulelink.ui.theme.PeerLinkColorLight
import com.example.schedulelink.ui.theme.TodayLineColorDark
import com.example.schedulelink.ui.theme.TodayLineColorLight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// データ: 大目的 → 中日程 → 小日程 の入れ子。表示用の行への展開は開閉状態に応じて画面側で行う。
// ---------------------------------------------------------------------------

/** 小日程からつながる別の小日程。[isLater]なら「→」、そうでなければ「←」で示す。 */
data class OutlineLink(val schedule: ScheduleEntity, val milestoneTitle: String?, val isLater: Boolean)

data class OutlineSchedule(val schedule: ScheduleEntity, val links: List<OutlineLink>)

data class OutlineMilestone(val milestone: MilestoneEntity, val status: NodeStatus, val schedules: List<OutlineSchedule>)

data class OutlineGoal(
    val goal: GoalEntity,
    val status: NodeStatus,
    val milestones: List<OutlineMilestone>,
    val doneMilestoneCount: Int
)

data class TreeOutline(val goals: List<OutlineGoal>, val unassigned: List<OutlineSchedule>) {
    val isEmpty: Boolean get() = goals.isEmpty() && unassigned.isEmpty()
}

/** アプリ本体(ScheduleFlowComponentsのisLater)と同じ順序付け。同じ日時ならidで決める。 */
private fun isLater(candidate: ScheduleEntity, reference: ScheduleEntity): Boolean = when {
    candidate.date != reference.date -> candidate.date > reference.date
    candidate.startTime != reference.startTime -> candidate.startTime > reference.startTime
    else -> candidate.id > reference.id
}

private val scheduleOrder = compareBy<ScheduleEntity>({ it.date }, { it.startTime }, { it.id })

fun buildTreeOutline(
    goals: List<GoalEntity>,
    milestones: List<MilestoneEntity>,
    schedules: List<ScheduleEntity>,
    today: LocalDate = LocalDate.now()
): TreeOutline {
    val scheduleById = schedules.associateBy { it.id }
    val milestoneTitleById = milestones.associate { it.id to it.title }

    fun outlineOf(schedule: ScheduleEntity) = OutlineSchedule(
        schedule = schedule,
        links = schedule.linkedIds
            .mapNotNull { scheduleById[it] }
            .sortedWith(scheduleOrder)
            .map { linked ->
                OutlineLink(linked, linked.milestoneId?.let { milestoneTitleById[it] }, isLater(linked, schedule))
            }
    )

    val schedulesByMilestone = schedules.filter { it.milestoneId != null }.groupBy { it.milestoneId!! }
    val milestonesByGoal = milestones.groupBy { it.goalId }

    val outlineGoals = goals.map { goal ->
        val goalMilestones = milestonesByGoal[goal.id].orEmpty().map { milestone ->
            OutlineMilestone(
                milestone = milestone,
                status = milestone.status.toNodeStatus(),
                schedules = schedulesByMilestone[milestone.id].orEmpty().sortedWith(scheduleOrder).map(::outlineOf)
            )
        }
        OutlineGoal(
            goal = goal,
            status = goalNodeStatus(goal, goalMilestones.map { it.status }, today),
            milestones = goalMilestones,
            doneMilestoneCount = goalMilestones.count { it.milestone.status == MilestoneStatus.DONE }
        )
    }
        // 済んだ大目的は下へ。それ以外は始まりの早い順(開始日のない目的は後ろ)。
        .sortedWith(compareBy({ it.status == NodeStatus.DONE }, { it.goal.startDate ?: LocalDate.MAX }))

    // 中日程が削除済みなどで見つからない小日程も「中日程なし」にまとめる。
    val knownMilestoneIds = milestones.map { it.id }.toSet()
    val unassigned = schedules
        .filter { it.milestoneId == null || it.milestoneId !in knownMilestoneIds }
        .sortedWith(scheduleOrder)
        .map(::outlineOf)

    return TreeOutline(outlineGoals, unassigned)
}

// ---------------------------------------------------------------------------
// 表示用の行(開閉状態を反映して平らに並べる。LazyColumnの1行 = 1要素)
// ---------------------------------------------------------------------------

private sealed interface OutlineRow {
    val key: String
    val level: Int

    data class Goal(val item: OutlineGoal, val expanded: Boolean) : OutlineRow {
        override val key = "g:${item.goal.id}"
        override val level = 0
    }

    data class Milestone(val item: OutlineMilestone, val expanded: Boolean) : OutlineRow {
        override val key = "m:${item.milestone.id}"
        override val level = 1
    }

    data class Unassigned(val count: Int, val expanded: Boolean) : OutlineRow {
        override val key = KEY_UNASSIGNED
        override val level = 0
    }

    data class Schedule(val item: OutlineSchedule, override val level: Int, val linksExpanded: Boolean) : OutlineRow {
        override val key = "s:${item.schedule.id}"
    }

    data class Link(val scheduleId: String, val link: OutlineLink, override val level: Int) : OutlineRow {
        override val key = "l:$scheduleId:${link.schedule.id}"
    }

    data class PastCollapsed(val groupId: String, val count: Int, override val level: Int, val expanded: Boolean) : OutlineRow {
        override val key = "p:$groupId"
    }

    data class TodayDivider(val groupId: String, override val level: Int) : OutlineRow {
        override val key = "t:$groupId"
    }

    data class Empty(override val key: String, val text: String, override val level: Int) : OutlineRow
}

private const val KEY_UNASSIGNED = "u"
/** 過去の小日程がこれより多いと「過去の予定 N件」の1行にたたむ。 */
private const val PAST_COLLAPSE_THRESHOLD = 3

private fun expansionKeyOfLinks(scheduleId: String) = "l:$scheduleId"

/** 済んでいない大目的・中日程は開いておく(開閉を変えたものだけ[isExpanded]側で覚える)。 */
private fun flattenOutline(
    outline: TreeOutline,
    today: LocalDate,
    isExpanded: (key: String, default: Boolean) -> Boolean
): List<OutlineRow> {
    val rows = mutableListOf<OutlineRow>()

    fun addSchedule(item: OutlineSchedule, level: Int) {
        val linksExpanded = item.links.isNotEmpty() && isExpanded(expansionKeyOfLinks(item.schedule.id), false)
        rows += OutlineRow.Schedule(item, level, linksExpanded)
        if (linksExpanded) {
            item.links.forEach { rows += OutlineRow.Link(item.schedule.id, it, level) }
        }
    }

    fun addSchedules(groupId: String, schedules: List<OutlineSchedule>, level: Int) {
        if (schedules.isEmpty()) {
            rows += OutlineRow.Empty("e:$groupId", "小日程はまだありません", level)
            return
        }
        val past = schedules.filter { it.schedule.date < today }
        val upcoming = schedules.filter { it.schedule.date >= today }
        if (past.size > PAST_COLLAPSE_THRESHOLD) {
            val expanded = isExpanded("p:$groupId", false)
            rows += OutlineRow.PastCollapsed(groupId, past.size, level, expanded)
            if (expanded) past.forEach { addSchedule(it, level) }
        } else {
            past.forEach { addSchedule(it, level) }
        }
        // 過去と今後の境目に「今日」の線を引き、今どこにいるかを一目で分かるようにする。
        if (past.isNotEmpty() && upcoming.isNotEmpty()) {
            rows += OutlineRow.TodayDivider(groupId, level)
        }
        upcoming.forEach { addSchedule(it, level) }
    }

    for (goal in outline.goals) {
        val goalRow = OutlineRow.Goal(goal, isExpanded("g:${goal.goal.id}", goal.status != NodeStatus.DONE))
        rows += goalRow
        if (!goalRow.expanded) continue
        if (goal.milestones.isEmpty()) {
            rows += OutlineRow.Empty("e:${goal.goal.id}", "中日程はまだありません", 1)
        }
        for (milestone in goal.milestones) {
            val milestoneRow = OutlineRow.Milestone(
                milestone,
                isExpanded("m:${milestone.milestone.id}", milestone.status != NodeStatus.DONE)
            )
            rows += milestoneRow
            if (milestoneRow.expanded) addSchedules(milestone.milestone.id, milestone.schedules, 2)
        }
    }

    if (outline.unassigned.isNotEmpty()) {
        val header = OutlineRow.Unassigned(outline.unassigned.size, isExpanded(KEY_UNASSIGNED, false))
        rows += header
        if (header.expanded) addSchedules(KEY_UNASSIGNED, outline.unassigned, 1)
    }
    return rows
}

// ---------------------------------------------------------------------------
// 画面
// ---------------------------------------------------------------------------

/** 1段下げるごとの字下げ幅。 */
private val INDENT = 20.dp
/** 行頭の開閉ボタン(または日付)の枠。タップ領域として48dpを確保する。 */
private val LEADING_SLOT = 48.dp

private val outlineDateFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)
private val outlineWeekdayFormatter = DateTimeFormatter.ofPattern("E", Locale.JAPAN)
private val outlineLinkDateFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)
private val outlineTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/** 開閉状態(ユーザーが変えたものだけ)。画面回転やプロセス再生成でも保つ。 */
private val ExpansionSaver = Saver<SnapshotStateMap<String, Boolean>, ArrayList<String>>(
    save = { map -> ArrayList(map.map { (key, expanded) -> (if (expanded) "1" else "0") + key }) },
    restore = { saved ->
        mutableStateMapOf<String, Boolean>().apply { saved.forEach { put(it.substring(1), it.first() == '1') } }
    }
)

private val rowAppearSpec = tween<Float>(Motion.DurationMedium)
private val rowDisappearSpec = tween<Float>(Motion.DurationShort)

/**
 * 全体マップの「ツリー」表示。大目的→中日程→小日程を字下げで並べ、開閉できる一覧にする。
 * タイムライン表示と違って線が交差せず、題名も省略されずに読める。
 */
@Composable
fun WholeTreeOutlineView(
    outline: TreeOutline,
    onGoalClick: (String) -> Unit,
    onMilestoneClick: (String) -> Unit,
    onScheduleClick: (String) -> Unit,
    onAddGoalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (outline.isEmpty) {
        EmptyState(
            icon = Icons.Outlined.Flag,
            message = "まだ大目的がありません",
            modifier = modifier.fillMaxSize(),
            actionLabel = "大目的を追加",
            onAction = onAddGoalClick
        )
        return
    }

    val expansion = rememberSaveable(saver = ExpansionSaver) { mutableStateMapOf<String, Boolean>() }
    val today = remember { LocalDate.now() }
    val rows = flattenOutline(outline, today) { key, default -> expansion[key] ?: default }
    fun toggle(key: String, default: Boolean) {
        expansion[key] = !(expansion[key] ?: default)
    }

    // 初めて開いたときは、進行中の大目的が画面の先頭に来るようにする。
    val listState = rememberLazyListState()
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialScrollDone) {
            val index = rows.indexOfFirst { it is OutlineRow.Goal && it.item.status == NodeStatus.ACTIVE }
            if (index > 0) listState.scrollToItem(index)
            initialScrollDone = true
        }
    }

    val isDark = LocalIsDarkTheme.current
    val guideColor = MaterialTheme.colorScheme.outlineVariant

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.FabClearance)
    ) {
        items(rows, key = { it.key }, contentType = { it::class.simpleName }) { row ->
            val itemModifier = Modifier.animateItem(
                fadeInSpec = rowAppearSpec,
                placementSpec = tween(Motion.DurationLong, easing = Motion.Emphasized),
                fadeOutSpec = rowDisappearSpec
            )
            when (row) {
                is OutlineRow.Goal -> GoalOutlineRow(
                    row = row,
                    color = TreeTier.GOAL.color(isDark),
                    onClick = { onGoalClick(row.item.goal.id) },
                    onToggle = { toggle(row.key, row.item.status != NodeStatus.DONE) },
                    modifier = itemModifier
                )
                is OutlineRow.Milestone -> MilestoneOutlineRow(
                    row = row,
                    color = TreeTier.MILESTONE.color(isDark),
                    guideColor = guideColor,
                    onClick = { onMilestoneClick(row.item.milestone.id) },
                    onToggle = { toggle(row.key, row.item.status != NodeStatus.DONE) },
                    modifier = itemModifier
                )
                is OutlineRow.Unassigned -> UnassignedOutlineRow(
                    row = row,
                    color = TreeTier.SCHEDULE.color(isDark),
                    onToggle = { toggle(row.key, false) },
                    modifier = itemModifier
                )
                is OutlineRow.Schedule -> ScheduleOutlineRow(
                    row = row,
                    today = today,
                    color = TreeTier.SCHEDULE.color(isDark),
                    linkColor = if (isDark) PeerLinkColorDark else PeerLinkColorLight,
                    guideColor = guideColor,
                    onClick = { onScheduleClick(row.item.schedule.id) },
                    onToggleLinks = { toggle(expansionKeyOfLinks(row.item.schedule.id), false) },
                    modifier = itemModifier
                )
                is OutlineRow.Link -> LinkOutlineRow(
                    row = row,
                    linkColor = if (isDark) PeerLinkColorDark else PeerLinkColorLight,
                    guideColor = guideColor,
                    onClick = { onScheduleClick(row.link.schedule.id) },
                    modifier = itemModifier
                )
                is OutlineRow.PastCollapsed -> OutlineRowFrame(
                    level = row.level,
                    guideColor = guideColor,
                    onClick = { toggle(row.key, false) },
                    modifier = itemModifier,
                    leading = { ExpandChevron(expanded = row.expanded, label = "過去の予定", onToggle = { toggle(row.key, false) }) }
                ) {
                    Text(
                        text = if (row.expanded) "過去の予定 ${row.count}件をたたむ" else "過去の予定 ${row.count}件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                is OutlineRow.TodayDivider -> TodayDividerRow(
                    level = row.level,
                    lineColor = if (isDark) TodayLineColorDark else TodayLineColorLight,
                    guideColor = guideColor,
                    modifier = itemModifier
                )
                is OutlineRow.Empty -> OutlineRowFrame(
                    level = row.level,
                    guideColor = guideColor,
                    onClick = null,
                    minHeight = 40.dp,
                    modifier = itemModifier,
                    leading = {}
                ) {
                    Text(
                        text = row.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 親の階層ごとに縦の字下げ線を引く(開閉ボタンの中心の位置)。線は交差しない。 */
private fun Modifier.indentGuides(level: Int, color: Color): Modifier =
    if (level == 0) this else drawBehind {
        val stroke = 1.dp.toPx()
        for (ancestor in 0 until level) {
            val x = (INDENT * ancestor + LEADING_SLOT / 2).toPx()
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
        }
    }

/** 字下げ・字下げ線・行頭の枠をそろえた、ツリーの1行の共通の形。 */
@Composable
private fun OutlineRowFrame(
    level: Int,
    guideColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    minHeight: Dp = 56.dp,
    leading: @Composable () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .indentGuides(level, guideColor)
            .padding(start = INDENT * level, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(LEADING_SLOT), contentAlignment = Alignment.Center) { leading() }
        content()
    }
}

@Composable
private fun ExpandChevron(expanded: Boolean, label: String, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(Motion.DurationMedium, easing = Motion.Standard),
        label = "chevron"
    )
    IconButton(onClick = onToggle) {
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (expanded) "${label}を閉じる" else "${label}を開く",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation)
        )
    }
}

@Composable
private fun GoalOutlineRow(
    row: OutlineRow.Goal,
    color: Color,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier
) {
    val goal = row.item.goal
    val total = row.item.milestones.size
    val isDone = row.item.status == NodeStatus.DONE
    val summary = listOfNotNull(
        formatRangeSubtitle(goal.startDate, goal.endDate).ifBlank { null },
        when {
            goal.type == GoalType.HABIT -> "継続中の習慣目的"
            total > 0 -> "中日程 ${row.item.doneMilestoneCount}/$total 完了"
            else -> null
        }
    ).joinToString(" · ")

    OutlineRowFrame(
        level = 0,
        guideColor = Color.Transparent,
        onClick = onClick,
        minHeight = 64.dp,
        // 大目的どうしの区切りが分かるよう、少し間を空けて一段沈んだ面に載せる。
        modifier = modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.surfaceContainerLow),
        leading = { ExpandChevron(expanded = row.expanded, label = goal.title, onToggle = onToggle) }
    ) {
        TierIcon(Icons.Outlined.Flag, "大目的", color)
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (goal.type == GoalType.PHASED && total > 0) {
                AppProgressBar(
                    ratio = row.item.doneMilestoneCount.toFloat() / total,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MilestoneOutlineRow(
    row: OutlineRow.Milestone,
    color: Color,
    guideColor: Color,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier
) {
    val milestone = row.item.milestone
    val (icon, statusLabel) = when (milestone.status) {
        MilestoneStatus.DONE -> Icons.Default.CheckCircle to "完了"
        MilestoneStatus.ACTIVE -> Icons.Default.Loop to "進行中"
        MilestoneStatus.UPCOMING -> Icons.Outlined.RadioButtonUnchecked to "未着手"
    }
    val summary = listOfNotNull(
        formatRangeSubtitle(milestone.startDate, milestone.endDate).ifBlank { null },
        "小日程 ${row.item.schedules.size}"
    ).joinToString(" · ")
    val isDone = row.item.status == NodeStatus.DONE

    OutlineRowFrame(
        level = row.level,
        guideColor = guideColor,
        onClick = onClick,
        modifier = modifier,
        leading = { ExpandChevron(expanded = row.expanded, label = milestone.title, onToggle = onToggle) }
    ) {
        TierIcon(icon, "中日程・$statusLabel", color)
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = milestone.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnassignedOutlineRow(row: OutlineRow.Unassigned, color: Color, onToggle: () -> Unit, modifier: Modifier) {
    OutlineRowFrame(
        level = 0,
        guideColor = Color.Transparent,
        onClick = onToggle,
        minHeight = 64.dp,
        modifier = modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.surfaceContainerLow),
        leading = { ExpandChevron(expanded = row.expanded, label = "中日程なしの小日程", onToggle = onToggle) }
    ) {
        TierIcon(Icons.Outlined.EventNote, null, color)
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text("中日程なし", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "小日程 ${row.count}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TierIcon(icon: ImageVector, description: String?, color: Color) {
    Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(12.dp))
}

@Composable
private fun ScheduleOutlineRow(
    row: OutlineRow.Schedule,
    today: LocalDate,
    color: Color,
    linkColor: Color,
    guideColor: Color,
    onClick: () -> Unit,
    onToggleLinks: () -> Unit,
    modifier: Modifier
) {
    val schedule = row.item.schedule
    val isPast = schedule.date < today
    val textColor = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val dateColor = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else color

    OutlineRowFrame(
        level = row.level,
        guideColor = guideColor,
        onClick = onClick,
        modifier = modifier,
        // 小日程は開閉するものがないので、行頭の枠に日付を置く。
        leading = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = schedule.date.format(outlineDateFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = dateColor
                )
                Text(
                    text = schedule.date.format(outlineWeekdayFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = dateColor
                )
            }
        }
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (schedule.date == today) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TodayBadge()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPast) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "済み",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp).padding(end = 2.dp)
                    )
                }
                Text(
                    text = "${schedule.startTime.format(outlineTimeFormatter)}〜${schedule.endTime.format(outlineTimeFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val linkCount = row.item.links.size
        if (linkCount > 0) {
            // 線を引く代わりに件数だけ示し、タップでつながる予定を行の下に開く。
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(
                        onClickLabel = if (row.linksExpanded) "つながる予定を閉じる" else "つながる予定を表示"
                    ) { onToggleLinks() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Link, contentDescription = "つながる予定", tint = linkColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = linkCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = linkColor
                )
            }
        }
    }
}

@Composable
private fun LinkOutlineRow(row: OutlineRow.Link, linkColor: Color, guideColor: Color, onClick: () -> Unit, modifier: Modifier) {
    val link = row.link
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .fillMaxWidth()
            .indentGuides(row.level, guideColor)
            .padding(start = INDENT * row.level + LEADING_SLOT, end = 12.dp, bottom = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(linkColor.copy(alpha = 0.08f))
                .border(1.dp, linkColor.copy(alpha = 0.6f), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = (if (link.isLater) "→ " else "← ") +
                    "${link.schedule.date.format(outlineLinkDateFormatter)} ${link.schedule.startTime.format(outlineTimeFormatter)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = linkColor
            )
            Text(
                text = (link.milestoneTitle?.let { "$it › " } ?: "") + link.schedule.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TodayDividerRow(level: Int, lineColor: Color, guideColor: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .indentGuides(level, guideColor)
            .padding(start = INDENT * level + LEADING_SLOT, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TodayBadge()
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(lineColor))
    }
}
