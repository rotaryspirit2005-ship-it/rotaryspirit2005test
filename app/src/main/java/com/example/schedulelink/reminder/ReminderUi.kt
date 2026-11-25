package com.example.schedulelink.reminder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.schedulelink.ui.common.PickerField
import com.example.schedulelink.ui.edit.TimePickerDialog
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val customFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E) H:mm", Locale.JAPAN)

/** 選んだ通知の表示。日時を指定したときは、その予定の日時から求めた通知の日時を出す。 */
fun reminderLabel(reminder: Reminder?, date: LocalDate, startTime: LocalTime): String = when {
    reminder == null -> "なし"
    reminder.option == ReminderOption.Custom -> reminder.triggerAt(date, startTime).format(customFormatter) + "頃"
    else -> "${reminder.option.label}頃"
}

/** 通知の時刻についての注意(過ぎている・予定の開始より後)。問題なければnull。 */
fun reminderWarning(reminder: Reminder?, date: LocalDate, startTime: LocalTime): String? {
    val at = reminder?.triggerAt(date, startTime) ?: return null
    return when {
        at.isBefore(LocalDateTime.now()) -> "通知の時刻が過ぎているため届きません"
        at.isAfter(LocalDateTime.of(date, startTime)) -> "予定の開始より後に通知します"
        else -> null
    }
}

/**
 * 通知の選択欄。押すと選択肢が開く。通知を初めて選んだとき(Android 13以降)に、
 * 通知の許可を聞く。許可がないときは、その旨と設定を開くボタンを出す。
 * [date]・[startTime]は予定の日時(日時を指定するときの初期値と、表示に使う)。
 */
@Composable
fun ReminderField(reminder: Reminder?, date: LocalDate, startTime: LocalTime, onChange: (Reminder?) -> Unit) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(canPostNotifications(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = canPostNotifications(context)
    }
    val warning = reminderWarning(reminder, date, startTime)
    Column {
        PickerField(
            label = "通知(この端末だけ)",
            value = reminderLabel(reminder, date, startTime),
            icon = Icons.Outlined.Notifications,
            onClick = { showPicker = true }
        )
        when {
            reminder != null && !allowed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "通知がオフのため届きません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { openNotificationSettings(context) }) { Text("設定を開く") }
            }
            warning != null -> Text(
                warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            else -> Text(
                "家族には届きません。時刻は数分ずれることがあります",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showPicker) {
        ReminderChooser(
            current = reminder,
            date = date,
            startTime = startTime,
            onDismiss = { showPicker = false },
            onPick = { picked ->
                showPicker = false
                onChange(picked)
                if (picked != null && !canPostNotifications(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }
}

private fun openNotificationSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private enum class ChooserStep { List, Date, Time }

/**
 * 通知の選び方。決まった選択肢か、「日時を指定…」でカレンダーと時計から選ぶ。
 * 指定した日時は、予定の開始の何分前かに直して持つ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderChooser(
    current: Reminder?,
    date: LocalDate,
    startTime: LocalTime,
    onDismiss: () -> Unit,
    onPick: (Reminder?) -> Unit
) {
    var step by remember { mutableStateOf(ChooserStep.List) }
    // 初期値: いまの指定日時、なければ予定の1時間前
    val initial = current?.triggerAt(date, startTime) ?: LocalDateTime.of(date, startTime).minusHours(1)
    var pickedDate by remember { mutableStateOf(initial.toLocalDate()) }
    val start = LocalDateTime.of(date, startTime)

    when (step) {
        ChooserStep.List -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("通知") },
            text = {
                Column {
                    val fixed = listOf<ReminderOption?>(null) + ReminderOption.entries.filter { it != ReminderOption.Custom }
                    fixed.forEach { item ->
                        ChoiceRow(
                            label = item?.label ?: "なし",
                            selected = if (item == null) current == null else current?.option == item,
                            onClick = { onPick(item?.let { Reminder(it) }) }
                        )
                    }
                    val customAt = current?.takeIf { it.option == ReminderOption.Custom }?.triggerAt(date, startTime)
                    ChoiceRow(
                        label = if (customAt != null) "日時を指定: ${customAt.format(customFormatter)}" else "日時を指定…",
                        selected = current?.option == ReminderOption.Custom,
                        onClick = { step = ChooserStep.Date }
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
        )
        ChooserStep.Date -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = pickedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                // 外側を押したり戻ったりしたときは、1つ前(選択肢の一覧)に戻る
                onDismissRequest = { step = ChooserStep.List },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        step = ChooserStep.Time
                    }) { Text("次へ(時刻)") }
                },
                dismissButton = { TextButton(onClick = { step = ChooserStep.List }) { Text("戻る") } }
            ) {
                DatePicker(
                    state = state,
                    title = { Text("通知する日(予定は${date.format(DateTimeFormatter.ofPattern("M月d日", Locale.JAPAN))})", modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) }
                )
            }
        }
        ChooserStep.Time -> {
            val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
            TimePickerDialog(
                title = "通知する時刻",
                state = state,
                onDismiss = { step = ChooserStep.Date },
                onConfirm = {
                    val at = LocalDateTime.of(pickedDate, LocalTime.of(state.hour, state.minute))
                    onPick(Reminder(ReminderOption.Custom, Duration.between(at, start).toMinutes()))
                }
            )
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
