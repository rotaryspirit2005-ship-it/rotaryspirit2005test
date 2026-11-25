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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** 選んだ通知の表示(「1時間前頃」など)。 */
fun reminderLabel(option: ReminderOption?): String = option?.let { "${it.label}頃" } ?: "なし"

/**
 * 通知の選択欄。押すと選択肢が開く。通知以外を初めて選んだとき(Android 13以降)に、
 * 通知の許可を聞く。許可がないときは、その旨と設定を開くボタンを出す。
 */
@Composable
fun ReminderField(option: ReminderOption?, onChange: (ReminderOption?) -> Unit) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(canPostNotifications(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = canPostNotifications(context)
    }
    Column {
        PickerField(
            label = "通知(この端末だけ)",
            value = reminderLabel(option),
            icon = Icons.Outlined.Notifications,
            onClick = { showPicker = true }
        )
        if (option != null && !allowed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "通知がオフのため届きません",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("設定を開く") }
            }
        } else {
            Text(
                "家族には届きません。時刻は数分ずれることがあります",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showPicker) {
        ReminderPickerDialog(
            current = option,
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

@Composable
fun ReminderPickerDialog(current: ReminderOption?, onDismiss: () -> Unit, onPick: (ReminderOption?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知") },
        text = {
            Column {
                (listOf<ReminderOption?>(null) + ReminderOption.entries).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = item == current, role = Role.RadioButton, onClick = { onPick(item) }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = item == current, onClick = null, modifier = Modifier.padding(end = 12.dp))
                        Text(item?.label ?: "なし", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}
