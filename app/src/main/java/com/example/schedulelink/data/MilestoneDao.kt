package com.example.schedulelink.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {

    @Query("SELECT * FROM milestones WHERE goalId = :goalId ORDER BY orderIndex, id")
    fun observeByGoal(goalId: Long): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones ORDER BY goalId, orderIndex, id")
    fun observeAll(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE id = :id")
    fun observeById(id: Long): Flow<MilestoneEntity?>

    @Query("SELECT COUNT(*) FROM milestones WHERE goalId = :goalId")
    fun observeCountForGoal(goalId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM milestones WHERE goalId = :goalId AND status = 'DONE'")
    fun observeDoneCountForGoal(goalId: Long): Flow<Int>

    @Insert
    suspend fun insert(milestone: MilestoneEntity): Long

    @Update
    suspend fun update(milestone: MilestoneEntity)

    @Delete
    suspend fun delete(milestone: MilestoneEntity)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteById(id: Long)
}
