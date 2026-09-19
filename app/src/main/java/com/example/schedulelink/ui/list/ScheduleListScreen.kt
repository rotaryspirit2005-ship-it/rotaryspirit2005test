package com.example.schedulelink.ui.list

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.collectAsState

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ScheduleListScreen(
    viewModel: ScheduleListViewModel,
    initialDate: LocalDate,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val schedules by viewModel.schedules.collectAsState()

    // 週表示の該当カードから、この画面全体がコンテナ変形でせり出してくるように見せる。
    // ここで使うキーは、画面に入った時点の日付(initialDate)で固定しておく
    // (この画面の中で前後の日付に移動しても、遷移アニメーション用のキーは変えない)。
    val screenModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = "day-$initialDate"),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }

    Scaffold(
        modifier = screenModifier,
        topBar = {
            TopAppBar(
                title = { Text(selectedDate.format(dateFormatter)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "週表示に戻る", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToPreviousDay() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "前の日", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.goToToday() }) {
                        Icon(Icons.Default.Today, contentDescription = "今日", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.goToNextDay() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "次の日", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "予定を追加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FlowLegend()
            if (schedules.isEmpty()) {
                ScheduleEmptyState(modifier = Modifier.fillMaxSize())
            } else {
                ScheduleFlowList(
                    items = schedules,
                    onItemClick = onItemClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
