package com.example.schedulelink.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.TodoEntity
import com.example.schedulelink.data.TodoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodoListState(
    val open: List<TodoEntity>,
    val done: List<TodoEntity>,
    /** リンク先の表示と、編集シートでの予定選びに使う。 */
    val schedulesById: Map<String, ScheduleEntity>
)

class TodoListViewModel(
    private val todoRepository: TodoRepository,
    scheduleRepository: ScheduleRepository
) : ViewModel() {

    /** 読み込み前はnull(「まだありません」が一瞬見えるのを避ける)。 */
    val state: StateFlow<TodoListState?> = combine(
        todoRepository.allTodos(),
        scheduleRepository.allSchedules()
    ) { todos, schedules ->
        TodoListState(
            open = todos.filter { !it.done }.sortedWith(openTodoOrder),
            done = todos.filter { it.done }.sortedWith(doneTodoOrder),
            schedulesById = schedules.associateBy { it.id }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 書き込みは画面を待たせない(オフラインでもFirestoreの購読にはすぐ反映される)。
    // 失敗しても落ちないよう例外は捕まえる。表示は購読している実データに従うので、
    // 拒否された変更は自動的に元に戻って見える。
    fun add(title: String, scheduleId: String? = null) = launchWrite {
        todoRepository.saveTodo(TodoEntity(title = title, scheduleId = scheduleId))
    }

    fun save(todo: TodoEntity) = launchWrite { todoRepository.saveTodo(todo) }

    fun setDone(todo: TodoEntity, done: Boolean) = launchWrite { todoRepository.setDone(todo.id, done) }

    fun delete(id: String) = launchWrite { todoRepository.deleteTodo(id) }

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
