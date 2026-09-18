package com.example.schedulelink.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ScheduleDetailViewModel(private val repository: ScheduleRepository) : ViewModel() {

    fun schedule(id: String): Flow<ScheduleEntity?> = repository.scheduleById(id)

    fun linkedSchedules(id: String): Flow<List<ScheduleEntity>> = repository.linkedSchedules(id)

    fun deleteSchedule(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
            onDone()
        }
    }
}
