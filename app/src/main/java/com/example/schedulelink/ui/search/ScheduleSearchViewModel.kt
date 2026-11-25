package com.example.schedulelink.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 予定をタイトル・場所・メモでさがす。全予定は購読で受け取り、絞り込みは端末の中で行う(追加の料金や索引はいらない)。 */
class ScheduleSearchViewModel(repository: ScheduleRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** nullは読み込み中。 */
    private val all: StateFlow<List<ScheduleEntity>?> = repository.allSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val loaded: StateFlow<Boolean> = combine(all, _query) { list, _ -> list != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val results: StateFlow<List<ScheduleEntity>> = combine(all, _query) { list, query ->
        val words = query.trim().split(Regex("[\\s　]+")).filter { it.isNotEmpty() }
        if (list == null || words.isEmpty()) {
            emptyList()
        } else {
            // words は全部を含むものだけ(「歯医者 駅前」のように言葉を足して絞れる)
            list.filter { schedule ->
                val text = "${schedule.title} ${schedule.location} ${schedule.memo}"
                words.all { text.contains(it, ignoreCase = true) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }
}
