package com.example.schedulelink.ui.edit

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.common.PhotoAttachmentSection
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    scheduleId: String?,
    initialMilestoneId: String?,
    viewModel: ScheduleEditViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    isPhotoFeatureEnabled: Boolean
) {
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var linkedIds by remember { mutableStateOf(setOf<String>()) }
    var milestoneId by remember { mutableStateOf(initialMilestoneId) }
    var loaded by remember { mutableStateOf(scheduleId == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showMilestonePicker by remember { mutableStateOf(false) }
    var existingPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var removedPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(scheduleId) {
        if (scheduleId != null) {
            viewModel.loadForEdit(scheduleId) { schedule, links ->
                title = schedule.title
                memo = schedule.memo
                date = schedule.date
                startTime = schedule.startTime
                endTime = schedule.endTime
                linkedIds = links
                milestoneId = schedule.milestoneId
                existingPhotoUrls = schedule.photoUrls
                loaded = true
            }
        }
    }

    val allSchedules by viewModel.allSchedules.collectAsState()
    val candidateLinks = allSchedules.filter { it.id != scheduleId }
    val allMilestones by viewModel.allMilestones.collectAsState()
    val selectedMilestoneTitle = allMilestones.firstOrNull { it.id == milestoneId }?.title ?: "なし"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scheduleId == null) "予定を追加" else "予定を編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("メモ") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("日付: ${date.format(dateFormatter)}")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("開始: $startTime")
                    }
                    OutlinedButton(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("終了: $endTime")
                    }
                }

                OutlinedButton(onClick = { showMilestonePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("所属する中日程: $selectedMilestoneTitle")
                }

                if (isPhotoFeatureEnabled) {
                    PhotoAttachmentSection(
                        existingUrls = existingPhotoUrls,
                        pendingUris = pendingPhotoUris,
                        onAdd = { uris -> pendingPhotoUris = pendingPhotoUris + uris },
                        onRemoveExisting = { url ->
                            existingPhotoUrls = existingPhotoUrls - url
                            removedPhotoUrls = removedPhotoUrls + url
                        },
                        onRemovePending = { uri -> pendingPhotoUris = pendingPhotoUris - uri }
                    )
                }

                Text(
                    text = "関連する行動予定",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (candidateLinks.isEmpty()) {
                    Text(
                        text = "リンクできる予定がありません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        candidateLinks.forEach { candidate ->
                            val checked = candidate.id in linkedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        linkedIds = if (checked) linkedIds - candidate.id else linkedIds + candidate.id
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        linkedIds = if (isChecked) linkedIds + candidate.id else linkedIds - candidate.id
                                    }
                                )
                                Column {
                                    Text(candidate.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "${candidate.date.format(dateFormatter)} ${candidate.startTime}〜${candidate.endTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = {
                        when {
                            title.isBlank() -> errorMessage = "タイトルを入力してください"
                            !endTime.isAfter(startTime) -> errorMessage = "終了時刻は開始時刻より後にしてください"
                            else -> {
                                errorMessage = null
                                val schedule = ScheduleEntity(
                                    id = scheduleId ?: "",
                                    title = title.trim(),
                                    memo = memo.trim(),
                                    date = date,
                                    startTime = startTime,
                                    endTime = endTime,
                                    milestoneId = milestoneId,
                                    photoUrls = existingPhotoUrls
                                )
                                viewModel.save(schedule, linkedIds, pendingPhotoUris, removedPhotoUrls) { onSaved() }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val state = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = true
        )
        TimePickerDialog(
            title = "開始時刻",
            state = state,
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                startTime = LocalTime.of(state.hour, state.minute)
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        val state = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = true
        )
        TimePickerDialog(
            title = "終了時刻",
            state = state,
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endTime = LocalTime.of(state.hour, state.minute)
                showEndTimePicker = false
            }
        )
    }

    if (showMilestonePicker) {
        AlertDialog(
            onDismissRequest = { showMilestonePicker = false },
            title = { Text("所属する中日程を選択") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                milestoneId = null
                                showMilestonePicker = false
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = milestoneId == null, onClick = {
                            milestoneId = null
                            showMilestonePicker = false
                        })
                        Text("なし")
                    }
                    allMilestones.forEach { milestone ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    milestoneId = milestone.id
                                    showMilestonePicker = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = milestoneId == milestone.id, onClick = {
                                milestoneId = milestone.id
                                showMilestonePicker = false
                            })
                            Text(milestone.title)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMilestonePicker = false }) { Text("閉じる") }
            }
        )
    }
}
