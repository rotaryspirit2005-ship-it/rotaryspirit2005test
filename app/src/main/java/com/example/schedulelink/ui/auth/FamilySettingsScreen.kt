package com.example.schedulelink.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulelink.data.FamilyRepository
import com.example.schedulelink.data.familyErrorMessage
import com.example.schedulelink.ui.common.DestructiveTextButton
import com.example.schedulelink.ui.common.InfoPanel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySettingsScreen(
    uid: String,
    familyId: String,
    familyRepository: FamilyRepository,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    onSignOut: () -> Unit
) {
    val family by familyRepository.observeFamily(familyId).collectAsState(initial = null)
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("家族グループの設定") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val current = family
            if (current != null) {
                Text(current.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "メンバー ${current.memberCount}人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                InfoPanel {
                    Text(
                        "招待コード",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        current.inviteCode,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    )
                    Text(
                        "このコードを家族に伝えると、同じ予定を一緒に編集できます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(onClick = {
                        clipboardManager.setText(AnnotatedString(current.inviteCode))
                        scope.launch { snackbarHostState.showSnackbar("コピーしました") }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("コピー")
                    }
                }
            }

            HorizontalDivider()
            DestructiveTextButton("この家族グループを抜ける") { showLeaveConfirm = true }
            TextButton(onClick = onSignOut) {
                Text("サインアウト")
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("家族グループを抜けますか?") },
            text = { Text("共有されている予定は見られなくなります。") },
            confirmButton = {
                DestructiveTextButton("抜ける") {
                    showLeaveConfirm = false
                    scope.launch {
                        // 通信・権限エラーでアプリが落ちないよう、失敗はメッセージで知らせる。
                        runCatching { familyRepository.leaveFamily(uid, familyId) }
                            .onSuccess { onLeft() }
                            .onFailure { snackbarHostState.showSnackbar(familyErrorMessage(it)) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}
