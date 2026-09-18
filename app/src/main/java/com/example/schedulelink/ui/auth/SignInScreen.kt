package com.example.schedulelink.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

/**
 * "default_web_client_id" は google-services.json に Web クライアント(Firebase
 * コンソールでGoogleサインインを有効にすると自動生成される)が含まれている場合にのみ
 * ビルド時に生成される。存在しない設定ファイルでもアプリ全体のビルドが壊れないよう、
 * 生成されたRクラスを直接参照せず、実行時にリソースIDを探す。
 */
private fun findDefaultWebClientId(context: android.content.Context): String? {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (resId != 0) context.getString(resId) else null
}

@Composable
fun SignInScreen(authRepository: AuthRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val webClientId = remember { findDefaultWebClientId(context) }

    if (webClientId == null) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("予定リンク", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Googleサインインの設定が完了していません。\n" +
                        "Firebaseコンソールの「Authentication」で Google サインインを有効にしてから、\n" +
                        "google-services.json を再ダウンロードして差し替えてください。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val signInClient = remember(webClientId) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken == null) {
                errorMessage = "Googleサインインに失敗しました"
            } else {
                scope.launch {
                    runCatching { authRepository.signInWithGoogleIdToken(idToken) }
                        .onFailure { errorMessage = "サインインに失敗しました: ${it.message}" }
                }
            }
        } catch (e: ApiException) {
            errorMessage = "サインインに失敗しました(コード: ${e.statusCode})"
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("予定リンク", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "家族と予定を共有するには、Googleアカウントでサインインしてください",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { launcher.launch(signInClient.signInIntent) }, modifier = Modifier.fillMaxWidth()) {
                Text("Googleでサインイン")
            }
            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}
