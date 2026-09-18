package com.example.schedulelink.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class GoalProgress(val doneCount: Int, val totalCount: Int) {
    val ratio: Float get() = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount
}

class MilestoneRepository(private val dao: MilestoneDao) {

    fun milestonesForGoal(goalId: Long): Flow<List<MilestoneEntity>> = dao.observeByGoal(goalId)

    fun allMilestones(): Flow<List<MilestoneEntity>> = dao.observeAll()

    fun milestoneById(id: Long): Flow<MilestoneEntity?> = dao.observeById(id)

    fun progressForGoal(goalId: Long): Flow<GoalProgress> =
        combine(dao.observeDoneCountForGoal(goalId), dao.observeCountForGoal(goalId)) { done, total ->
            GoalProgress(done, total)
        }

    suspend fun saveMilestone(milestone: MilestoneEntity): Long {
        return if (milestone.id == 0L) dao.insert(milestone) else {
            dao.update(milestone)
            milestone.id
        }
    }

    suspend fun deleteMilestone(id: Long) {
        dao.deleteById(id)
    }
}
