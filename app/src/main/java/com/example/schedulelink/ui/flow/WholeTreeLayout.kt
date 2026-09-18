package com.example.schedulelink.ui.flow

import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.ScheduleEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class TreeTier { GOAL, MILESTONE, SCHEDULE }

data class TreeNode(
    val id: String,
    val tier: TreeTier,
    val title: String,
    val subtitle: String,
    /** タイムライン上の位置を決める日付。 */
    val date: LocalDate,
    /** 同じ階層で日付が近いノードどうしが重ならないよう、縦にずらすためのレーン番号。 */
    val lane: Int
)

/** 小日程どうしの横のリンク(親子関係とは別の、同じ階層内のつながり)。 */
data class PeerLink(val fromId: String, val toId: String)

data class WholeTree(
    val nodes: List<TreeNode>,
    val peerLinks: List<PeerLink>,
    /** 親子関係の線を引くための (親ノードID, 子ノードID) の一覧。 */
    val treeEdges: List<Pair<String, String>>,
    val minDate: LocalDate,
    val maxDate: LocalDate
)

/** ノード1つが横方向に占める日数の目安。レーン分けの衝突判定に使う。 */
const val NODE_WIDTH_DAYS = 16L
const val MIN_GAP_DAYS = NODE_WIDTH_DAYS + 2L

private val monthDayFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)

private data class RawNode(
    val id: String,
    val tier: TreeTier,
    val title: String,
    val subtitle: String,
    val date: LocalDate
)

/**
 * 大目的→中日程→小日程の親子ツリーを、実際の日付に基づく横位置(タイムライン)で
 * 組み立てる。日付の近いノードどうしが同じ階層内で重ならないよう、
 * ガントチャートのように縦のレーンをずらして割り当てる。
 */
fun buildWholeTree(
    goals: List<GoalEntity>,
    milestones: List<MilestoneEntity>,
    schedules: List<ScheduleEntity>
): WholeTree {
    val today = LocalDate.now()
    val milestonesByGoal: Map<String, List<MilestoneEntity>> = milestones.groupBy { it.goalId }
    val schedulesByMilestone: Map<String, List<ScheduleEntity>> =
        schedules.filter { it.milestoneId != null }.groupBy { it.milestoneId!! }
    val orphanSchedules = schedules.filter { it.milestoneId == null }

    fun milestoneDate(milestone: MilestoneEntity): LocalDate {
        milestone.startDate?.let { return it }
        val childDates = schedulesByMilestone[milestone.id].orEmpty().map { it.date }
        return childDates.minOrNull() ?: today
    }

    val milestoneDateById = milestones.associate { it.id to milestoneDate(it) }

    fun goalDate(goal: GoalEntity): LocalDate {
        goal.startDate?.let { return it }
        val childDates = milestonesByGoal[goal.id].orEmpty().mapNotNull { milestoneDateById[it.id] }
        return childDates.minOrNull() ?: today
    }

    val rawNodes = mutableListOf<RawNode>()
    val edges = mutableListOf<Pair<String, String>>()

    for (goal in goals) {
        rawNodes += RawNode(
            id = goal.id,
            tier = TreeTier.GOAL,
            title = goal.title,
            subtitle = formatRangeSubtitle(goal.startDate, goal.endDate),
            date = goalDate(goal)
        )
        for (milestone in milestonesByGoal[goal.id].orEmpty()) {
            edges += goal.id to milestone.id
            rawNodes += RawNode(
                id = milestone.id,
                tier = TreeTier.MILESTONE,
                title = milestone.title,
                subtitle = formatRangeSubtitle(milestone.startDate, milestone.endDate),
                date = milestoneDateById.getValue(milestone.id)
            )
            for (schedule in schedulesByMilestone[milestone.id].orEmpty()) {
                edges += milestone.id to schedule.id
                rawNodes += RawNode(
                    id = schedule.id,
                    tier = TreeTier.SCHEDULE,
                    title = schedule.title,
                    subtitle = "${schedule.date.format(monthDayFormatter)} ${schedule.startTime}",
                    date = schedule.date
                )
            }
        }
    }

    for (schedule in orphanSchedules) {
        rawNodes += RawNode(
            id = schedule.id,
            tier = TreeTier.SCHEDULE,
            title = schedule.title,
            subtitle = "${schedule.date.format(monthDayFormatter)} ${schedule.startTime}",
            date = schedule.date
        )
    }

    if (rawNodes.isEmpty()) {
        return WholeTree(emptyList(), emptyList(), emptyList(), today, today)
    }

    // 前後にゆとりを持たせておくことで、端まで拡大・パンしても目盛りが
    // 画面いっぱいに続いているように見える(データの範囲ぴったりで途切れない)。
    val minDate = rawNodes.minOf { it.date }.minusDays(21)
    val maxDate = rawNodes.maxOf { it.date }.plusDays(21)

    val nodes = mutableListOf<TreeNode>()
    for (tier in TreeTier.entries) {
        val tierNodes = rawNodes.filter { it.tier == tier }.sortedBy { it.date }
        // 各レーンについて「次に空く日数位置」を覚えておき、
        // 先頭から入る空きレーンに詰めていく(ガントチャートと同じ考え方)。
        val laneFreeFromDay = mutableListOf<Long>()
        for (raw in tierNodes) {
            val startDay = ChronoUnit.DAYS.between(minDate, raw.date)
            var lane = laneFreeFromDay.indexOfFirst { it <= startDay }
            if (lane == -1) {
                lane = laneFreeFromDay.size
                laneFreeFromDay.add(startDay + MIN_GAP_DAYS)
            } else {
                laneFreeFromDay[lane] = startDay + MIN_GAP_DAYS
            }
            nodes += TreeNode(raw.id, raw.tier, raw.title, raw.subtitle, raw.date, lane)
        }
    }

    val peerLinks = mutableListOf<PeerLink>()
    val seenPairs = mutableSetOf<String>()
    for (schedule in schedules) {
        for (linkedId in schedule.linkedIds) {
            val key = listOf(schedule.id, linkedId).sorted().joinToString("|")
            if (seenPairs.add(key)) {
                peerLinks += PeerLink(schedule.id, linkedId)
            }
        }
    }

    return WholeTree(nodes, peerLinks, edges, minDate, maxDate)
}

private fun formatRangeSubtitle(start: LocalDate?, end: LocalDate?): String = when {
    start != null && end != null -> "${start.format(monthDayFormatter)}〜${end.format(monthDayFormatter)}"
    start != null -> "${start.format(monthDayFormatter)}〜"
    end != null -> "〜${end.format(monthDayFormatter)}"
    else -> ""
}
