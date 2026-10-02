package com.example.schedulelink.ui.todo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.TodoEntity
import com.example.schedulelink.ui.common.EmptyState
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.Motion

private val todoAppearSpec = tween<Float>(Motion.DurationMedium)
private val todoDisappearSpec = tween<Float>(Motion.DurationShort)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    viewModel: TodoListViewModel,
    onScheduleClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showDone by rememberSaveable { mutableStateOf(false) }
    // 編集中のやることのID(新規作成はここでは行わない。追加は上の入力欄から)。
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("やること") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        val current = state ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Dimens.ScreenPadding)
        ) {
            item(key = "add") {
                TodoQuickAddField(
                    onAdd = { viewModel.add(it) },
                    modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp)
                )
            }
            if (current.open.isEmpty() && current.done.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.TaskAlt,
                        message = "やることはまだありません\n上の欄から追加できます",
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                    )
                }
            }
            items(current.open, key = { it.id }) { todo ->
                TodoListRow(todo, current, viewModel, onScheduleClick, onEdit = { editingId = todo.id })
            }
            if (current.done.isNotEmpty()) {
                item(key = "doneHeader") {
                    DoneHeader(
                        count = current.done.size,
                        expanded = showDone,
                        onToggle = { showDone = !showDone },
                        modifier = Modifier.animateItem(
                            fadeInSpec = todoAppearSpec,
                            placementSpec = tween(Motion.DurationLong, easing = Motion.Emphasized),
                            fadeOutSpec = todoDisappearSpec
                        )
                    )
                }
                if (showDone) {
                    items(current.done, key = { it.id }) { todo ->
                        TodoListRow(todo, current, viewModel, onScheduleClick, onEdit = { editingId = todo.id })
                    }
                }
            }
        }

        val editing = editingId?.let { id -> (current.open + current.done).firstOrNull { it.id == id } }
        if (editing != null) {
            TodoEditSheet(
                initial = editing,
                schedules = current.schedulesById.values.toList(),
                onDismiss = { editingId = null },
                onSave = { viewModel.save(it); editingId = null },
                onDelete = { viewModel.delete(editing.id); editingId = null }
            )
        }
    }
}

@Composable
private fun LazyItemScope.TodoListRow(
    todo: TodoEntity,
    state: TodoListState,
    viewModel: TodoListViewModel,
    onScheduleClick: (String) -> Unit,
    onEdit: () -> Unit
) {
    TodoRow(
        todo = todo,
        // リンク先の予定が削除済みなら(別の端末で消された場合など)リンクなしとして表示する。
        linkedSchedule = todo.scheduleId?.let { state.schedulesById[it] },
        onToggle = { viewModel.setDone(todo, it) },
        onClick = onEdit,
        onScheduleClick = onScheduleClick,
        // 完了にすると、取り消し線のまま下の「完了済み」へ滑らかに移る。
        modifier = Modifier.animateItem(
            fadeInSpec = todoAppearSpec,
            placementSpec = tween(Motion.DurationLong, easing = Motion.Emphasized),
            fadeOutSpec = todoDisappearSpec
        )
    )
}

@Composable
private fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(Motion.DurationMedium, easing = Motion.Standard),
        label = "doneChevron"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = if (expanded) "完了済みを閉じる" else "完了済みを開く", onClick = onToggle)
            .padding(horizontal = Dimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "完了済み ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation)
        )
    }
}
