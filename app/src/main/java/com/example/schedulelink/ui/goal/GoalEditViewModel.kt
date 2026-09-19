package com.example.schedulelink.ui.goal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.PhotoStorageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class GoalEditViewModel(
    private val repository: GoalRepository,
    private val photoStorageRepository: PhotoStorageRepository
) : ViewModel() {

    fun loadForEdit(id: String, onLoaded: (GoalEntity) -> Unit) {
        viewModelScope.launch {
            repository.goalById(id).first()?.let(onLoaded)
        }
    }

    fun save(
        goal: GoalEntity,
        newPhotoUris: List<Uri>,
        removedPhotoUrls: List<String>,
        onSaved: (String) -> Unit
    ) {
        viewModelScope.launch {
            val idForPath = goal.id.ifBlank { UUID.randomUUID().toString() }
            val uploadedUrls = newPhotoUris.map { uri -> photoStorageRepository.upload("goals", idForPath, uri) }
            val id = repository.saveGoal(goal.copy(photoUrls = goal.photoUrls + uploadedUrls))
            removedPhotoUrls.forEach { photoStorageRepository.delete(it) }
            onSaved(id)
        }
    }
}
