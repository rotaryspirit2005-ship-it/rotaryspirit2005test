package com.example.schedulelink.ui.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.TagEntity
import com.example.schedulelink.data.TagRepository
import com.example.schedulelink.ui.common.DestructiveTextButton
import com.example.schedulelink.ui.common.FormLabel
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.TAG_COLOR_COUNT
import com.example.schedulelink.ui.theme.tagColor

// 家族のタグ(誰の予定か)の共通部品。タグの一覧は画面の上位で一度だけ購読して
// [LocalTagController]で配るので、表示側は引数を増やさずにタグを引ける。

/** いま家族にあるタグと、タグの追加・更新・削除の窓口。 */
class TagController(val tags: List<TagEntity>, val repository: TagRepository) {
    val byId: Map<String, TagEntity> = tags.associateBy { it.id }

    /** [tagIds]のうち、存在するタグだけを順に返す(削除済みのタグのIDは無視する)。 */
    fun resolve(tagIds: List<String>): List<TagEntity> = tagIds.mapNotNull { byId[it] }
}

val LocalTagController = compositionLocalOf<TagController?> { null }

/** タグ絞り込みで「タグが付いていない項目」を表す特別なID。 */
const val NO_TAG_FILTER = "__none__"

/**
 * [filter](選んだタグのID。[NO_TAG_FILTER]を含められる)に項目が合うか。何も選んでいなければ
 * すべて合う。複数選んだときは、どれか1つでも付いていれば合う。
 */
fun matchesTagFilter(tagIds: List<String>, filter: Set<String>, knownTagIds: Set<String>): Boolean {
    if (filter.isEmpty()) return true
    val known = tagIds.filter { it in knownTagIds }
    if (NO_TAG_FILTER in filter && known.isEmpty()) return true
    return known.any { it in filter }
}

@Composable
fun TagDot(color: Color, size: Dp = 8.dp) {
    Box(modifier = Modifier.size(size).clip(CircleShape).background(color))
}

/** 項目の行・カードに出す、タグの名前つきの小さな表示。多いときは「+N」にまとめる。 */
@Composable
fun TagLabels(tagIds: List<String>, modifier: Modifier = Modifier, max: Int = 2) {
    val controller = LocalTagController.current ?: return
    val tags = controller.resolve(tagIds)
    if (tags.isEmpty()) return
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.take(max).forEach { tag ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TagDot(tagColor(tag.colorIndex, isDark))
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
            }
        }
        if (tags.size > max) {
            Text(
                text = "+${tags.size - max}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 名前のない、色の点だけの表示(幅の狭いカード用)。 */
@Composable
fun TagDots(tagIds: List<String>, modifier: Modifier = Modifier, max: Int = 3) {
    val controller = LocalTagController.current ?: return
    val tags = controller.resolve(tagIds)
    if (tags.isEmpty()) return
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = modifier.semantics { contentDescription = "タグ: " + tags.joinToString("、") { it.name } },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.take(max).forEach { tag -> TagDot(tagColor(tag.colorIndex, isDark)) }
    }
}

/** 編集画面で、項目に付けるタグを選ぶ欄。その場で新しいタグも作れる(作ったらすぐ選択される)。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerSection(selectedIds: List<String>, onChange: (List<String>) -> Unit) {
    val controller = LocalTagController.current ?: return
    val isDark = LocalIsDarkTheme.current
    var showCreate by remember { mutableStateOf(false) }

    // ダイアログは親に大きさ0の要素を1つ出すので、呼び出し元の間隔(spacedBy)が増えないよう全体を包む。
    Box {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormLabel("タグ(誰の予定か)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            controller.tags.forEach { tag ->
                val selected = tag.id in selectedIds
                FilterChip(
                    selected = selected,
                    onClick = { onChange(if (selected) selectedIds - tag.id else selectedIds + tag.id) },
                    label = { Text(tag.name) },
                    leadingIcon = {
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        } else {
                            TagDot(tagColor(tag.colorIndex, isDark), size = 10.dp)
                        }
                    }
                )
            }
            AssistChip(
                onClick = { showCreate = true },
                label = { Text(if (controller.tags.isEmpty()) "タグを追加" else "新規") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        if (controller.tags.isEmpty()) {
            Text(
                "家族の名前などのタグを付けると、誰の予定かを色で分けて見られます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showCreate) {
        TagEditDialog(
            initial = null,
            suggestedColorIndex = controller.tags.size,
            onDismiss = { showCreate = false },
            onSave = { name, colorIndex ->
                showCreate = false
                val id = controller.repository.saveTag(TagEntity(name = name, colorIndex = colorIndex))
                onChange(selectedIds + id)
            },
            onDelete = null
        )
    }
    }
}

/** タグの名前と色を決めるダイアログ。[initial]がnullなら新規作成。[onDelete]があれば削除ボタンも出す。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    initial: TagEntity?,
    suggestedColorIndex: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, colorIndex: Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isDark = LocalIsDarkTheme.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: (suggestedColorIndex % TAG_COLOR_COUNT)) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "タグを作る" else "タグを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("名前(例: パパ、ママ、家族全員)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FormLabel("色")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (index in 0 until TAG_COLOR_COUNT) {
                        val selected = index == colorIndex
                        val swatch = tagColor(index, isDark)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { colorIndex = index })
                                .semantics { contentDescription = "色${index + 1}" },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                // 明るい色(ダークのパステル)の上でも見えるよう、色の明るさでチェックの色を替える。
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed, colorIndex) }, enabled = trimmed.isNotEmpty()) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) DestructiveTextButton("削除", onDelete)
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        }
    )
}

/**
 * タグで絞り込む横並びのチップ行。何も選んでいない状態が「すべて」。複数選ぶと、どれかが付いた
 * 項目を出す。タグが1件もないときは何も出さない。
 */
@Composable
fun TagFilterRow(selected: Set<String>, onChange: (Set<String>) -> Unit, modifier: Modifier = Modifier) {
    val controller = LocalTagController.current ?: return
    if (controller.tags.isEmpty()) return
    val isDark = LocalIsDarkTheme.current
    LazyRow(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected.isEmpty(),
                onClick = { onChange(emptySet()) },
                label = { Text("すべて") }
            )
        }
        items(controller.tags, key = { it.id }) { tag ->
            val isSelected = tag.id in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - tag.id else selected + tag.id) },
                label = { Text(tag.name) },
                leadingIcon = {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    } else {
                        TagDot(tagColor(tag.colorIndex, isDark), size = 10.dp)
                    }
                }
            )
        }
        item(key = "none") {
            val isSelected = NO_TAG_FILTER in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - NO_TAG_FILTER else selected + NO_TAG_FILTER) },
                label = { Text("タグなし") },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
        }
    }
}
