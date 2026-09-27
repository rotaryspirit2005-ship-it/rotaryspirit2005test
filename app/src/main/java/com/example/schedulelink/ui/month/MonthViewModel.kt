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
import kotlinx.coroutines.flow.onEach
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

    /** 表示中の月と前後1か月分。スワイプ中に見えている隣の月にも予定の点を出すため。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val schedulesByDate: StateFlow<Map<LocalDate, List<ScheduleEntity>>> = _currentMonth
        .flatMapLatest { month ->
            repository.schedulesInRange(month.minusMonths(1).atDay(1), month.plusMonths(1).atEndOfMonth())
                .map { list -> list.groupBy { it.date } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 直近に予定を読み込み終えた日付。同じ日の再購読(他の画面から戻ったとき)で一覧を消さないために使う。 */
    private var lastLoadedDate: LocalDate? = null

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
                .onEach { lastLoadedDate = date }
                .onStart { if (lastLoadedDate != date) emit(DaySchedules(date, emptyList(), loaded = false)) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DaySchedules(LocalDate.now(), emptyList(), loaded = false)
        )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * 月表示のページ送りが止まったときに呼ぶ。選択日がその月の外なら、
     * 今月なら今日・それ以外は1日を選び直す。
     */
    fun showMonth(month: YearMonth) {
        if (_currentMonth.value == month) return
        _currentMonth.value = month
        if (YearMonth.from(_selectedDate.value) != month) {
            val today = LocalDate.now()
            _selectedDate.value = if (YearMonth.from(today) == month) today else month.atDay(1)
        }
    }
}
