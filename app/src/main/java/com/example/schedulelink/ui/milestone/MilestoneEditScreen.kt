package com.example.schedulelink.ui.milestone

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneStatus
import com.example.schedulelink.ui.common.FormLabel
import com.example.schedulelink.ui.common.PhotoAttachmentSection
import com.example.schedulelink.ui.common.PickerField
import com.example.schedulelink.ui.common.SelectableChip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MilestoneEditScreen(
    goalId: String,
    milestoneId: String?,
    viewModel: MilestoneEditViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    isPhotoFeatureEnabled: Boolean
) {
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(MilestoneStatus.UPCOMING) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusWeeks(2)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var existingPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var removedPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(milestoneId) {
        if (milestoneId != null) {
            viewModel.loadForEdit(milestoneId) { milestone ->
                title = milestone.title
                memo = milestone.memo
                status = milestone.status
                milestone.startDate?.let { startDate = it }
                milestone.endDate?.let { endDate = it }
                existingPhotoUrls = milestone.photoUrls
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (milestoneId == null) "中日程を追加" else "中日程を編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("中日程のタイトル") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("メモ") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            FormLabel("状態")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectableChip(
                    selected = status == MilestoneStatus.UPCOMING,
                    onClick = { status = MilestoneStatus.UPCOMING },
                    label = "未着手"
                )
                SelectableChip(
                    selected = status == MilestoneStatus.ACTIVE,
                    onClick = { status = MilestoneStatus.ACTIVE },
                    label = "進行中"
                )
                SelectableChip(
                    selected = status == MilestoneStatus.DONE,
                    onClick = { status = MilestoneStatus.DONE },
                    label = "完了"
                )
            }

            // 「yyyy年M月d日」は半分の幅に収まらず折り返すため、縦に並べる。
            PickerField(
                label = "開始日",
                value = startDate.format(dateFormatter),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { showStartDatePicker = true }
            )
            PickerField(
                label = "終了日",
                value = endDate.format(dateFormatter),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { showEndDatePicker = true }
            )
            if (!endDate.isAfter(startDate)) {
                Text(
                    text = "終了日は開始日より後にしてください",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "タイトルを入力してください"
                    } else if (!endDate.isAfter(startDate)) {
                        errorMessage = "終了日は開始日より後にしてください"
                    } else {
                        errorMessage = null
                        val milestone = MilestoneEntity(
                            id = milestoneId ?: "",
                            goalId = goalId,
                            title = title.trim(),
                            memo = memo.trim(),
                            startDate = startDate,
                            endDate = endDate,
                            status = status,
                            photoUrls = existingPhotoUrls
                        )
                        viewModel.save(milestone, pendingPhotoUris, removedPhotoUrls) { onSaved() }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }

    if (showStartDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        endDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
