package com.example.schedulelink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.ui.detail.ScheduleDetailViewModel
import com.example.schedulelink.ui.edit.ScheduleEditViewModel
import com.example.schedulelink.ui.goal.GoalDetailViewModel
import com.example.schedulelink.ui.goal.GoalEditViewModel
import com.example.schedulelink.ui.goal.GoalListViewModel
import com.example.schedulelink.ui.list.ScheduleListViewModel
import com.example.schedulelink.ui.milestone.MilestoneDetailViewModel
import com.example.schedulelink.ui.milestone.MilestoneEditViewModel
import com.example.schedulelink.ui.month.MonthViewModel
import com.example.schedulelink.ui.week.WeekViewModel

class AppViewModelFactory(
    private val repository: ScheduleRepository,
    private val goalRepository: GoalRepository,
    private val milestoneRepository: MilestoneRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ScheduleListViewModel::class.java) ->
                ScheduleListViewModel(repository) as T
            modelClass.isAssignableFrom(ScheduleDetailViewModel::class.java) ->
                ScheduleDetailViewModel(repository) as T
            modelClass.isAssignableFrom(ScheduleEditViewModel::class.java) ->
                ScheduleEditViewModel(repository, milestoneRepository) as T
            modelClass.isAssignableFrom(GoalListViewModel::class.java) ->
                GoalListViewModel(goalRepository, milestoneRepository) as T
            modelClass.isAssignableFrom(GoalEditViewModel::class.java) ->
                GoalEditViewModel(goalRepository) as T
            modelClass.isAssignableFrom(GoalDetailViewModel::class.java) ->
                GoalDetailViewModel(goalRepository, milestoneRepository) as T
            modelClass.isAssignableFrom(MilestoneEditViewModel::class.java) ->
                MilestoneEditViewModel(milestoneRepository) as T
            modelClass.isAssignableFrom(MilestoneDetailViewModel::class.java) ->
                MilestoneDetailViewModel(milestoneRepository, repository) as T
            modelClass.isAssignableFrom(MonthViewModel::class.java) ->
                MonthViewModel(repository) as T
            modelClass.isAssignableFrom(WeekViewModel::class.java) ->
                WeekViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
