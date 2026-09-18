package com.example.schedulelink.ui.milestone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.flow.FlowScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneDetailScreen(
    milestoneId: String,
    viewModel: MilestoneDetailViewModel,
    onEdit: (goalId: String, milestoneId: String) -> Unit,
    onBack: () -> Unit,
    onAddSchedule: (String) -> Unit,
    onScheduleClick: (String) -> Unit
) {
    val milestone by viewModel.milestone(milestoneId).collectAsState(initial = null)
    val schedules by viewModel.schedules(milestoneId).collectAsState(initial = emptyList())
    val flowRows by viewModel.flowRows(milestoneId).collectAsState(initial = emptyList())
    var showFlow by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(milestone?.title ?: "中日程の詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { milestone?.let { onEdit(it.goalId, milestoneId) } }) {
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddSchedule(milestoneId) }) {
                Icon(Icons.Default.Add, contentDescription = "小日程を追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            milestone?.memo?.takeIf { it.isNotBlank() }?.let {
                Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = !showFlow, onClick = { showFlow = false }, label = { Text("リスト") })
                FilterChip(selected = showFlow, onClick = { showFlow = true }, label = { Text("フロー") })
            }

            if (schedules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        "まだ小日程がありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (showFlow) {
                FlowScreen(
                    rows = flowRows,
                    onItemClick = onScheduleClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleRow(schedule = schedule, onClick = { onScheduleClick(schedule.id) })
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("中日程を削除しますか?") },
            text = { Text("紐づく小日程はリンクが解除されますが削除はされません。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteMilestone(milestoneId) { onBack() }
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun ScheduleRow(schedule: ScheduleEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${schedule.date} ${schedule.startTime}〜${schedule.endTime}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(schedule.title, style = MaterialTheme.typography.titleSmall)
        }
    }
}
