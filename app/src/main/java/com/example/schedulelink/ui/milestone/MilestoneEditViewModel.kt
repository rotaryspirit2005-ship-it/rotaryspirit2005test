package com.example.schedulelink.ui.milestone

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.PhotoStorageRepository
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MilestoneEditViewModel(
    private val repository: MilestoneRepository,
    private val scheduleRepository: ScheduleRepository,
    private val photoStorageRepository: PhotoStorageRepository
) : ViewModel() {

    /** この中日程に属する予定(日付をずらす対象の候補)。 */
    fun schedulesOfMilestone(milestoneId: String): Flow<List<ScheduleEntity>> =
        scheduleRepository.schedulesForMilestone(milestoneId)

    fun loadForEdit(id: String, onLoaded: (MilestoneEntity) -> Unit) {
        viewModelScope.launch {
            repository.milestoneById(id).first()?.let(onLoaded)
        }
    }

    fun save(
        milestone: MilestoneEntity,
        newPhotoUris: List<Uri>,
        removedPhotoUrls: List<String>,
        // この中日程の日付変更に合わせて、同じ日数だけ日付をずらす予定(0日なら何もしない)。
        shiftScheduleIds: Set<String> = emptySet(),
        shiftDays: Long = 0,
        onSaved: (String) -> Unit
    ) {
        viewModelScope.launch {
            // 本体の保存より先にずらす(本体の保存はオフラインだと応答待ちになり、その間に画面を
            // 離れると後のずらし処理が実行されず、予定だけ元の日付に残ってしまうため)。
            if (shiftDays != 0L && shiftScheduleIds.isNotEmpty()) {
                scheduleRepository.shiftSchedulesByDays(shiftScheduleIds, shiftDays)
            }
            val idForPath = milestone.id.ifBlank { UUID.randomUUID().toString() }
            val uploadedUrls = newPhotoUris.map { uri -> photoStorageRepository.upload("milestones", idForPath, uri) }
            val id = repository.saveMilestone(milestone.copy(photoUrls = milestone.photoUrls + uploadedUrls))
            removedPhotoUrls.forEach { photoStorageRepository.delete(it) }
            onSaved(id)
        }
    }
}
