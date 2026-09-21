package com.example.schedulelink.ui.goal

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalType
import com.example.schedulelink.ui.common.PhotoAttachmentSection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(
    goalId: String?,
    viewModel: GoalEditViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    isPhotoFeatureEnabled: Boolean
) {
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GoalType.PHASED) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var existingPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var removedPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(goalId) {
        if (goalId != null) {
            viewModel.loadForEdit(goalId) { goal ->
                title = goal.title
                memo = goal.memo
                type = goal.type
                goal.startDate?.let { startDate = it }
                goal.endDate?.let { endDate = it }
                existingPhotoUrls = goal.photoUrls
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (goalId == null) "大目的を追加" else "大目的を編集") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("目的のタイトル") },
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

            Text("種類", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == GoalType.PHASED,
                    onClick = { type = GoalType.PHASED },
                    label = { Text("期間のある目的") }
                )
                FilterChip(
                    selected = type == GoalType.HABIT,
                    onClick = { type = GoalType.HABIT },
                    label = { Text("継続する習慣") }
                )
            }

            if (type == GoalType.PHASED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("開始: ${startDate.format(dateFormatter)}")
                    }
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("終了: ${endDate.format(dateFormatter)}")
                    }
                }
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
                    } else if (type == GoalType.PHASED && !endDate.isAfter(startDate)) {
                        errorMessage = "終了日は開始日より後にしてください"
                    } else {
                        errorMessage = null
                        val goal = GoalEntity(
                            id = goalId ?: "",
                            title = title.trim(),
                            memo = memo.trim(),
                            type = type,
                            startDate = if (type == GoalType.PHASED) startDate else null,
                            endDate = if (type == GoalType.PHASED) endDate else null,
                            photoUrls = existingPhotoUrls
                        )
                        viewModel.save(goal, pendingPhotoUris, removedPhotoUrls) { onSaved() }
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
