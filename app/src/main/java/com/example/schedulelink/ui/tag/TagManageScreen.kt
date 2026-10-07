package com.example.schedulelink.ui.tag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.TagEntity
import com.example.schedulelink.ui.common.AppFab
import com.example.schedulelink.ui.common.DestructiveTextButton
import com.example.schedulelink.ui.common.EmptyState
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.tagColor

/** 家族のタグ(誰の予定か)を作る・名前や色を変える・消す画面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageScreen(onBack: () -> Unit) {
    val controller = LocalTagController.current
    val isDark = LocalIsDarkTheme.current
    // nullは新規作成、それ以外は編集中のタグ。
    var editing by remember { mutableStateOf<TagEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TagEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("タグの管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            if (controller != null) AppFab(onClick = { creating = true }, icon = Icons.Default.Add, contentDescription = "タグを追加")
        }
    ) { padding ->
        val tags = controller?.tags.orEmpty()
        if (tags.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.LocalOffer,
                message = "まだタグがありません\n「パパ」「ママ」などを作ると、誰の予定かを色で分けられます",
                modifier = Modifier.fillMaxSize().padding(padding),
                actionLabel = "タグを作る",
                onAction = if (controller != null) ({ creating = true }) else null
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = Dimens.FabClearance)
            ) {
                items(tags, key = { it.id }) { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { editing = tag }
                            .padding(horizontal = Dimens.ListItemPaddingH, vertical = Dimens.ListItemPaddingV),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TagDot(tagColor(tag.colorIndex, isDark), size = 16.dp)
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (controller != null && creating) {
        TagEditDialog(
            initial = null,
            suggestedColorIndex = controller.tags.size,
            onDismiss = { creating = false },
            onSave = { name, colorIndex ->
                creating = false
                controller.repository.saveTag(TagEntity(name = name, colorIndex = colorIndex))
            },
            onDelete = null
        )
    }
    val target = editing
    if (controller != null && target != null) {
        TagEditDialog(
            initial = target,
            suggestedColorIndex = target.colorIndex,
            onDismiss = { editing = null },
            onSave = { name, colorIndex ->
                editing = null
                controller.repository.saveTag(target.copy(name = name, colorIndex = colorIndex))
            },
            onDelete = {
                editing = null
                deleting = target
            }
        )
    }
    val deleteTarget = deleting
    if (controller != null && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("「${deleteTarget.name}」を削除しますか?") },
            text = { Text("付いている予定からもこのタグが見えなくなります。予定そのものは消えません。") },
            confirmButton = {
                DestructiveTextButton("削除") {
                    deleting = null
                    controller.repository.deleteTag(deleteTarget.id)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } }
        )
    }
}
