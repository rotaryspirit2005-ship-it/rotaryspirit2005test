package com.example.schedulelink.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.schedulelink.ui.common.SaveBottomBar
import com.example.schedulelink.ui.help.HelpAction
import com.example.schedulelink.ui.help.HelpTopic
import com.example.schedulelink.ui.tag.LocalTagController
import com.example.schedulelink.ui.tag.TagPickerSection
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.GoalColorDark
import com.example.schedulelink.ui.theme.GoalColorLight
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.MilestoneColorDark
import com.example.schedulelink.ui.theme.MilestoneColorLight
import com.example.schedulelink.ui.theme.ScheduleColorDark
import com.example.schedulelink.ui.theme.ScheduleColorLight
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)

private fun LocalDate.toMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun levelColor(level: PlanLevel): Color {
    val dark = LocalIsDarkTheme.current
    return when (level) {
        PlanLevel.Why -> if (dark) GoalColorDark else GoalColorLight
        PlanLevel.How -> if (dark) MilestoneColorDark else MilestoneColorLight
        PlanLevel.What -> if (dark) ScheduleColorDark else ScheduleColorLight
    }
}

/** 「いつ」の表示。予定は1日、大目的・中日程は期間。 */
private fun whenText(level: PlanLevel, start: LocalDate?, end: LocalDate?): String? = when {
    start == null -> null
    level == PlanLevel.What || end == null || end == start -> start.format(shortDate)
    else -> "${start.format(shortDate)}〜${end.format(shortDate)}"
}

