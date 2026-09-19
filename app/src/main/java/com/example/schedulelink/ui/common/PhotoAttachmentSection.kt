package com.example.schedulelink.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private sealed interface PhotoItem {
    data class Existing(val url: String) : PhotoItem
    data class Pending(val uri: Uri) : PhotoItem
}

/**
 * 目的/中日程/小日程の編集画面で共通して使う、写真の追加・削除UI。
 * 既にアップロード済みでURLを持つ写真(existingUrls)と、
 * まだアップロードしていない選択直後の写真(pendingUris)を横並びで表示する。
 * アップロード自体は保存時にまとめて行うので、ここでは選択/削除の状態管理のみ行う。
 */
@Composable
fun PhotoAttachmentSection(
    existingUrls: List<String>,
    pendingUris: List<Uri>,
    onAdd: (List<Uri>) -> Unit,
    onRemoveExisting: (String) -> Unit,
    onRemovePending: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) onAdd(uris) }

    val items: List<PhotoItem> = existingUrls.map { PhotoItem.Existing(it) } +
        pendingUris.map { PhotoItem.Pending(it) }

    Column(modifier = modifier) {
        Text("写真", style = MaterialTheme.typography.titleSmall)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items,
                key = { item ->
                    when (item) {
                        is PhotoItem.Existing -> item.url
                        is PhotoItem.Pending -> item.uri.toString()
                    }
                }
            ) { item ->
                PhotoThumbnail(
                    model = when (item) {
                        is PhotoItem.Existing -> item.url
                        is PhotoItem.Pending -> item.uri
                    },
                    onRemove = {
                        when (item) {
                            is PhotoItem.Existing -> onRemoveExisting(item.url)
                            is PhotoItem.Pending -> onRemovePending(item.uri)
                        }
                    }
                )
            }
            item {
                AddPhotoTile(
                    onClick = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
        }
    }
}

/** 詳細画面で使う、削除ボタンのない読み取り専用の写真一覧。 */
@Composable
fun PhotoGallery(urls: List<String>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(urls, key = { it }) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(model: Any, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(88.dp)) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(22.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                // 写真の上に重ねる削除バッジ。背景の明暗に関わらず常に
                // 白いアイコンとのコントラストを保つ必要があるため、
                // テーマに関係なく固定の暗色にする。
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "削除",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AddAPhoto,
            contentDescription = "写真を追加",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
