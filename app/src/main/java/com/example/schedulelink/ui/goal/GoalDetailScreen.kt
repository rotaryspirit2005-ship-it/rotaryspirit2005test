package com.example.schedulelink.ui.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.schedulelink.data.GoalType
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: String,
    viewModel: GoalDetailViewModel,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onAddMilestone: (String) -> Unit,
    onMilestoneClick: (String) -> Unit
) {
    val goal by viewModel.goal(goalId).collectAsState(initial = null)
    val milestones by viewModel.milestones(goalId).collectAsState(initial = emptyList())
    val progress by viewModel.progress(goalId).collectAsState(
        initial = com.example.schedulelink.data.GoalProgress(0, 0)
    )
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("大目的の詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(goalId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddMilestone(goalId) }) {
                Icon(Icons.Default.Add, contentDescription = "中日程を追加")
            }
        }
    ) { padding ->
        val current = goal
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                if (current.memo.isNotBlank()) {
                    Text(current.memo, style = MaterialTheme.typography.bodyMedium)
                }
                if (current.type == GoalType.PHASED) {
                    Text(
                        "中日程 ${progress.doneCount}/${progress.totalCount} 完了",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress = progress.ratio,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "継続中の習慣目的",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    "中日程",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (milestones.isEmpty()) {
                    Text(
                        "まだ中日程がありません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    milestones.forEach { milestone ->
                        MilestoneRow(milestone = milestone, onClick = { onMilestoneClick(milestone.id) })
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("大目的を削除しますか?") },
            text = { Text("この操作は取り消せません。中日程もすべて削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteGoal(goalId) { onBack() }
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun MilestoneRow(milestone: MilestoneEntity, onClick: () -> Unit) {
    val (containerColor, contentColor) = when (milestone.status) {
        MilestoneStatus.DONE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        MilestoneStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        MilestoneStatus.UPCOMING -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (milestone.status) {
                MilestoneStatus.DONE -> Icon(Icons.Default.CheckCircle, contentDescription = "完了", tint = contentColor)
                MilestoneStatus.ACTIVE -> Icon(Icons.Default.Loop, contentDescription = "進行中", tint = contentColor)
                MilestoneStatus.UPCOMING -> {}
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(milestone.title, style = MaterialTheme.typography.titleSmall, color = contentColor)
                if (milestone.startDate != null && milestone.endDate != null) {
                    Text(
                        "${milestone.startDate} 〜 ${milestone.endDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}
