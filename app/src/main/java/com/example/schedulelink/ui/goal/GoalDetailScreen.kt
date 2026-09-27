package com.example.schedulelink.ui.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.GoalProgress
import com.example.schedulelink.data.GoalType
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.MilestoneStatus
import com.example.schedulelink.ui.common.AppFab
import com.example.schedulelink.ui.common.AppProgressBar
import com.example.schedulelink.ui.common.DestructiveTextButton
import com.example.schedulelink.ui.common.InfoPanel
import com.example.schedulelink.ui.common.PhotoGallery
import com.example.schedulelink.ui.common.SectionHeader
import com.example.schedulelink.ui.common.commonDateFormatter
import com.example.schedulelink.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: String,
    viewModel: GoalDetailViewModel,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onAddMilestone: (String) -> Unit,
    onMilestoneClick: (String) -> Unit,
    isPhotoFeatureEnabled: Boolean
) {
    val goal by viewModel.goal(goalId).collectAsState(initial = null)
    val milestones by viewModel.milestones(goalId).collectAsState(initial = emptyList())
    val progress by viewModel.progress(goalId).collectAsState(initial = GoalProgress(0, 0))
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
            AppFab(onClick = { onAddMilestone(goalId) }, icon = Icons.Default.Add, contentDescription = "中日程を追加")
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
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.ListGap)
            ) {
                InfoPanel {
                    Text(current.title, style = MaterialTheme.typography.headlineSmall)
                    if (current.memo.isNotBlank()) {
                        Text(current.memo, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (isPhotoFeatureEnabled) {
                        PhotoGallery(urls = current.photoUrls)
                    }
                    if (current.type == GoalType.PHASED) {
                        Text(
                            "中日程 ${progress.doneCount}/${progress.totalCount} 完了",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AppProgressBar(ratio = progress.ratio)
                    } else {
                        Text(
                            "継続中の習慣目的",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                SectionHeader("中日程")
                if (milestones.isEmpty()) {
                    Text(
                        "まだ中日程がありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    milestones.forEach { milestone ->
                        MilestoneRow(milestone = milestone, onClick = { onMilestoneClick(milestone.id) })
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.FabClearance))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("大目的を削除しますか?") },
            text = { Text("この操作は取り消せません。中日程もすべて削除されます。") },
            confirmButton = {
                DestructiveTextButton("削除") {
                    showDeleteConfirm = false
                    viewModel.deleteGoal(goalId) { onBack() }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun MilestoneRow(milestone: MilestoneEntity, onClick: () -> Unit) {
    val content: @Composable (Color, Color) -> Unit = { textColor, iconColor ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ListItemPaddingH, vertical = Dimens.ListItemPaddingV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val (icon, label) = when (milestone.status) {
                MilestoneStatus.DONE -> Icons.Default.CheckCircle to "完了"
                MilestoneStatus.ACTIVE -> Icons.Default.Loop to "進行中"
                MilestoneStatus.UPCOMING -> Icons.Outlined.RadioButtonUnchecked to "未着手"
            }
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(milestone.title, style = MaterialTheme.typography.titleSmall, color = textColor)
                if (milestone.startDate != null && milestone.endDate != null) {
                    Text(
                        "${milestone.startDate.format(commonDateFormatter)} 〜 ${milestone.endDate.format(commonDateFormatter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor
                    )
                }
            }
        }
    }

    // 状態ごとに面の色で区別する: 進行中は強調色で塗り、完了は一段沈んだ面、未着手は枠線のみ。
    when (milestone.status) {
        MilestoneStatus.ACTIVE -> Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            content(MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        }
        MilestoneStatus.DONE -> Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            content(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary)
        }
        MilestoneStatus.UPCOMING -> OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            content(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.outline)
        }
    }
}
