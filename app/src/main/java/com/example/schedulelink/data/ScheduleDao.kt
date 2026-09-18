package com.example.schedulelink.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY date, startTime")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query(
        """
        SELECT s.*, (SELECT COUNT(*) FROM schedule_links l WHERE l.scheduleId = s.id) AS linkedCount
        FROM schedules s
        WHERE s.date = :date
        ORDER BY s.startTime
        """
    )
    fun observeByDateWithLinkCount(date: LocalDate): Flow<List<ScheduleWithLinkCount>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    fun observeById(id: Long): Flow<ScheduleEntity?>

    @Query("SELECT * FROM schedules WHERE milestoneId = :milestoneId ORDER BY date, startTime")
    fun observeByMilestone(milestoneId: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT COUNT(*) FROM schedules WHERE milestoneId = :milestoneId")
    fun observeCountForMilestone(milestoneId: Long): Flow<Int>

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Delete
    suspend fun delete(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT linkedScheduleId FROM schedule_links WHERE scheduleId = :scheduleId")
    fun observeLinkedIds(scheduleId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT * FROM schedules
        WHERE id IN (SELECT linkedScheduleId FROM schedule_links WHERE scheduleId = :scheduleId)
        ORDER BY date, startTime
        """
    )
    fun observeLinkedSchedules(scheduleId: Long): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: ScheduleLinkCrossRef)

    @Query("DELETE FROM schedule_links WHERE scheduleId = :a AND linkedScheduleId = :b")
    suspend fun deleteLinkOneWay(a: Long, b: Long)

    @Query("DELETE FROM schedule_links WHERE scheduleId = :scheduleId OR linkedScheduleId = :scheduleId")
    suspend fun deleteAllLinksFor(scheduleId: Long)
}
