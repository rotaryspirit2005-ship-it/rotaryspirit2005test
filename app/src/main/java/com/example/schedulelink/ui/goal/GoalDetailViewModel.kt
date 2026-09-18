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

    fun goal(id: String): Flow<GoalEntity?> = goalRepository.goalById(id)

    fun milestones(goalId: String): Flow<List<MilestoneEntity>> = milestoneRepository.milestonesForGoal(goalId)

    fun progress(goalId: String): Flow<GoalProgress> = milestoneRepository.progressForGoal(goalId)

    fun deleteGoal(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            milestoneRepository.deleteAllForGoal(id)
            goalRepository.deleteGoal(id)
            onDone()
        }
    }
}
