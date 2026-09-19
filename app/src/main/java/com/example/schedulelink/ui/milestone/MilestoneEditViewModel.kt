package com.example.schedulelink.ui.milestone

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.PhotoStorageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MilestoneEditViewModel(
    private val repository: MilestoneRepository,
    private val photoStorageRepository: PhotoStorageRepository
) : ViewModel() {

    fun loadForEdit(id: String, onLoaded: (MilestoneEntity) -> Unit) {
        viewModelScope.launch {
            repository.milestoneById(id).first()?.let(onLoaded)
        }
    }

    fun save(
        milestone: MilestoneEntity,
        newPhotoUris: List<Uri>,
        removedPhotoUrls: List<String>,
        onSaved: (String) -> Unit
    ) {
        viewModelScope.launch {
            val idForPath = milestone.id.ifBlank { UUID.randomUUID().toString() }
            val uploadedUrls = newPhotoUris.map { uri -> photoStorageRepository.upload("milestones", idForPath, uri) }
            val id = repository.saveMilestone(milestone.copy(photoUrls = milestone.photoUrls + uploadedUrls))
            removedPhotoUrls.forEach { photoStorageRepository.delete(it) }
            onSaved(id)
        }
    }
}
