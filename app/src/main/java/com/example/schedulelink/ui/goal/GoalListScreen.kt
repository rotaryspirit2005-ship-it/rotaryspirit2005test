package com.example.schedulelink.ui.goal

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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.GoalType
import com.example.schedulelink.ui.common.AppFab
import com.example.schedulelink.ui.common.AppProgressBar
import com.example.schedulelink.ui.common.EmptyState
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(
    viewModel: GoalListViewModel,
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val goals by viewModel.goals.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目的マップ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            AppFab(onClick = onAddClick, icon = Icons.Default.Add, contentDescription = "目的を追加")
        }
    ) { padding ->
        if (goals.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Flag,
                message = "まだ大目的がありません",
                modifier = Modifier.fillMaxSize().padding(padding),
                actionLabel = "大目的を追加",
                onAction = onAddClick
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = Dimens.ScreenPadding,
                    end = Dimens.ScreenPadding,
                    top = Dimens.ScreenPadding,
                    bottom = Dimens.FabClearance
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.ListGap)
            ) {
                items(goals, key = { it.goal.id }) { item ->
                    GoalCard(
                        item = item,
                        onClick = { onItemClick(item.goal.id) },
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

@Composable
private fun GoalCard(item: GoalListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val goal = item.goal
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = Dimens.ListItemPaddingH, vertical = Dimens.ListItemPaddingV),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(goal.title, style = MaterialTheme.typography.titleMedium)
            if (goal.memo.isNotBlank()) {
                Text(
                    goal.memo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (goal.type == GoalType.HABIT) {
                Text(
                    "継続中の習慣目的",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "中日程 ${item.progress.doneCount}/${item.progress.totalCount} 完了",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppProgressBar(ratio = item.progress.ratio, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
