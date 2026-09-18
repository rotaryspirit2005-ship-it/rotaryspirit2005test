package com.example.schedulelink.ui.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.ui.flow.FlowRow
import com.example.schedulelink.ui.flow.observeFlowRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MilestoneDetailViewModel(
    private val milestoneRepository: MilestoneRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    fun milestone(id: Long): Flow<MilestoneEntity?> = milestoneRepository.milestoneById(id)

    fun schedules(milestoneId: Long): Flow<List<ScheduleEntity>> =
        scheduleRepository.schedulesForMilestone(milestoneId)

    fun flowRows(milestoneId: Long): Flow<List<FlowRow>> =
        observeFlowRows(
            schedulesFlow = scheduleRepository.schedulesForMilestone(milestoneId),
            repository = scheduleRepository
        )

    fun deleteMilestone(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            milestoneRepository.deleteMilestone(id)
            onDone()
        }
    }
}
