package com.example.schedulelink.ui.goal

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
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
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.GoalType
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
    var tagIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val context = LocalContext.current

    // 期間を平行移動して保存するときに、この大目的の中日程・予定も同じ日数ずらすかをダイアログで聞く。
    // 元の日付は「もともと日付がなかった(習慣型)」かどうかも区別するため別に持つ。
    var originalStart by remember { mutableStateOf<LocalDate?>(null) }
    var originalEnd by remember { mutableStateOf<LocalDate?>(null) }
    var shiftDialog by remember { mutableStateOf<List<ShiftDialogGroup>?>(null) }
    var saving by remember { mutableStateOf(false) }
    val milestonesFlow = remember(goalId) {
        if (goalId != null) viewModel.milestonesOfGoal(goalId) else flowOf(emptyList())
    }
    val schedulesFlow = remember(goalId) {
        if (goalId != null) viewModel.schedulesOfGoal(goalId) else flowOf(emptyList())
    }
    val goalMilestones by milestonesFlow.collectAsState(initial = emptyList())
    val goalSchedules by schedulesFlow.collectAsState(initial = emptyList())
    // 期間(開始・終了)が両方ある中日程だけをずらす対象にする。
    val shiftableMilestones = goalMilestones.filter { it.startDate != null && it.endDate != null }
    val milestoneTitleById = goalMilestones.associate { it.id to it.title }
    val dayShift = rangeShiftDays(
        originalStart,
        originalEnd,
        if (type == GoalType.PHASED) startDate else null,
        if (type == GoalType.PHASED) endDate else null
    )
    val canShiftLinked = goalId != null && dayShift != null &&
        (shiftableMilestones.isNotEmpty() || goalSchedules.isNotEmpty())

    LaunchedEffect(goalId) {
        if (goalId != null) {
            viewModel.loadForEdit(goalId) { goal ->
                title = goal.title
                memo = goal.memo
                type = goal.type
                originalStart = goal.startDate
                originalEnd = goal.endDate
                goal.startDate?.let { startDate = it }
                goal.endDate?.let { endDate = it }
                existingPhotoUrls = goal.photoUrls
                tagIds = goal.tagIds
            }
        }
    }

    // 入力を直したら、固定バーに出ている検証エラーを消す(直したのに赤いまま残らないように)。
    LaunchedEffect(title, startDate, endDate, type) { errorMessage = null }

    // 保存の本体。ダイアログの各ボタンと、ダイアログを出さないときの両方から呼ぶ。
    fun save(shiftMilestoneIds: Set<String>, shiftScheduleIds: Set<String>, shiftDays: Long) {
        saving = true
        val goal = GoalEntity(
            id = goalId ?: "",
            title = title.trim(),
            memo = memo.trim(),
            type = type,
            startDate = if (type == GoalType.PHASED) startDate else null,
            endDate = if (type == GoalType.PHASED) endDate else null,
            photoUrls = existingPhotoUrls,
            tagIds = tagIds
        )
        val parts = listOfNotNull(
            if (shiftMilestoneIds.isNotEmpty()) "中日程 ${shiftMilestoneIds.size}件" else null,
            if (shiftScheduleIds.isNotEmpty()) "予定 ${shiftScheduleIds.size}件" else null
        )
        viewModel.save(goal, pendingPhotoUris, removedPhotoUrls, shiftMilestoneIds, shiftScheduleIds, shiftDays) {
            if (shiftDays != 0L && parts.isNotEmpty()) {
                Toast.makeText(
                    context,
                    "${parts.joinToString("・")}の日付を${signedDays(shiftDays)}ずらしました",
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
                    } else if (type == GoalType.PHASED && !endDate.isAfter(startDate)) {
                        errorMessage = "終了日は開始日より後にしてください"
                    } else if (canShiftLinked && dayShift != null) {
                        errorMessage = null
                        // 中日程の期間をずらしても、その中の予定は動かないので両方を並べる
                        shiftDialog = listOf(
                            ShiftDialogGroup("中日程の期間", shiftableMilestones.map { it.toShiftDialogItem(dayShift) }),
                            ShiftDialogGroup(
                                "予定の日付",
                                goalSchedules.map { schedule ->
                                    schedule.toShiftDialogItem(dayShift, schedule.milestoneId?.let { milestoneTitleById[it] })
                                }
                            )
                        ).filter { it.items.isNotEmpty() }
                    } else {
                        errorMessage = null
                        save(emptySet(), emptySet(), 0L)
                    }
        }, errorMessage = errorMessage, enabled = !saving)
        },
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
                .consumeWindowInsets(padding)
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

            FormLabel("種類")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectableChip(
                    selected = type == GoalType.PHASED,
                    onClick = { type = GoalType.PHASED },
                    label = "期間のある目的"
                )
                SelectableChip(
                    selected = type == GoalType.HABIT,
                    onClick = { type = GoalType.HABIT },
                    label = "継続する習慣"
                )
            }

            TagPickerSection(selectedIds = tagIds, onChange = { tagIds = it })

            if (type == GoalType.PHASED) {
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
            title = "中日程・予定もずらしますか？",
            days = days,
            groups = groups,
            onSave = { selected ->
                shiftDialog = null
                val milestoneIndex = groups.indexOfFirst { it.header == "中日程の期間" }
                val scheduleIndex = groups.indexOfFirst { it.header == "予定の日付" }
                save(
                    selected.getOrNull(milestoneIndex).orEmpty(),
                    selected.getOrNull(scheduleIndex).orEmpty(),
                    days
                )
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
