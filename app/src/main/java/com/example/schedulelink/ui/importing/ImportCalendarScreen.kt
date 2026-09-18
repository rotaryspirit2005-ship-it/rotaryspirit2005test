package com.example.schedulelink.ui.importing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.importing.DeviceCalendar
import com.example.schedulelink.importing.DeviceCalendarReader
import com.example.schedulelink.importing.ImportedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCalendarScreen(repository: ScheduleRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    var calendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
    var selectedCalendarIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var rangeStart by remember { mutableStateOf(LocalDate.now()) }
    var rangeEnd by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    var events by remember { mutableStateOf<List<ImportedEvent>>(emptyList()) }
    var selectedEvents by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var searching by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            calendars = withContext(Dispatchers.IO) { DeviceCalendarReader.readCalendars(context.contentResolver) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("端末のカレンダーから読み込む") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasPermission) {
                Text(
                    "端末に同期されているカレンダー(Googleカレンダーなど)を読み取るには、カレンダーへのアクセス許可が必要です",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("アクセスを許可する") }
            } else {
                Text("読み込むカレンダー", style = MaterialTheme.typography.titleSmall)
                if (calendars.isEmpty()) {
                    Text(
                        "端末に同期されているカレンダーが見つかりません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    calendars.forEach { calendar ->
                        val checked = calendar.id in selectedCalendarIds
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedCalendarIds = if (isChecked) selectedCalendarIds + calendar.id else selectedCalendarIds - calendar.id
                                }
                            )
                            Column {
                                Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                                if (calendar.accountName.isNotBlank()) {
                                    Text(
                                        calendar.accountName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                        Text("開始: ${rangeStart.format(dateFormatter)}")
                    }
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                        Text("終了: ${rangeEnd.format(dateFormatter)}")
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        if (selectedCalendarIds.isEmpty()) {
                            errorMessage = "カレンダーを1つ以上選んでください"
                        } else if (!rangeEnd.isAfter(rangeStart)) {
                            errorMessage = "終了日は開始日より後にしてください"
                        } else {
                            searching = true
                            scope.launch {
                                val found = withContext(Dispatchers.IO) {
                                    DeviceCalendarReader.readEvents(
                                        context.contentResolver,
                                        selectedCalendarIds,
                                        rangeStart,
                                        rangeEnd.plusDays(1)
                                    )
                                }
                                events = found
                                selectedEvents = found.indices.toSet()
                                if (found.isEmpty()) errorMessage = "この期間に予定が見つかりませんでした"
                                searching = false
                            }
                        }
                    },
                    enabled = !searching,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (searching) "検索中..." else "予定を検索") }
            }

            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            importedCount?.let { Text("${it}件の予定を追加しました", color = MaterialTheme.colorScheme.primary) }

            if (events.isNotEmpty()) {
                Text(
                    "${events.size}件見つかりました。取り込む予定を選んでください",
                    style = MaterialTheme.typography.titleSmall
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(events.size) { index ->
                        val event = events[index]
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = index in selectedEvents,
                                onCheckedChange = { checked ->
                                    selectedEvents = if (checked) selectedEvents + index else selectedEvents - index
                                }
                            )
                            Column {
                                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${event.date} ${event.startTime}〜${event.endTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        importing = true
                        scope.launch {
                            val toImport = events.filterIndexed { index, _ -> index in selectedEvents }
                            toImport.forEach { event ->
                                repository.saveSchedule(
                                    ScheduleEntity(
                                        title = event.title,
                                        memo = event.memo,
                                        date = event.date,
                                        startTime = event.startTime,
                                        endTime = event.endTime
                                    ),
                                    emptySet()
                                )
                            }
                            importedCount = toImport.size
                            events = emptyList()
                            selectedEvents = emptySet()
                            importing = false
                        }
                    },
                    enabled = !importing && selectedEvents.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (importing) "取り込み中..." else "選んだ${selectedEvents.size}件を取り込む") }
            } else if (searching) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = rangeStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        rangeStart = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("キャンセル") } }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = rangeEnd.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        rangeEnd = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("キャンセル") } }
        ) { DatePicker(state = state) }
    }
}
