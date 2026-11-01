package com.example.schedulelink.ui.edit

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.help.HelpAction
import com.example.schedulelink.ui.help.HelpTopic
import com.example.schedulelink.ui.tag.TagPickerSection
import com.example.schedulelink.ui.common.SaveBottomBar
import com.example.schedulelink.ui.common.PhotoAttachmentSection
import com.example.schedulelink.ui.common.PickerField
import com.example.schedulelink.ui.common.SectionHeader
import com.example.schedulelink.ui.common.ShiftDialogGroup
import com.example.schedulelink.ui.common.ShiftLinkedDialog
import com.example.schedulelink.ui.common.toShiftDialogItem
import com.example.schedulelink.ui.common.signedDays
import com.example.schedulelink.ui.common.commonTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditScreen(
    scheduleId: String?,
    initialMilestoneId: String?,
    // MonthScreenで選択中の日付から直接この画面を開いたときに、日付欄をあらかじめ
    // その日にしておくための引数。新規作成時のみ使う(編集時は既存の日付を使う)。
    initialDate: LocalDate? = null,
    viewModel: ScheduleEditViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    isPhotoFeatureEnabled: Boolean
) {
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate ?: LocalDate.now()) }
    // 編集を始めた時点の日付。ここからの日数の差を、リンクした予定をずらす日数にする。
    var originalDate by remember { mutableStateOf<LocalDate?>(null) }
    // 保存時に「リンクした予定もずらすか」を聞くダイアログの中身(開いた時点の一覧で固定)。nullなら閉じている
    var shiftDialog by remember { mutableStateOf<List<ShiftDialogGroup>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var linkedIds by remember { mutableStateOf(setOf<String>()) }
    var milestoneId by remember { mutableStateOf(initialMilestoneId) }
    var loaded by remember { mutableStateOf(scheduleId == null) }
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showMilestonePicker by remember { mutableStateOf(false) }
    var existingPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var removedPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(scheduleId) {
        if (scheduleId != null) {
            viewModel.loadForEdit(scheduleId) { schedule, links ->
                title = schedule.title
                memo = schedule.memo
                location = schedule.location
                date = schedule.date
                originalDate = schedule.date
                startTime = schedule.startTime
                endTime = schedule.endTime
                linkedIds = links
                milestoneId = schedule.milestoneId
                existingPhotoUrls = schedule.photoUrls
                tagIds = schedule.tagIds
                loaded = true
            }
        }
    }

    val allSchedules by viewModel.allSchedules.collectAsState()
    val candidateLinks = allSchedules.filter { it.id != scheduleId }
    val allMilestones by viewModel.allMilestones.collectAsState()

    // 編集時に日付を変え、リンクした予定(いまチェックが入っている予定)があれば、保存時に
    // 同じ日数ずらすかをダイアログで聞く。
    val dayShift = originalDate?.let { ChronoUnit.DAYS.between(it, date) } ?: 0L
    val shiftTargets = candidateLinks.filter { it.id in linkedIds }
    val canShiftLinked = scheduleId != null && dayShift != 0L && shiftTargets.isNotEmpty()
    val selectedMilestoneTitle = allMilestones.firstOrNull { it.id == milestoneId }?.title ?: "なし"

    // 入力を直したら、固定バーに出ている検証エラーを消す(直したのに赤いまま残らないように)。
    LaunchedEffect(title, date, startTime, endTime) { errorMessage = null }

    // 保存の本体。ダイアログの各ボタンと、ダイアログを出さないときの両方から呼ぶ。
    fun save(shiftIds: Set<String>, shiftDays: Long) {
        saving = true
        val schedule = ScheduleEntity(
            id = scheduleId ?: "",
            title = title.trim(),
            memo = memo.trim(),
            location = location.trim(),
            date = date,
            startTime = startTime,
            endTime = endTime,
            milestoneId = milestoneId,
            photoUrls = existingPhotoUrls,
            tagIds = tagIds
        )
        viewModel.save(schedule, linkedIds, pendingPhotoUris, removedPhotoUrls, shiftIds, shiftDays, onError = {
            saving = false
            errorMessage = "保存できませんでした。通信状態を確かめて、もう一度お試しください"
        }) {
            if (shiftDays != 0L && shiftIds.isNotEmpty()) {
                Toast.makeText(
                    context,
                    "リンクした予定 ${shiftIds.size}件の日付を${signedDays(shiftDays)}ずらしました",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onSaved()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            if (loaded) {
            SaveBottomBar(onSave = {
                        when {
                            title.isBlank() -> errorMessage = "タイトルを入力してください"
                            !endTime.isAfter(startTime) -> errorMessage = "終了時刻は開始時刻より後にしてください"
                            canShiftLinked -> {
                                errorMessage = null
                                shiftDialog = listOf(ShiftDialogGroup(null, shiftTargets.map { it.toShiftDialogItem(dayShift) }))
                            }
                            else -> {
                                errorMessage = null
                                save(emptySet(), 0L)
                            }
                        }
        }, errorMessage = errorMessage, enabled = !saving)
        }
        },
        topBar = {
            TopAppBar(
                actions = { HelpAction(HelpTopic.ScheduleEdit) },
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
                    .consumeWindowInsets(padding)
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
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("場所(どこで)") },
                    leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
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

                TagPickerSection(selectedIds = tagIds, onChange = { tagIds = it })

                PickerField(
                    label = "日付",
                    value = date.format(dateFormatter),
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { showDatePicker = true }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PickerField(
                        label = "開始",
                        value = startTime.format(commonTimeFormatter),
                        icon = Icons.Outlined.Schedule,
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    PickerField(
                        label = "終了",
                        value = endTime.format(commonTimeFormatter),
                        icon = Icons.Outlined.Schedule,
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!endTime.isAfter(startTime)) {
                    Text(
                        text = "終了時刻は開始時刻より後にしてください",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                PickerField(
                    label = "所属する中日程",
                    value = selectedMilestoneTitle,
                    icon = Icons.Outlined.Flag,
                    onClick = { showMilestonePicker = true }
                )

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

                SectionHeader("関連する行動予定")

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
                                    // 日付を変えて保存するときは、ずらすかどうかを保存時のダイアログで選ぶ。
                                    Text(
                                        text = "${candidate.date.format(dateFormatter)} " +
                                            "${candidate.startTime.format(commonTimeFormatter)}〜${candidate.endTime.format(commonTimeFormatter)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    shiftDialog?.let { groups ->
        ShiftLinkedDialog(
            title = "リンクした予定もずらしますか？",
            days = dayShift,
            groups = groups,
            onSave = { selected ->
                shiftDialog = null
                save(selected.first(), dayShift)
            },
            onCancel = { shiftDialog = null }
        )
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
