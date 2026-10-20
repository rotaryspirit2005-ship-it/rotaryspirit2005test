package com.example.schedulelink.ui.flow

import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.MilestoneStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// 全体マップ(ツリー・タイムライン)で共通に使う、階層・進み具合・期間の表示。

enum class TreeTier { GOAL, MILESTONE, SCHEDULE }

/** ノードの進み具合。マップ上での見た目(完了は少し暗くする等)に使う。 */
enum class NodeStatus { DONE, ACTIVE, UPCOMING }

private val monthDayFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)

internal fun MilestoneStatus.toNodeStatus(): NodeStatus = when (this) {
    MilestoneStatus.DONE -> NodeStatus.DONE
    MilestoneStatus.ACTIVE -> NodeStatus.ACTIVE
    MilestoneStatus.UPCOMING -> NodeStatus.UPCOMING
}

/**
 * 大目的自体には完了フラグが無いので、配下の中日程の状態から推測する。
 * (中日程が1つも無い場合だけ、目的自身の開始日・終了日から推測する。)
 */
internal fun goalNodeStatus(goal: GoalEntity, milestoneStatuses: List<NodeStatus>, today: LocalDate): NodeStatus = when {
    milestoneStatuses.isNotEmpty() && milestoneStatuses.all { it == NodeStatus.DONE } -> NodeStatus.DONE
    milestoneStatuses.any { it == NodeStatus.DONE || it == NodeStatus.ACTIVE } -> NodeStatus.ACTIVE
    milestoneStatuses.isNotEmpty() -> NodeStatus.UPCOMING
    goal.endDate != null && goal.endDate.isBefore(today) -> NodeStatus.DONE
    goal.startDate != null && !goal.startDate.isAfter(today) -> NodeStatus.ACTIVE
    else -> NodeStatus.UPCOMING
}

internal fun formatRangeSubtitle(start: LocalDate?, end: LocalDate?): String = when {
    start != null && end != null -> "${start.format(monthDayFormatter)}〜${end.format(monthDayFormatter)}"
    start != null -> "${start.format(monthDayFormatter)}〜"
    end != null -> "〜${end.format(monthDayFormatter)}"
    else -> ""
}
