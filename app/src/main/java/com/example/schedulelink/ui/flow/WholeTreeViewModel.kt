package com.example.schedulelink.ui.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

class WholeTreeViewModel(
    goalRepository: GoalRepository,
    milestoneRepository: MilestoneRepository,
    scheduleRepository: ScheduleRepository
) : ViewModel() {

    // ツリー表示とタイムライン表示で同じ購読を使い回す(Firestoreの購読を二重にしない)。
    private val source = combine(
        goalRepository.allGoals(),
        milestoneRepository.allMilestones(),
        scheduleRepository.allSchedules()
    ) { goals, milestones, schedules -> Triple(goals, milestones, schedules) }
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** タイムライン表示の元データ(配置は画面側で、ズームの段階に合わせて計算する)。読み込み前はnull。 */
    val timelineData: StateFlow<TimelineData?> = source
        .map { (goals, milestones, schedules) -> TimelineData(goals, milestones, schedules) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 読み込み前はnull(「まだ大目的がありません」が一瞬見えるのを避けるため)。 */
    val outline: StateFlow<TreeOutline?> = source
        .map { (goals, milestones, schedules) -> buildTreeOutline(goals, milestones, schedules) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class TimelineData(
    val goals: List<GoalEntity>,
    val milestones: List<MilestoneEntity>,
    val schedules: List<ScheduleEntity>
) {
    val isEmpty: Boolean get() = goals.isEmpty() && schedules.isEmpty()
}
