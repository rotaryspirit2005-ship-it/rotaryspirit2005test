package com.example.schedulelink.ui.importing

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.importing.ImportedEvent
import com.example.schedulelink.importing.IcsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportIcsScreen(repository: ScheduleRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var events by remember { mutableStateOf<List<ImportedEvent>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf<Int?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        errorMessage = null
        importedCount = null
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BufferedReader(InputStreamReader(input)).readText()
                    }
                }.getOrNull()
            }
            if (text == null) {
                errorMessage = "ファイルを読み込めませんでした"
            } else {
                val parsed = withContext(Dispatchers.Default) { IcsParser.parse(text) }
                events = parsed
                selected = parsed.indices.toSet()
                if (parsed.isEmpty()) errorMessage = "予定が見つかりませんでした"
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(".icsファイルから読み込む") },
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
            Text(
                "他のカレンダーアプリからエクスポートした .ics ファイルを選んでください",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { pickFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("ファイルを選択")
            }

            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            importedCount?.let { Text("${it}件の予定を追加しました", color = MaterialTheme.colorScheme.primary) }

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (events.isNotEmpty()) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = index in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + index else selected - index
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
                            val toImport = events.filterIndexed { index, _ -> index in selected }
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
                            selected = emptySet()
                            importing = false
                        }
                    },
                    enabled = !importing && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (importing) "取り込み中..." else "選んだ${selected.size}件を取り込む")
                }
            }
        }
    }
}
