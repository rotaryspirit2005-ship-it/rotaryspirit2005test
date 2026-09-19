package com.example.schedulelink.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.common.PhotoGallery
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDetailScreen(
    scheduleId: String,
    viewModel: ScheduleDetailViewModel,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onLinkedClick: (String) -> Unit
) {
    val schedule by viewModel.schedule(scheduleId).collectAsState(initial = null)
    val linked by viewModel.linkedSchedules(scheduleId).collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("予定の詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(scheduleId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            )
        }
    ) { padding ->
        val current = schedule
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "読み込み中...",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "${current.date.format(dateFormatter)}  ${current.startTime} 〜 ${current.endTime}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (current.memo.isNotBlank()) {
                    Text(current.memo, style = MaterialTheme.typography.bodyMedium)
                }
                PhotoGallery(urls = current.photoUrls)

                Text(
                    text = "関連する行動予定 (${linked.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )

                if (linked.isEmpty()) {
                    Text(
                        text = "リンクされた予定はありません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    linked.forEach { l ->
                        LinkedScheduleRow(l, onClick = { onLinkedClick(l.id) })
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("予定を削除しますか?") },
            text = { Text("この操作は取り消せません。関連するリンクも削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSchedule(scheduleId) { onBack() }
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun LinkedScheduleRow(schedule: ScheduleEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(schedule.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${schedule.date.format(dateFormatter)}  ${schedule.startTime} 〜 ${schedule.endTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
