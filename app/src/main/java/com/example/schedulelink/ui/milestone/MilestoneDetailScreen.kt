package com.example.schedulelink.ui.milestone

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.common.AppFab
import com.example.schedulelink.ui.common.DestructiveTextButton
import com.example.schedulelink.ui.common.EmptyState
import com.example.schedulelink.ui.common.InfoPanel
import com.example.schedulelink.ui.common.PhotoGallery
import com.example.schedulelink.ui.common.SectionHeader
import com.example.schedulelink.ui.common.commonDateFormatter
import com.example.schedulelink.ui.common.commonTimeFormatter
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.Motion
import com.example.schedulelink.ui.theme.tabularNums

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneDetailScreen(
    milestoneId: String,
    viewModel: MilestoneDetailViewModel,
    onEdit: (goalId: String, milestoneId: String) -> Unit,
    onBack: () -> Unit,
    onAddSchedule: (String) -> Unit,
    onScheduleClick: (String) -> Unit,
    isPhotoFeatureEnabled: Boolean
) {
    val milestone by viewModel.milestone(milestoneId).collectAsState(initial = null)
    val schedules by viewModel.schedules(milestoneId).collectAsState(initial = emptyList())
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
            AppFab(onClick = { onAddSchedule(milestoneId) }, icon = Icons.Default.Add, contentDescription = "小日程を追加")
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val memo = milestone?.memo?.takeIf { it.isNotBlank() }
            val photoUrls = milestone?.photoUrls?.takeIf { isPhotoFeatureEnabled && it.isNotEmpty() }
            if (memo != null || photoUrls != null) {
                InfoPanel(
                    modifier = Modifier.padding(
                        start = Dimens.ScreenPadding,
                        end = Dimens.ScreenPadding,
                        top = Dimens.ScreenPadding
                    )
                ) {
                    memo?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    photoUrls?.let { urls -> PhotoGallery(urls = urls) }
                }
            }

            if (schedules.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.EventNote,
                    message = "まだ小日程がありません",
                    modifier = Modifier.fillMaxSize(),
                    actionLabel = "小日程を追加",
                    onAction = { onAddSchedule(milestoneId) }
                )
            } else {
                SectionHeader("小日程", modifier = Modifier.padding(horizontal = Dimens.ScreenPadding))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.ScreenPadding,
                        end = Dimens.ScreenPadding,
                        top = 4.dp,
                        bottom = Dimens.FabClearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ListGap)
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleRow(
                            schedule = schedule,
                            onClick = { onScheduleClick(schedule.id) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(Motion.DurationMedium),
                                placementSpec = tween(Motion.DurationLong, easing = Motion.Emphasized),
                                fadeOutSpec = tween(Motion.DurationShort)
                            )
                        )
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
                DestructiveTextButton("削除") {
                    showDeleteConfirm = false
                    viewModel.deleteMilestone(milestoneId) { onBack() }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun ScheduleRow(schedule: ScheduleEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Dimens.ListItemPaddingH, vertical = Dimens.ListItemPaddingV)) {
            Text(
                "${schedule.date.format(commonDateFormatter)} " +
                    "${schedule.startTime.format(commonTimeFormatter)}〜${schedule.endTime.format(commonTimeFormatter)}",
                style = MaterialTheme.typography.labelLarge.tabularNums(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(schedule.title, style = MaterialTheme.typography.titleSmall)
        }
    }
}
