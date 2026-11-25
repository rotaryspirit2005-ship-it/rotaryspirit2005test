package com.example.schedulelink.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.common.EmptyState
import com.example.schedulelink.ui.help.HelpAction
import com.example.schedulelink.ui.help.HelpTopic
import com.example.schedulelink.ui.tag.TagDots
import com.example.schedulelink.ui.theme.Dimens
import java.time.format.DateTimeFormatter
import java.util.Locale

private val resultDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPAN)

/** 予定をさがす画面。タイトル・場所・メモに入っている言葉で絞り込み、タップで詳細を開く。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSearchScreen(
    viewModel: ScheduleSearchViewModel,
    onScheduleClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // 開いたらすぐ書き始められるようにする
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("予定をさがす") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
                actions = { HelpAction(HelpTopic.Search) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("タイトル・場所・メモ") },
                placeholder = { Text("例: 歯医者  駅前") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "入力を消す")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp)
                    .focusRequester(focusRequester)
            )
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                query.isBlank() -> EmptyState(
                    icon = Icons.Outlined.Search,
                    message = "さがしたい言葉を入れてください。\n言葉を空白で区切ると、すべてを含む予定に絞れます。",
                    modifier = Modifier.fillMaxSize()
                )
                results.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Search,
                    message = "「${query.trim()}」を含む予定は見つかりませんでした",
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimens.ScreenPadding)
                ) {
                    item(key = "count") {
                        Text(
                            "${results.size}件",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp)
                        )
                    }
                    // 新しい日付から並べる(これからの予定が見つけやすい)
                    items(results.sortedByDescending { it.date }, key = { it.id }) { schedule ->
                        ResultRow(schedule, onClick = { onScheduleClick(schedule.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(schedule: ScheduleEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "詳細を開く", onClick = onClick)
            .padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                schedule.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            TagDots(schedule.tagIds, modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            schedule.date.format(resultDateFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (schedule.location.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = "場所",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp).heightIn(max = 16.dp)
                )
                Text(
                    schedule.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
