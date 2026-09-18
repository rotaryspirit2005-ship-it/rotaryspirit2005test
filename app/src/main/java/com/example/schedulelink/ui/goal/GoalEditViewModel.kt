package com.example.schedulelink.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GoalEditViewModel(private val repository: GoalRepository) : ViewModel() {

    fun loadForEdit(id: String, onLoaded: (GoalEntity) -> Unit) {
        viewModelScope.launch {
            repository.goalById(id).first()?.let(onLoaded)
        }
    }

    fun save(goal: GoalEntity, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.saveGoal(goal)
            onSaved(id)
        }
    }
}
