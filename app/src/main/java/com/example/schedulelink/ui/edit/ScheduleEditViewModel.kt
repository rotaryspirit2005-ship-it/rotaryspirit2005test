package com.example.schedulelink.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleEditViewModel(
    private val repository: ScheduleRepository,
    private val milestoneRepository: MilestoneRepository
) : ViewModel() {

    val allSchedules: StateFlow<List<ScheduleEntity>> = repository.allSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allMilestones: StateFlow<List<MilestoneEntity>> = milestoneRepository.allMilestones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadForEdit(id: Long, onLoaded: (ScheduleEntity, Set<Long>) -> Unit) {
        viewModelScope.launch {
            val schedule = repository.scheduleById(id).first()
            val links = repository.linkedIds(id).first().toSet()
            if (schedule != null) {
                onLoaded(schedule, links)
            }
        }
    }

    fun save(schedule: ScheduleEntity, linkedIds: Set<Long>, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.saveSchedule(schedule, linkedIds)
            onSaved(id)
        }
    }
}