/**
 * 「表で計画」画面。紙に書くように、なぜ(大目的)・どう進める(中日程)・何を(予定)を字下げで並べ、
 * いつ・誰が・どこでを各行に付ける。「確認へ」で登録内容を確かめてから、まとめて登録する。
 * 書きかけは端末に自動で残る。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanTableScreen(
    goalId: String?,
    viewModel: PlanTableViewModel,
    onBack: () -> Unit,
    onFinished: (goalIds: List<String>) -> Unit
) {
    val context = LocalContext.current
    val store = remember { PlanDraftStore(context) }
    val rows by viewModel.rows.collectAsState()
    val existing by viewModel.existing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var confirming by rememberSaveable { mutableStateOf(false) }
    var excluded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var saving by remember { mutableStateOf(false) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var dateRowId by remember { mutableStateOf<String?>(null) }
    var tagRowId by remember { mutableStateOf<String?>(null) }
    var locationRowId by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 書きかけはViewModelが変更のたびに端末へ残す
    LaunchedEffect(Unit) { viewModel.initialize(store, goalId) }
    val listState = rememberLazyListState()
    // 足した行が画面の外なら、そこまで送ってから入力欄を移す(先頭の凡例の分だけ1つずらす)
    LaunchedEffect(focusId) {
        val index = rows.indexOfFirst { it.id == focusId }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    fun openConfirm() {
        excluded = emptyList()
        confirming = true
        viewModel.loadExisting(onError = {
            confirming = false
            scope.launch { snackbarHostState.showSnackbar("登録済みの内容を読み込めませんでした。通信状態を確かめてください") }
        })
    }

    BackHandler(enabled = confirming && !saving) { confirming = false }
    // 登録中に画面を離れると途中で止まってしまうので、終わるまで戻る操作を受け付けない
    BackHandler(enabled = saving) { }

    val resolved = existing?.let { (goals, milestones) -> resolvePlan(rows, goals, milestones) }
    // 実際に登録する行: チェックが入っていて、問題がなく、親も登録される(または登録済み)行
    val included: Set<String> = buildSet {
        if (resolved == null) return@buildSet
        val parents = parentsOf(rows)
        resolved.forEach { r ->
            val parent = parents[r.row.id]
            val parentOk = parent == null || parent.id in this
            if (r.error == null && r.row.id !in excluded && parentOk) add(r.row.id)
        }
    }
    val createCount = resolved?.count { it.row.id in included && it.matchedId == null } ?: 0

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (confirming) "登録する内容の確認" else "表で計画") },
                navigationIcon = {
                    IconButton(onClick = { if (confirming) confirming = false else onBack() }, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    HelpAction(HelpTopic.PlanTable)
                    if (!confirming) {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "その他")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("表を空にする") },
                                onClick = { menu = false; showClearConfirm = true }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (confirming) {
                SaveBottomBar(
                    onSave = {
                        saving = true
                        viewModel.register(
                            included = included,
                            onDone = { count, goalIds ->
                                saving = false
                                confirming = false
                                android.widget.Toast.makeText(context, "${count}件を登録しました", android.widget.Toast.LENGTH_SHORT).show()
                                onFinished(goalIds)
                            },
                            onError = { count ->
                                saving = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "途中で登録できませんでした(${count}件は登録済み)。通信状態を確かめて、もう一度お試しください"
                                    )
                                }
                            }
                        )
                    },
                    enabled = !saving && existing != null && createCount > 0,
                    label = when {
                        saving -> "登録しています…"
                        createCount == 0 -> "登録できる行がありません"
                        else -> "${createCount}件を登録"
                    }
                )
            } else {
                SaveBottomBar(onSave = { openConfirm() }, label = "確認へ", enabled = rows.any { it.existingId == null })
            }
        }
    ) { padding ->
        if (confirming) {
            if (resolved == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                ConfirmList(
                    resolved = resolved,
                    included = included,
                    excluded = excluded.toSet(),
                    onToggle = { id, checked ->
                        // 親を外すと、その下の行もまとめて外す
                        val parents = parentsOf(rows)
                        fun descendantsOf(rowId: String): List<String> = rows.filter { parents[it.id]?.id == rowId }
                            .flatMap { listOf(it.id) + descendantsOf(it.id) }
                        val ids = listOf(id) + descendantsOf(id)
                        excluded = if (checked) (excluded - ids.toSet()) else (excluded + ids).distinct()
                    },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "legend") { FiveW1HLegend() }
                items(rows, key = { it.id }) { row ->
                    val index = rows.indexOf(row)
                    PlanRowCard(
                        row = row,
                        requestFocus = focusId == row.id,
                        onFocused = { if (focusId == row.id) focusId = null },
                        canMoveUp = index > 0,
                        canMoveDown = index < rows.lastIndex,
                        onChange = viewModel::update,
                        onNext = { focusId = viewModel.insertAfter(row.id, row.level) },
                        onAddBelow = { level -> focusId = viewModel.insertAfter(row.id, level) },
                        onDate = { dateRowId = row.id },
                        onTags = { tagRowId = row.id },
                        onLocation = { locationRowId = row.id },
                        onIndent = { delta -> viewModel.indent(row.id, delta) },
                        onMove = { delta -> viewModel.move(row.id, delta) },
                        onDelete = {
                            val removed = viewModel.delete(row.id) ?: return@PlanRowCard
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    "行を削除しました",
                                    actionLabel = "元に戻す",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.restore(removed.first, removed.second)
                            }
                        }
                    )
                }
                item(key = "add") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PlanLevel.entries.forEach { level ->
                            OutlinedButton(onClick = { focusId = viewModel.append(level) }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${level.label}(${level.hint})")
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- 各セルの入力 ----

    dateRowId?.let { id ->
        val row = rows.firstOrNull { it.id == id }
        if (row != null) {
            // 前の行の日付から始めると、続けて選ぶときにめくる手間が少ない
            val index = rows.indexOf(row)
            val hintDate = row.start ?: rows.take(index).lastOrNull { it.start != null }?.let { it.end ?: it.start } ?: LocalDate.now()
            PlanDateDialog(
                row = row,
                hintDate = hintDate,
                onDismiss = { dateRowId = null },
                onConfirm = { start, end ->
                    viewModel.update(row.copy(start = start, end = if (row.level == PlanLevel.What) null else end))
                    dateRowId = null
                }
            )
        }
    }

    tagRowId?.let { id ->
        val row = rows.firstOrNull { it.id == id }
        if (row != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { tagRowId = null }, sheetState = sheetState) {
                Column(
                    modifier = Modifier.padding(horizontal = Dimens.ScreenPadding).padding(bottom = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("誰が「${row.title.ifBlank { row.level.label }}」をしますか？", style = MaterialTheme.typography.titleMedium)
                    TagPickerSection(selectedIds = row.tagIds, onChange = { viewModel.update(row.copy(tagIds = it)) })
                    Button(onClick = { tagRowId = null }, modifier = Modifier.fillMaxWidth()) { Text("決定") }
                }
            }
        }
    }

    locationRowId?.let { id ->
        val row = rows.firstOrNull { it.id == id }
        if (row != null) {
            var text by remember(id) { mutableStateOf(row.location) }
            AlertDialog(
                onDismissRequest = { locationRowId = null },
                title = { Text("どこで") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("場所") },
                        leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.update(row.copy(location = text.trim()))
                        locationRowId = null
                    }) { Text("決定") }
                },
                dismissButton = { TextButton(onClick = { locationRowId = null }) { Text("キャンセル") } }
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("表を空にしますか？") },
            text = { Text("書きかけの行はすべて消えます。登録済みの予定は消えません。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    showClearConfirm = false
                }) { Text("空にする") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("キャンセル") } }
        )
    }
}

/** 5W1Hの対応を示す見出し。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiveW1HLegend() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("5W1Hで書いてみよう", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                "なぜ = 大目的",
                "どう進める = 中日程",
                "何を = 予定",
                "いつ = 日付",
                "誰が = タグ",
                "どこで = 場所"
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanRowCard(
    row: PlanRow,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (PlanRow) -> Unit,
    onNext: () -> Unit,
    onAddBelow: (PlanLevel) -> Unit,
    onDate: () -> Unit,
    onTags: () -> Unit,
    onLocation: () -> Unit,
    onIndent: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val color = levelColor(row.level)
    val focusRequester = remember { FocusRequester() }
    val isExisting = row.existingId != null
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocused()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp * row.level.depth)
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(color, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp, top = 4.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.level.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.widthIn(min = 64.dp).padding(end = 6.dp)
                )
                BasicTextField(
                    value = row.title,
                    onValueChange = { onChange(row.copy(title = it)) },
                    readOnly = isExisting,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { onNext() }),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = 40.dp)) {
                            if (row.title.isEmpty()) {
                                Text(
                                    when (row.level) {
                                        PlanLevel.Why -> "何のために？(例: 引越し)"
                                        PlanLevel.How -> "どう進める？(例: 業者選び)"
                                        PlanLevel.What -> "何をする？(例: 見積もり依頼)"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            inner()
                        }
                    }
                )
                if (isExisting) {
                    Text(
                        "登録済み",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                RowMenu(
                    row = row,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onAddBelow = onAddBelow,
                    onIndent = onIndent,
                    onMove = onMove,
                    onDelete = onDelete
                )
            }
            if (!isExisting) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CellChip(
                        icon = Icons.Outlined.CalendarMonth,
                        value = whenText(row.level, row.start, row.end),
                        placeholder = "いつ？",
                        onClick = onDate
                    )
                    CellChip(
                        icon = Icons.Outlined.Person,
                        value = tagNames(row.tagIds),
                        placeholder = "誰が？",
                        onClick = onTags
                    )
                    if (row.level == PlanLevel.What) {
                        CellChip(
                            icon = Icons.Outlined.Place,
                            value = row.location.ifBlank { null },
                            placeholder = "どこで？",
                            onClick = onLocation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun tagNames(tagIds: List<String>): String? {
    val controller = LocalTagController.current ?: return null
    return controller.resolve(tagIds).takeIf { it.isNotEmpty() }?.joinToString("・") { it.name }
}

@Composable
private fun CellChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String?, placeholder: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                value ?: placeholder,
                maxLines = 1,
                color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
    )
}

@Composable
private fun RowMenu(
    row: PlanRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAddBelow: (PlanLevel) -> Unit,
    onIndent: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "「${row.title.ifBlank { row.level.label }}」の操作")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PlanLevel.entries.filter { it.depth >= row.level.depth }.forEach { level ->
                DropdownMenuItem(
                    text = { Text("下に「${level.label}」を追加") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { open = false; onAddBelow(level) }
                )
            }
            HorizontalDivider()
            if (row.existingId == null && row.level.depth < 2) {
                DropdownMenuItem(
                    text = { Text("一段下げる") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = null) },
                    onClick = { open = false; onIndent(1) }
                )
            }
            if (row.existingId == null && row.level.depth > 0) {
                DropdownMenuItem(
                    text = { Text("一段上げる") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = null) },
                    onClick = { open = false; onIndent(-1) }
                )
            }
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text("上へ") },
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                    onClick = { open = false; onMove(-1) }
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text("下へ") },
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                    onClick = { open = false; onMove(1) }
                )
            }
            DropdownMenuItem(
                text = { Text("この行を削除") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

/** 「いつ」をカレンダーで選ぶ。予定は1日、大目的・中日程は開始と終了をまとめて選ぶ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDateDialog(
    row: PlanRow,
    hintDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalDate?) -> Unit
) {
    if (row.level == PlanLevel.What) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (row.start ?: hintDate).toMillis())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { onConfirm(state.selectedDateMillis?.toLocalDate(), null) }) { Text("決定") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onConfirm(null, null) }) { Text("日付を消す") }
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                }
            }
        ) {
            DatePicker(state = state)
        }
    } else {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = row.start?.toMillis(),
            initialSelectedEndDateMillis = row.end?.toMillis(),
            initialDisplayedMonthMillis = (row.start ?: hintDate).toMillis()
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val start = state.selectedStartDateMillis?.toLocalDate()
                    // 1日だけ選んだときは、その日1日の期間にする
                    val end = state.selectedEndDateMillis?.toLocalDate() ?: start
                    onConfirm(start, end)
                }) { Text("決定") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onConfirm(null, null) }) { Text("日付を消す") }
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                }
            }
        ) {
            DateRangePicker(
                state = state,
                title = {
                    Text(
                        "「${row.title.ifBlank { row.level.label }}」の期間",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 登録前の確認。字下げのまま並べ、行ごとにチェックで登録するかを選ぶ。 */
