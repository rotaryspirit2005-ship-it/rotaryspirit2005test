package com.example.schedulelink.ui.milestone

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneStatus
import com.example.schedulelink.ui.tag.TagPickerSection
import com.example.schedulelink.ui.common.SaveBottomBar
import com.example.schedulelink.ui.common.FormLabel
import com.example.schedulelink.ui.common.PhotoAttachmentSection
import com.example.schedulelink.ui.common.PickerField
import com.example.schedulelink.ui.common.SelectableChip
import com.example.schedulelink.ui.common.ShiftDialogGroup
import com.example.schedulelink.ui.common.ShiftLinkedDialog
import com.example.schedulelink.ui.common.toShiftDialogItem
import com.example.schedulelink.ui.common.rangeShiftDays
import com.example.schedulelink.ui.common.signedDays
import kotlinx.coroutines.flow.flowOf
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
    var tagIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val context = LocalContext.current

    // 期間を平行移動して保存するときに、この中日程の予定も同じ日数ずらすかをダイアログで聞く。元の日付は
    // 「もともと日付がなかった」かどうかも区別するため、画面の初期値とは別に持つ。
    var originalStart by remember { mutableStateOf<LocalDate?>(null) }
    var originalEnd by remember { mutableStateOf<LocalDate?>(null) }
    var shiftDialog by remember { mutableStateOf<List<ShiftDialogGroup>?>(null) }
    var saving by remember { mutableStateOf(false) }
    val schedulesFlow = remember(milestoneId) {
        if (milestoneId != null) viewModel.schedulesOfMilestone(milestoneId) else flowOf(emptyList())
    }
    val milestoneSchedules by schedulesFlow.collectAsState(initial = emptyList())
    val dayShift = rangeShiftDays(originalStart, originalEnd, startDate, endDate)
    val canShiftLinked = milestoneId != null && dayShift != null && milestoneSchedules.isNotEmpty()

    LaunchedEffect(milestoneId) {
        if (milestoneId != null) {
            viewModel.loadForEdit(milestoneId) { milestone ->
                title = milestone.title
                memo = milestone.memo
                status = milestone.status
                originalStart = milestone.startDate
                originalEnd = milestone.endDate
                milestone.startDate?.let { startDate = it }
                milestone.endDate?.let { endDate = it }
                existingPhotoUrls = milestone.photoUrls
                tagIds = milestone.tagIds
            }
        }
    }

    // 入力を直したら、固定バーに出ている検証エラーを消す(直したのに赤いまま残らないように)。
    LaunchedEffect(title, startDate, endDate) { errorMessage = null }

    // 保存の本体。ダイアログの各ボタンと、ダイアログを出さないときの両方から呼ぶ。
    fun save(shiftScheduleIds: Set<String>, shiftDays: Long) {
        saving = true
        val milestone = MilestoneEntity(
            id = milestoneId ?: "",
            goalId = goalId,
            title = title.trim(),
            memo = memo.trim(),
            startDate = startDate,
            endDate = endDate,
            status = status,
            photoUrls = existingPhotoUrls,
            tagIds = tagIds
        )
        viewModel.save(milestone, pendingPhotoUris, removedPhotoUrls, shiftScheduleIds, shiftDays, onError = {
            saving = false
            errorMessage = "保存できませんでした。通信状態を確かめて、もう一度お試しください"
        }) {
            if (shiftDays != 0L && shiftScheduleIds.isNotEmpty()) {
                Toast.makeText(
                    context,
                    "この中日程の予定 ${shiftScheduleIds.size}件の日付を${signedDays(shiftDays)}ずらしました",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onSaved()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            SaveBottomBar(onSave = {
                    if (title.isBlank()) {
                        errorMessage = "タイトルを入力してください"
                    } else if (!endDate.isAfter(startDate)) {
                        errorMessage = "終了日は開始日より後にしてください"
                    } else if (canShiftLinked && dayShift != null) {
                        errorMessage = null
                        shiftDialog = listOf(ShiftDialogGroup(null, milestoneSchedules.map { it.toShiftDialogItem(dayShift) }))
                    } else {
                        errorMessage = null
                        save(emptySet(), 0L)
                    }
        }, errorMessage = errorMessage, enabled = !saving)
        },
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

            TagPickerSection(selectedIds = tagIds, onChange = { tagIds = it })

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

        }
    }

    shiftDialog?.let { groups ->
        val days = dayShift ?: 0L
        ShiftLinkedDialog(
            title = "この中日程の予定もずらしますか？",
            days = days,
            groups = groups,
            onSave = { selected ->
                shiftDialog = null
                save(selected.first(), days)
            },
            onCancel = { shiftDialog = null }
        )
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
