package com.example.schedulelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.schedulelink.ScheduleLinkApplication
import com.example.schedulelink.data.GoalRepository
import com.example.schedulelink.data.MilestoneRepository
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.ui.auth.FamilyGroupScreen
import com.example.schedulelink.ui.auth.SignInScreen
import com.example.schedulelink.ui.navigation.ScheduleNavHost
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private sealed interface FamilyGateState {
    object Loading : FamilyGateState
    object NoFamily : FamilyGateState
    data class Joined(val familyId: String) : FamilyGateState
    data class Error(val message: String) : FamilyGateState
}

/**
 * サインイン状態・家族グループ所属状態に応じて、
 * サインイン画面 → 家族グループ作成/参加画面 → 本編 の順に切り替える。
 */
@Composable
fun AppRoot(app: ScheduleLinkApplication, isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    val user by app.authRepository.currentUser.collectAsState(initial = null)
    val firebaseUser = user

    if (firebaseUser == null) {
        SignInScreen(authRepository = app.authRepository)
    } else {
        FamilyGate(app, firebaseUser, isDarkTheme, onToggleTheme)
    }
}

@Composable
private fun FamilyGate(app: ScheduleLinkApplication, user: FirebaseUser, isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    // Firestoreのセキュリティルール未整備・権限エラーなどでFlowが例外終了しても
    // アプリ全体がクラッシュしないよう、ここで必ず捕まえてエラー画面に変換する。
    val state by remember(user.uid) {
        app.familyRepository.observeFamilyId(user.uid)
            .map<String?, FamilyGateState> { id -> if (id == null) FamilyGateState.NoFamily else FamilyGateState.Joined(id) }
            .catch { e -> emit(FamilyGateState.Error(e.message ?: "不明なエラーが発生しました")) }
    }.collectAsState(initial = FamilyGateState.Loading)

    when (val current = state) {
        is FamilyGateState.Loading -> Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        is FamilyGateState.NoFamily -> FamilyGroupScreen(
            uid = user.uid,
            displayName = user.displayName ?: user.email ?: "家族メンバー",
            familyRepository = app.familyRepository,
            onSignOut = { app.authRepository.signOut() }
        )
        is FamilyGateState.Joined -> MainApp(
            app,
            uid = user.uid,
            familyId = current.familyId,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme
        )
        is FamilyGateState.Error -> FamilyGateErrorScreen(
            message = current.message,
            onSignOut = { app.authRepository.signOut() }
        )
    }
}

@Composable
private fun FamilyGateErrorScreen(message: String, onSignOut: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("データの読み込みに失敗しました", style = MaterialTheme.typography.titleMedium)
            Text(
                "Firestoreのセキュリティルールが正しく設定されていない可能性があります。\n\n$message",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onSignOut) { Text("サインアウトしてやり直す") }
        }
    }
}

@Composable
private fun MainApp(
    app: ScheduleLinkApplication,
    uid: String,
    familyId: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val scheduleRepository = remember(familyId) { ScheduleRepository(app.firestore, familyId) }
    val goalRepository = remember(familyId) { GoalRepository(app.firestore, familyId) }
    val milestoneRepository = remember(familyId) { MilestoneRepository(app.firestore, familyId) }

    ScheduleNavHost(
        repository = scheduleRepository,
        goalRepository = goalRepository,
        milestoneRepository = milestoneRepository,
        uid = uid,
        familyId = familyId,
        familyRepository = app.familyRepository,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        onSignOut = { app.authRepository.signOut() }
    )
}
