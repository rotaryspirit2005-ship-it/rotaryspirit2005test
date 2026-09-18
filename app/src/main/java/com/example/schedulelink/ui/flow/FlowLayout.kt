package com.example.schedulelink.ui.flow

import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

enum class BranchSide { LEFT, RIGHT }

data class FlowBranch(val target: ScheduleEntity)

data class FlowRow(
    val schedule: ScheduleEntity,
    val linkedCount: Int,
    val branches: List<FlowBranch>,
    val overflowCount: Int,
    val side: BranchSide
)

private const val MAX_BRANCHES_PER_ROW = 1

/**
 * トランク(時刻順)+枝分岐チップのレイアウトを計算する。
 * ある予定から見て時刻が後の予定へのリンクだけを「枝」として扱うことで、
 * 同じリンクが両側に重複して表示されるのを防ぐ。
 */
fun buildFlowRows(
    schedules: List<ScheduleEntity>,
    linkedIdsById: Map<Long, List<Long>>
): List<FlowRow> {
    val indexById = schedules.withIndex().associate { (index, schedule) -> schedule.id to index }
    val byId = schedules.associateBy { it.id }
    var branchSideToggle = false

    return schedules.mapIndexed { index, schedule ->
        val linkedIds = linkedIdsById[schedule.id].orEmpty()
        val outgoingIds = linkedIds.filter { linkedId ->
            val targetIndex = indexById[linkedId]
            targetIndex != null && targetIndex > index
        }
        val visibleTargets = outgoingIds.take(MAX_BRANCHES_PER_ROW).mapNotNull { byId[it] }
        val overflow = (outgoingIds.size - visibleTargets.size).coerceAtLeast(0)

        val side = if (branchSideToggle) BranchSide.LEFT else BranchSide.RIGHT
        if (visibleTargets.isNotEmpty()) branchSideToggle = !branchSideToggle

        FlowRow(
            schedule = schedule,
            linkedCount = linkedIds.size,
            branches = visibleTargets.map { FlowBranch(it) },
            overflowCount = overflow,
            side = side
        )
    }
}

/**
 * 予定一覧のFlowと各予定のリンク先Flowを合成し、[FlowRow]の一覧を作る。
 * 日次リストの「フロー」表示・中日程の「フロー」表示の両方から使う。
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeFlowRows(
    schedulesFlow: Flow<List<ScheduleEntity>>,
    repository: ScheduleRepository
): Flow<List<FlowRow>> = schedulesFlow.flatMapLatest { list ->
    if (list.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(list.map { repository.linkedIds(it.id) }) { linkedIdLists ->
            val linkedIdsById = list.mapIndexed { index, schedule -> schedule.id to linkedIdLists[index] }.toMap()
            buildFlowRows(list, linkedIdsById)
        }
    }
}
