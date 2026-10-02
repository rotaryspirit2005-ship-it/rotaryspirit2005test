package com.example.schedulelink.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.common.DestructiveTextButton
import com.example.schedulelink.ui.common.InfoPanel
import com.example.schedulelink.ui.common.PhotoGallery
import com.example.schedulelink.ui.common.SectionHeader
import com.example.schedulelink.ui.common.commonTimeFormatter
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.tabularNums
import com.example.schedulelink.ui.todo.TodoEditSheet
import com.example.schedulelink.ui.todo.TodoQuickAddField
import com.example.schedulelink.ui.todo.TodoRow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleDetailScreen(
    scheduleId: String,
    viewModel: ScheduleDetailViewModel,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onLinkedClick: (String) -> Unit,
    isPhotoFeatureEnabled: Boolean
) {
    // 再描画のたびに購読し直さないよう、Flowは予定ごとに1度だけ作る。
    val scheduleFlow = remember(scheduleId) { viewModel.schedule(scheduleId) }
    val linkedFlow = remember(scheduleId) { viewModel.linkedSchedules(scheduleId) }
    val schedule by scheduleFlow.collectAsState(initial = null)
    val linked by linkedFlow.collectAsState(initial = emptyList())
    val todosFlow = remember(scheduleId) { viewModel.todos(scheduleId) }
    val todos by todosFlow.collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("予定の詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(scheduleId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            )
        }
    ) { padding ->
        val current = schedule
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "読み込み中...",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // やることの追加欄に入力するとき、キーボードで隠れないようにする。
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.ListGap)
            ) {
                InfoPanel {
                    Text(current.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${current.date.format(dateFormatter)}  " +
                            "${current.startTime.format(commonTimeFormatter)} 〜 ${current.endTime.format(commonTimeFormatter)}",
                        style = MaterialTheme.typography.bodyLarge.tabularNums(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (current.memo.isNotBlank()) {
                        Text(current.memo, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (isPhotoFeatureEnabled) {
                        PhotoGallery(urls = current.photoUrls)
                    }
                }

                // やること: この予定に向けて準備すること。チェックはその場で切り替えられる。
                val openCount = todos.count { !it.done }
                SectionHeader(if (todos.isEmpty()) "やること" else "やること (未完了 $openCount / ${todos.size})")
                if (todos.isEmpty()) {
                    Text(
                        text = "やることはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column {
                        todos.forEach { todo ->
                            key(todo.id) {
                                TodoRow(
                                    todo = todo,
                                    linkedSchedule = null,
                                    onToggle = { viewModel.setTodoDone(todo, it) },
                                    onClick = { editingTodoId = todo.id },
                                    onScheduleClick = null
                                )
                            }
                        }
                    }
                }
                TodoQuickAddField(
                    onAdd = { viewModel.addTodo(it, scheduleId) },
                    placeholder = "この予定のやることを追加"
                )

                SectionHeader("関連する行動予定 (${linked.size})")

                if (linked.isEmpty()) {
                    Text(
                        text = "リンクされた予定はありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    linked.forEach { l ->
                        LinkedScheduleRow(l, onClick = { onLinkedClick(l.id) })
                    }
                }
            }
        }
    }

    val editingTodo = editingTodoId?.let { id -> todos.firstOrNull { it.id == id } }
    if (editingTodo != null) {
        // リンク先の候補(予定一覧)は、シートを開いている間だけ購読する。
        val schedulesFlow = remember { viewModel.allSchedules() }
        val schedules by schedulesFlow.collectAsState(initial = emptyList())
        TodoEditSheet(
            initial = editingTodo,
            // 読み込み前でも「なし」と表示されないよう、今の予定を候補に含めておく。
            schedules = schedules.ifEmpty { listOfNotNull(schedule) },
            onDismiss = { editingTodoId = null },
            onSave = { viewModel.saveTodo(it); editingTodoId = null },
            onDelete = {
                val deleted = editingTodo
                viewModel.deleteTodo(deleted.id)
                editingTodoId = null
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    // 操作ボタン付きのスナックバーは既定では自動で消えないため、表示時間を指定する。
                    val result = snackbarHostState.showSnackbar(
                        "やることを削除しました",
                        actionLabel = "元に戻す",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.saveTodo(deleted)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("予定を削除しますか?") },
            text = { Text("この操作は取り消せません。関連するリンクも削除されます(リンクされたやることは残ります)。") },
            confirmButton = {
                DestructiveTextButton("削除") {
                    showDeleteConfirm = false
                    viewModel.deleteSchedule(scheduleId) { onBack() }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun LinkedScheduleRow(schedule: ScheduleEntity, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.ListItemPaddingH, vertical = Dimens.ListItemPaddingV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(schedule.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${schedule.date.format(dateFormatter)}  " +
                        "${schedule.startTime.format(commonTimeFormatter)} 〜 ${schedule.endTime.format(commonTimeFormatter)}",
                    style = MaterialTheme.typography.bodySmall.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
