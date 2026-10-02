package com.example.schedulelink.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.TodoEntity
import com.example.schedulelink.data.TodoRepository
import com.example.schedulelink.ui.todo.doneTodoOrder
import com.example.schedulelink.ui.todo.openTodoOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ScheduleDetailViewModel(
    private val repository: ScheduleRepository,
    private val todoRepository: TodoRepository
) : ViewModel() {

    fun schedule(id: String): Flow<ScheduleEntity?> = repository.scheduleById(id)

    fun linkedSchedules(id: String): Flow<List<ScheduleEntity>> = repository.linkedSchedules(id)

    /** この予定にリンクされたやること(未完了→完了済みの順)。 */
    fun todos(scheduleId: String): Flow<List<TodoEntity>> = todoRepository.todosForSchedule(scheduleId).map { todos ->
        todos.filter { !it.done }.sortedWith(openTodoOrder) + todos.filter { it.done }.sortedWith(doneTodoOrder)
    }

    /** やることの編集シートでリンク先を選び直すための候補(シートを開いている間だけ購読する)。 */
    fun allSchedules(): Flow<List<ScheduleEntity>> = repository.allSchedules()

    fun deleteSchedule(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            // リンクしていたやることは、リンクだけ外れて残る(ScheduleRepository側で処理)。
            repository.deleteSchedule(id)
            onDone()
        }
    }

    // やることの書き込みは画面を待たせない。失敗しても落ちないよう例外は捕まえる。
    fun addTodo(title: String, scheduleId: String) = launchWrite {
        todoRepository.saveTodo(TodoEntity(title = title, scheduleId = scheduleId))
    }

    fun saveTodo(todo: TodoEntity) = launchWrite { todoRepository.saveTodo(todo) }

    fun setTodoDone(todo: TodoEntity, done: Boolean) = launchWrite { todoRepository.setDone(todo.id, done) }

    fun deleteTodo(id: String) = launchWrite { todoRepository.deleteTodo(id) }

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
