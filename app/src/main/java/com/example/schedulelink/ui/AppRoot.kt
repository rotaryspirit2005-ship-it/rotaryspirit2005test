package com.example.schedulelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.schedulelink.data.PhotoStorageRepository
import com.example.schedulelink.data.ScheduleRepository
import com.example.schedulelink.data.TodoRepository
import com.example.schedulelink.ui.auth.FamilyGroupScreen
import com.example.schedulelink.ui.auth.SignInScreen
import com.example.schedulelink.ui.navigation.ScheduleNavHost
import com.example.schedulelink.ui.widget.refreshAgendaWidgets
import com.example.schedulelink.ui.widget.refreshMonthMiniWidgets
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
fun AppRoot(
    app: ScheduleLinkApplication,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isPhotoFeatureEnabled: Boolean,
    onTogglePhotoFeature: (Boolean) -> Unit
) {
    val user by app.authRepository.currentUser.collectAsState(initial = null)
    val firebaseUser = user

    if (firebaseUser == null) {
        SignInScreen(authRepository = app.authRepository)
    } else {
        FamilyGate(app, firebaseUser, isDarkTheme, onToggleTheme, isPhotoFeatureEnabled, onTogglePhotoFeature)
    }
}

@Composable
private fun FamilyGate(
    app: ScheduleLinkApplication,
    user: FirebaseUser,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isPhotoFeatureEnabled: Boolean,
    onTogglePhotoFeature: (Boolean) -> Unit
) {
    // Firestoreのセキュリティルール未整備・権限エラーなどでFlowが例外終了しても
    // アプリ全体がクラッシュしないよう、ここで必ず捕まえてエラー画面に変換する。
    val state by remember(user.uid) {
        app.familyRepository.observeFamilyId(user.uid)
            .map<String?, FamilyGateState> { id -> if (id == null) FamilyGateState.NoFamily else FamilyGateState.Joined(id) }
            .catch { e -> emit(FamilyGateState.Error(e.message ?: "不明なエラーが発生しました")) }
    }.collectAsState(initial = FamilyGateState.Loading)

    val signOut: () -> Unit = {
        app.widgetPreferences.familyId = null
        app.authRepository.signOut()
    }

    when (val current = state) {
        is FamilyGateState.Loading -> Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        is FamilyGateState.NoFamily -> FamilyGroupScreen(
            uid = user.uid,
            displayName = user.displayName ?: user.email ?: "家族メンバー",
            familyRepository = app.familyRepository,
            onSignOut = signOut
        )
        is FamilyGateState.Joined -> MainApp(
            app,
            uid = user.uid,
            familyId = current.familyId,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            isPhotoFeatureEnabled = isPhotoFeatureEnabled,
            onTogglePhotoFeature = onTogglePhotoFeature,
            onSignOut = signOut
        )
        is FamilyGateState.Error -> FamilyGateErrorScreen(
            message = current.message,
            onSignOut = signOut
        )
    }
}

@Composable
private fun FamilyGateErrorScreen(message: String, onSignOut: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
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

@OptIn(FlowPreview::class)
@Composable
private fun MainApp(
    app: ScheduleLinkApplication,
    uid: String,
    familyId: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isPhotoFeatureEnabled: Boolean,
    onTogglePhotoFeature: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val scheduleRepository = remember(familyId) { ScheduleRepository(app.firestore, familyId) }
    val goalRepository = remember(familyId) { GoalRepository(app.firestore, familyId) }
    val milestoneRepository = remember(familyId) { MilestoneRepository(app.firestore, familyId) }
    val photoStorageRepository = remember(familyId) { PhotoStorageRepository(FirebaseStorage.getInstance(), familyId) }
    val todoRepository = remember(familyId) { TodoRepository(app.firestore, familyId) }

    LaunchedEffect(familyId) {
        // ウィジェットはCompose外(バックグラウンド)から動くため、
        // サインイン済みの家族IDだけ端末に保存しておき、判明したタイミングで
        // 30分の定期更新を待たずに一度だけ内容を最新化する。
        app.widgetPreferences.familyId = familyId
        refreshAgendaWidgets(app)
        refreshMonthMiniWidgets(app)
    }

    // アプリでやることを追加・完了したら、ウィジェットの「やること」もすぐ最新にする
    // (家族の誰かが変えた場合も、アプリを開いている間は反映される)。連続した操作はまとめる。
    LaunchedEffect(todoRepository) {
        todoRepository.allTodos()
            .map { todos -> todos.map { listOf(it.id, it.title, it.done, it.dueDate, it.scheduleId) } }
            .distinctUntilChanged()
            .drop(1)
            .debounce(1_500)
            .collect { runCatching { refreshMonthMiniWidgets(app) } }
    }

    ScheduleNavHost(
        repository = scheduleRepository,
        goalRepository = goalRepository,
        milestoneRepository = milestoneRepository,
        photoStorageRepository = photoStorageRepository,
        todoRepository = todoRepository,
        uid = uid,
        familyId = familyId,
        familyRepository = app.familyRepository,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        isPhotoFeatureEnabled = isPhotoFeatureEnabled,
        onTogglePhotoFeature = onTogglePhotoFeature,
        onSignOut = onSignOut
    )
}
