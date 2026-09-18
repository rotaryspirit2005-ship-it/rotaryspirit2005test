package com.example.schedulelink.data

import kotlinx.coroutines.flow.Flow

class GoalRepository(private val dao: GoalDao) {

    fun allGoals(): Flow<List<GoalEntity>> = dao.observeAll()

    fun goalById(id: Long): Flow<GoalEntity?> = dao.observeById(id)

    suspend fun saveGoal(goal: GoalEntity): Long {
        return if (goal.id == 0L) dao.insert(goal) else {
            dao.update(goal)
            goal.id
        }
    }

    suspend fun deleteGoal(id: Long) {
        dao.deleteById(id)
    }
}
