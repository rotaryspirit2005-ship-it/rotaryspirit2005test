package com.example.schedulelink.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.TodoEntity
import com.example.schedulelink.ui.common.DestructiveTextButton
import com.example.schedulelink.ui.common.PickerField
import com.example.schedulelink.ui.common.commonTimeFormatter
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.tabularNums
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dueFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)
private val pickerDateFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
private val chipDateFormatter = DateTimeFormatter.ofPattern("M/d", Locale.JAPAN)

/** 未完了は期限の近い順(期限なしは後ろ)→作成順。 */
val openTodoOrder: Comparator<TodoEntity> = compareBy({ it.dueDate ?: LocalDate.MAX }, { it.createdAt })

/** 完了済みは新しく完了したものから。 */
val doneTodoOrder: Comparator<TodoEntity> = compareByDescending { it.completedAt ?: 0L }

/**
 * やること1行。チェックボックスで完了を切り替え、行のそれ以外をタップすると編集を開く。
 * [linkedSchedule]を渡すと、リンク先の予定をチップで示す(タップで予定の詳細へ)。
 */
@Composable
fun TodoRow(
    todo: TodoEntity,
    linkedSchedule: ScheduleEntity?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onScheduleClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val isOverdue = !todo.done && todo.dueDate != null && todo.dueDate.isBefore(today)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = Dimens.ListItemPaddingH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkboxは標準で48dpのタップ領域を持つ。
        Checkbox(checked = todo.done, onCheckedChange = onToggle)
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            val hasDue = todo.dueDate != null && !todo.done
            val showChip = linkedSchedule != null && onScheduleClick != null
            if (hasDue || showChip) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasDue) {
                        Text(
                            text = "${todo.dueDate!!.format(dueFormatter)}まで",
                            style = MaterialTheme.typography.labelMedium.tabularNums(),
                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showChip) {
                        AssistChip(
                            onClick = { onScheduleClick!!(linkedSchedule!!.id) },
                            label = {
                                Text(
                                    "${linkedSchedule!!.date.format(chipDateFormatter)} ${linkedSchedule.title}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

/** 一覧の上などに置く、1行で素早く追加する欄。追加後も入力を続けられるよう、文字だけ消す。 */
@Composable
fun TodoQuickAddField(onAdd: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "やることを追加") {
    var text by remember { mutableStateOf("") }
    val submit = {
        val title = text.trim()
        if (title.isNotEmpty()) {
            onAdd(title)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * やることの編集シート(一覧と予定の詳細で共通)。[schedules]はリンク先の候補。
 * [onDelete]がnullなら削除ボタンを出さない(新規作成時)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditSheet(
    initial: TodoEntity,
    schedules: List<ScheduleEntity>,
    onDismiss: () -> Unit,
    onSave: (TodoEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var memo by remember(initial.id) { mutableStateOf(initial.memo) }
    var dueDate by remember(initial.id) { mutableStateOf(initial.dueDate) }
    var scheduleId by remember(initial.id) { mutableStateOf(initial.scheduleId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSchedulePicker by remember { mutableStateOf(false) }
    val linkedSchedule = schedules.firstOrNull { it.id == scheduleId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.FormGap)
        ) {
            Text(
                if (initial.id.isBlank()) "やることを追加" else "やることを編集",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("やること") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("メモ") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerField(
                    label = "期限",
                    value = dueDate?.format(pickerDateFormatter) ?: "なし",
                    icon = Icons.Default.Event,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
                if (dueDate != null) {
                    TextButton(onClick = { dueDate = null }) { Text("期限なし") }
                }
            }
            PickerField(
                label = "予定とのリンク",
                // リンク先の予定が(別の端末で)削除済みなら「なし」と同じ扱いにする。
                value = linkedSchedule?.let { "${it.date.format(pickerDateFormatter)} ${it.title}" } ?: "なし",
                icon = Icons.Default.Link,
                onClick = { showSchedulePicker = true }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    DestructiveTextButton("削除", onClick = onDelete)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("キャンセル") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            initial.copy(
                                title = title.trim(),
                                memo = memo.trim(),
                                dueDate = dueDate,
                                scheduleId = linkedSchedule?.id
                            )
                        )
                    },
                    enabled = title.isNotBlank()
                ) { Text("保存") }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (dueDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } }
        ) {
            DatePicker(state = state)
        }
    }

    if (showSchedulePicker) {
        SchedulePickerDialog(
            schedules = schedules,
            selectedId = linkedSchedule?.id,
            onSelect = { scheduleId = it; showSchedulePicker = false },
            onDismiss = { showSchedulePicker = false }
        )
    }
}

/** 一週間前〜二か月先の予定を日付ごとに並べ、リンク先を1つ選ぶ(選択中の予定は範囲外でも出す)。 */
@Composable
private fun SchedulePickerDialog(
    schedules: List<ScheduleEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    val candidates = remember(schedules, selectedId) {
        schedules
            .filter { (!it.date.isBefore(today.minusDays(7)) && !it.date.isAfter(today.plusDays(60))) || it.id == selectedId }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .groupBy { it.date }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("リンクする予定") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    PickerRow(label = "リンクしない", selected = selectedId == null, onClick = { onSelect(null) })
                }
                if (candidates.isEmpty()) {
                    item {
                        Text(
                            "前後の期間に予定がありません",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                candidates.forEach { (date, daySchedules) ->
                    item(key = "h:$date") {
                        Text(
                            date.format(pickerDateFormatter),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(daySchedules, key = { it.id }) { schedule ->
                        PickerRow(
                            label = "${schedule.startTime.format(commonTimeFormatter)} ${schedule.title}",
                            selected = schedule.id == selectedId,
                            onClick = { onSelect(schedule.id) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
