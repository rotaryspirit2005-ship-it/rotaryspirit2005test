package com.example.schedulelink.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.ScheduleWithLinks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class DaySchedules(
    val date: LocalDate,
    val items: List<ScheduleWithLinks>,
    val loaded: Boolean
)

class MonthViewModel(private val repository: ScheduleRepository) : ViewModel() {

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    /** カレンダーの下に予定を表示する、選択中の日付。 */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val schedulesByDate: StateFlow<Map<LocalDate, List<ScheduleEntity>>> = _currentMonth
        .flatMapLatest { month ->
            repository.schedulesInRange(month.atDay(1), month.atEndOfMonth())
                .map { list -> list.groupBy { it.date } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 選択日とその日の予定を必ず組で流す。日付だけ先に切り替わって、新しい日付の見出しの下に
     * 前の日の予定が一瞬残る(Firestoreの応答待ちの間)のを防ぐため、切り替え直後は
     * loaded=false の空の状態を先に出す。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDay: StateFlow<DaySchedules> = _selectedDate
        .flatMapLatest { date ->
            repository.schedulesForDateWithLinks(date)
                .map { DaySchedules(date, it, loaded = true) }
                .onStart { emit(DaySchedules(date, emptyList(), loaded = false)) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DaySchedules(LocalDate.now(), emptyList(), loaded = false)
        )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun goToPreviousMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
        _selectedDate.value = _currentMonth.value.atDay(1)
    }

    fun goToNextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
        _selectedDate.value = _currentMonth.value.atDay(1)
    }

    fun goToToday() {
        _currentMonth.value = YearMonth.now()
        _selectedDate.value = LocalDate.now()
    }
}
