package com.example.schedulelink.ui.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MilestoneEditViewModel(private val repository: MilestoneRepository) : ViewModel() {

    fun loadForEdit(id: Long, onLoaded: (MilestoneEntity) -> Unit) {
        viewModelScope.launch {
            repository.milestoneById(id).first()?.let(onLoaded)
        }
    }

    fun save(milestone: MilestoneEntity, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.saveMilestone(milestone)
            onSaved(id)
        }
    }
}
