package com.example.schedulelink.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.FamilyRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyGroupScreen(
    uid: String,
    displayName: String,
    familyRepository: FamilyRepository,
    onSignOut: () -> Unit
) {
    var familyName by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("家族グループ") },
                actions = { TextButton(onClick = onSignOut) { Text("サインアウト") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "家族グループを新しく作るか、すでにある家族グループの招待コードで参加してください",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = familyName,
                onValueChange = { familyName = it },
                label = { Text("新しい家族グループ名") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (familyName.isBlank()) {
                        errorMessage = "グループ名を入力してください"
                    } else {
                        errorMessage = null
                        loading = true
                        scope.launch {
                            runCatching { familyRepository.createFamily(uid, displayName, familyName.trim()) }
                                .onFailure { errorMessage = it.message }
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("作成する") }

            Divider()

            OutlinedTextField(
                value = inviteCodeInput,
                onValueChange = { inviteCodeInput = it },
                label = { Text("招待コード") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (inviteCodeInput.isBlank()) {
                        errorMessage = "招待コードを入力してください"
                    } else {
                        errorMessage = null
                        loading = true
                        scope.launch {
                            familyRepository.joinFamily(uid, displayName, inviteCodeInput)
                                .onFailure { errorMessage = it.message }
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("参加する") }

            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
