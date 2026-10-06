package com.example.schedulelink.ui.goal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.PhotoStorageRepository
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class GoalEditViewModel(
    private val repository: GoalRepository,
    private val milestoneRepository: MilestoneRepository,
    private val scheduleRepository: ScheduleRepository,
    private val photoStorageRepository: PhotoStorageRepository
) : ViewModel() {

    /** この大目的の中日程(日付をずらす対象の候補)。 */
    fun milestonesOfGoal(goalId: String): Flow<List<MilestoneEntity>> =
        milestoneRepository.milestonesForGoal(goalId)

    /** この大目的の中日程に属する予定(日付をずらす対象の候補)。 */
    fun schedulesOfGoal(goalId: String): Flow<List<ScheduleEntity>> =
        combine(milestoneRepository.milestonesForGoal(goalId), scheduleRepository.allSchedules()) { milestones, schedules ->
            val milestoneIds = milestones.map { it.id }.toSet()
            schedules.filter { it.milestoneId in milestoneIds }
        }

    fun loadForEdit(id: String, onLoaded: (GoalEntity) -> Unit) {
        viewModelScope.launch {
            repository.goalById(id).first()?.let(onLoaded)
        }
    }

    fun save(
        goal: GoalEntity,
        newPhotoUris: List<Uri>,
        removedPhotoUrls: List<String>,
        // この大目的の日付変更に合わせて、同じ日数だけ日付をずらす中日程・予定(0日なら何もしない)。
        shiftMilestoneIds: Set<String> = emptySet(),
        shiftScheduleIds: Set<String> = emptySet(),
        shiftDays: Long = 0,
        onSaved: (String) -> Unit
    ) {
        viewModelScope.launch {
            // 本体の保存より先にずらす(理由はMilestoneEditViewModelと同じ)。
            if (shiftDays != 0L) {
                if (shiftMilestoneIds.isNotEmpty()) milestoneRepository.shiftMilestonesByDays(shiftMilestoneIds, shiftDays)
                if (shiftScheduleIds.isNotEmpty()) scheduleRepository.shiftSchedulesByDays(shiftScheduleIds, shiftDays)
            }
            val idForPath = goal.id.ifBlank { UUID.randomUUID().toString() }
            val uploadedUrls = newPhotoUris.map { uri -> photoStorageRepository.upload("goals", idForPath, uri) }
            val id = repository.saveGoal(goal.copy(photoUrls = goal.photoUrls + uploadedUrls))
            removedPhotoUrls.forEach { photoStorageRepository.delete(it) }
            onSaved(id)
        }
    }
}
