package com.example.schedulelink.ui.edit

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    state: TimePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(title) },
        text = {
            TimePicker(state = state, modifier = Modifier.padding(8.dp))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { androidx.compose.material3.Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { androidx.compose.material3.Text("キャンセル") }
        }
    )
}
