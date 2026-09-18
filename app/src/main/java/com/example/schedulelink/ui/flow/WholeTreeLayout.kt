package com.example.schedulelink.ui.flow

import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.ScheduleEntity

enum class TreeTier { GOAL, MILESTONE, SCHEDULE }

data class TreeNode(
    val id: String,
    val tier: TreeTier,
    val title: String,
    val subtitle: String,
    /** 横位置(スロット単位)。実際のdp換算は描画側で行う。 */
    val slot: Float
)

/** 小日程どうしの横のリンク(親子関係とは別の、同じ階層内のつながり)。 */
data class PeerLink(val fromId: String, val toId: String)

data class WholeTree(
    val nodes: List<TreeNode>,
    val peerLinks: List<PeerLink>,
    /** 親子関係の線を引くための (親ノードID, 子ノードID) の一覧。 */
    val treeEdges: List<Pair<String, String>>,
    val maxSlot: Float
)

/**
 * 大目的→中日程→小日程の親子ツリーを1つに組み立てる。
 * 子の位置(スロット)は「葉(末端)を左から順に並べ、親は子の平均位置に置く」
 * という単純な木構造レイアウトで求める。milestoneIdの無い小日程は
 * どの目的にも属さない「未分類」として、ツリーの右側に別枠で並べる。
 */
fun buildWholeTree(
    goals: List<GoalEntity>,
    milestones: List<MilestoneEntity>,
    schedules: List<ScheduleEntity>
): WholeTree {
    val milestonesByGoal: Map<String, List<MilestoneEntity>> = milestones.groupBy { it.goalId }
    val schedulesByMilestone: Map<String, List<ScheduleEntity>> =
        schedules.filter { it.milestoneId != null }.groupBy { it.milestoneId!! }
    val orphanSchedules = schedules.filter { it.milestoneId == null }

    val nodes = mutableListOf<TreeNode>()
    val edges = mutableListOf<Pair<String, String>>()
    var nextSlot = 0f

    for (goal in goals) {
        val goalMilestones = milestonesByGoal[goal.id].orEmpty()
        val milestoneSlots = mutableListOf<Float>()

        if (goalMilestones.isEmpty()) {
            milestoneSlots += nextSlot
            nextSlot += 1f
        } else {
            for (milestone in goalMilestones) {
                val milestoneSchedules = schedulesByMilestone[milestone.id].orEmpty()
                val scheduleSlots = mutableListOf<Float>()

                if (milestoneSchedules.isEmpty()) {
                    scheduleSlots += nextSlot
                    nextSlot += 1f
                } else {
                    for (schedule in milestoneSchedules) {
                        val slot = nextSlot
                        nextSlot += 1f
                        scheduleSlots += slot
                        nodes += TreeNode(
                            id = schedule.id,
                            tier = TreeTier.SCHEDULE,
                            title = schedule.title,
                            subtitle = schedule.startTime.toString(),
                            slot = slot
                        )
                        edges += milestone.id to schedule.id
                    }
                }

                val milestoneSlot = scheduleSlots.average().toFloat()
                milestoneSlots += milestoneSlot
                nodes += TreeNode(
                    id = milestone.id,
                    tier = TreeTier.MILESTONE,
                    title = milestone.title,
                    subtitle = "",
                    slot = milestoneSlot
                )
                edges += goal.id to milestone.id
            }
        }

        val goalSlot = milestoneSlots.average().toFloat()
        nodes += TreeNode(
            id = goal.id,
            tier = TreeTier.GOAL,
            title = goal.title,
            subtitle = "",
            slot = goalSlot
        )
    }

    for (schedule in orphanSchedules) {
        val slot = nextSlot
        nextSlot += 1f
        nodes += TreeNode(
            id = schedule.id,
            tier = TreeTier.SCHEDULE,
            title = schedule.title,
            subtitle = schedule.startTime.toString(),
            slot = slot
        )
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

    return WholeTree(
        nodes = nodes,
        peerLinks = peerLinks,
        treeEdges = edges,
        maxSlot = (nextSlot - 1f).coerceAtLeast(0f)
    )
}
