package com.example.schedulelink.ui.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MilestoneDetailViewModel(
    private val milestoneRepository: MilestoneRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    fun milestone(id: String): Flow<MilestoneEntity?> = milestoneRepository.milestoneById(id)

    fun schedules(milestoneId: String): Flow<List<ScheduleEntity>> =
        scheduleRepository.schedulesForMilestone(milestoneId)

    fun deleteMilestone(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            milestoneRepository.deleteMilestone(id)
            onDone()
        }
    }
}
