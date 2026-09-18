package com.example.schedulelink.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalProgress
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class GoalDetailViewModel(
    private val goalRepository: GoalRepository,
    private val milestoneRepository: MilestoneRepository
) : ViewModel() {

    fun goal(id: Long): Flow<GoalEntity?> = goalRepository.goalById(id)

    fun milestones(goalId: Long): Flow<List<MilestoneEntity>> = milestoneRepository.milestonesForGoal(goalId)

    fun progress(goalId: Long): Flow<GoalProgress> = milestoneRepository.progressForGoal(goalId)

    fun deleteGoal(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            goalRepository.deleteGoal(id)
            onDone()
        }
    }
}
