package com.example.schedulelink.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalProgress
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalListItem(val goal: GoalEntity, val progress: GoalProgress)

class GoalListViewModel(
    private val goalRepository: GoalRepository,
    private val milestoneRepository: MilestoneRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val goals: StateFlow<List<GoalListItem>> = goalRepository.allGoals()
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(list.map { goal -> milestoneRepository.progressForGoal(goal.id) }) { progresses ->
                    list.mapIndexed { index, goal -> GoalListItem(goal, progresses[index]) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            milestoneRepository.deleteAllForGoal(id)
            goalRepository.deleteGoal(id)
        }
    }
}