@Composable
private fun ConfirmList(
    resolved: List<ResolvedRow>,
    included: Set<String>,
    excluded: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "intro") {
            Text(
                "チェックした行を登録します。赤い行は登録されないので、戻って直してください。同じ名前の大目的・中日程があれば、そこに追加します。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(resolved, key = { it.row.id }) { r ->
            val row = r.row
            val color = levelColor(row.level)
            val anchor = r.matchedId != null
            val checked = row.id in included
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp * row.level.depth)
                    .background(
                        if (r.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(12.dp)
                    )
                    .then(
                        if (r.error == null) {
                            Modifier.toggleable(value = checked, role = Role.Checkbox) { onToggle(row.id, row.id in excluded) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = r.error == null, modifier = Modifier.padding(horizontal = 12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.level.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                        Spacer(Modifier.width(8.dp))
                        Text(row.title.ifBlank { "(未入力)" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (anchor) "既存に追加" else "新規",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (anchor) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .background(
                                    if (anchor) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    val details = listOfNotNull(
                        if (anchor) null else whenText(row.level, r.start, r.end),
                        tagNames(row.tagIds),
                        row.location.ifBlank { null }?.let { "@$it" }
                    )
                    if (details.isNotEmpty()) {
                        Text(details.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 親の行が登録されないときは、その理由を出す(チェックだけ外れて見えないように)
                    val blockedByParent = r.error == null && !checked && row.id !in excluded
                    (r.error ?: if (blockedByParent) "上の行が登録されないため、この行も登録されません" else r.note)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                r.error != null -> MaterialTheme.colorScheme.onErrorContainer
                                blockedByParent -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}
