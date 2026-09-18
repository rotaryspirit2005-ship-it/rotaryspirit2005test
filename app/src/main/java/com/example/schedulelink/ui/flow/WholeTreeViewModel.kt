package com.example.schedulelink.ui.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class WholeTreeViewModel(
    goalRepository: GoalRepository,
    milestoneRepository: MilestoneRepository,
    scheduleRepository: ScheduleRepository
) : ViewModel() {

    val tree: StateFlow<WholeTree> = combine(
        goalRepository.allGoals(),
        milestoneRepository.allMilestones(),
        scheduleRepository.allSchedules()
    ) { goals, milestones, schedules ->
        buildWholeTree(goals, milestones, schedules)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WholeTree(emptyList(), emptyList(), emptyList(), 0f)
    )
}
