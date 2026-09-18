package com.example.schedulelink.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class WeekViewModel(private val repository: ScheduleRepository) : ViewModel() {

    private val _weekStart = MutableStateFlow(LocalDate.now().startOfWeek())
    val weekStart: StateFlow<LocalDate> = _weekStart.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val schedulesByDate: StateFlow<Map<LocalDate, List<ScheduleEntity>>> = _weekStart
        .flatMapLatest { start ->
            repository.schedulesInRange(start, start.plusDays(6))
                .map { list -> list.groupBy { it.date } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 月表示から特定の日付をタップ/ピンチした直後に、その日を含む週へ合わせる。 */
    fun initWeek(date: LocalDate) {
        val start = date.startOfWeek()
        if (start != _weekStart.value) {
            _weekStart.value = start
        }
    }

    fun goToPreviousWeek() {
        _weekStart.value = _weekStart.value.minusWeeks(1)
    }

    fun goToNextWeek() {
        _weekStart.value = _weekStart.value.plusWeeks(1)
    }

    fun goToToday() {
        _weekStart.value = LocalDate.now().startOfWeek()
    }
}

/** 日曜始まりの週の開始日を返す。 */
private fun LocalDate.startOfWeek(): LocalDate {
    val offset = dayOfWeek.value % 7 // SUNDAY(7)->0, MONDAY(1)->1, ..., SATURDAY(6)->6
    return minusDays(offset.toLong())
}
