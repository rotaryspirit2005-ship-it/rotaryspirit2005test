package com.example.schedulelink.ui.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

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
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val tree: StateFlow<WholeTree> = source
        .map { (goals, milestones, schedules) -> buildWholeTree(goals, milestones, schedules) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WholeTree(emptyList(), emptyList(), emptyList(), LocalDate.now(), LocalDate.now())
        )

    /** 読み込み前はnull(「まだ大目的がありません」が一瞬見えるのを避けるため)。 */
    val outline: StateFlow<TreeOutline?> = source
        .map { (goals, milestones, schedules) -> buildTreeOutline(goals, milestones, schedules) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
