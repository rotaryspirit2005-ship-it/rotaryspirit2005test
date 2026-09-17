package com.example.schedulelink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.ui.detail.ScheduleDetailViewModel
import com.example.schedulelink.ui.edit.ScheduleEditViewModel
import com.example.schedulelink.ui.list.ScheduleListViewModel

class AppViewModelFactory(private val repository: ScheduleRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ScheduleListViewModel::class.java) ->
                ScheduleListViewModel(repository) as T
            modelClass.isAssignableFrom(ScheduleDetailViewModel::class.java) ->
                ScheduleDetailViewModel(repository) as T
            modelClass.isAssignableFrom(ScheduleEditViewModel::class.java) ->
                ScheduleEditViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
