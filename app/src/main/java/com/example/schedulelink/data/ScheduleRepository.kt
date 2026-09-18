package com.example.schedulelink.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class ScheduleRepository(private val dao: ScheduleDao) {

    fun allSchedules(): Flow<List<ScheduleEntity>> = dao.observeAll()

    fun schedulesForDate(date: LocalDate): Flow<List<ScheduleWithLinkCount>> =
        dao.observeByDateWithLinkCount(date)

    fun scheduleById(id: Long): Flow<ScheduleEntity?> = dao.observeById(id)

    fun schedulesForMilestone(milestoneId: Long): Flow<List<ScheduleEntity>> =
        dao.observeByMilestone(milestoneId)

    fun countForMilestone(milestoneId: Long): Flow<Int> = dao.observeCountForMilestone(milestoneId)

    fun linkedSchedules(id: Long): Flow<List<ScheduleEntity>> = dao.observeLinkedSchedules(id)

    fun linkedIds(id: Long): Flow<List<Long>> = dao.observeLinkedIds(id)

    suspend fun saveSchedule(schedule: ScheduleEntity, linkedIds: Set<Long>): Long {
        val id = if (schedule.id == 0L) {
            dao.insert(schedule)
        } else {
            dao.update(schedule)
            schedule.id
        }
        setLinks(id, linkedIds)
        return id
    }

    suspend fun deleteSchedule(id: Long) {
        dao.deleteAllLinksFor(id)
        dao.deleteById(id)
    }

    private suspend fun setLinks(scheduleId: Long, newLinkedIds: Set<Long>) {
        val current = dao.observeLinkedIds(scheduleId).first().toSet()
        val toAdd = newLinkedIds - current
        val toRemove = current - newLinkedIds

        toAdd.forEach { otherId ->
            dao.insertLink(ScheduleLinkCrossRef(scheduleId, otherId))
            dao.insertLink(ScheduleLinkCrossRef(otherId, scheduleId))
        }
        toRemove.forEach { otherId ->
            dao.deleteLinkOneWay(scheduleId, otherId)
            dao.deleteLinkOneWay(otherId, scheduleId)
        }
    }
}
