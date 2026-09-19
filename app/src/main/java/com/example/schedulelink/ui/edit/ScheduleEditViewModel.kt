package com.example.schedulelink.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.PhotoStorageRepository
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ScheduleEditViewModel(
    private val repository: ScheduleRepository,
    private val milestoneRepository: MilestoneRepository,
    private val photoStorageRepository: PhotoStorageRepository
) : ViewModel() {

    val allSchedules: StateFlow<List<ScheduleEntity>> = repository.allSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allMilestones: StateFlow<List<MilestoneEntity>> = milestoneRepository.allMilestones()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadForEdit(id: String, onLoaded: (ScheduleEntity, Set<String>) -> Unit) {
        viewModelScope.launch {
            val schedule = repository.scheduleById(id).first()
            val links = repository.linkedIds(id).first().toSet()
            if (schedule != null) {
                onLoaded(schedule, links)
            }
        }
    }

    fun save(
        schedule: ScheduleEntity,
        linkedIds: Set<String>,
        newPhotoUris: List<Uri>,
        removedPhotoUrls: List<String>,
        onSaved: (String) -> Unit
    ) {
        viewModelScope.launch {
            val idForPath = schedule.id.ifBlank { UUID.randomUUID().toString() }
            val uploadedUrls = newPhotoUris.map { uri -> photoStorageRepository.upload("schedules", idForPath, uri) }
            val id = repository.saveSchedule(schedule.copy(photoUrls = schedule.photoUrls + uploadedUrls), linkedIds)
            removedPhotoUrls.forEach { photoStorageRepository.delete(it) }
            onSaved(id)
        }
    }
}
