package com.example.schedulelink

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.schedulelink.ui.AppRoot
import com.example.schedulelink.ui.navigation.DeepLinks
import com.example.schedulelink.ui.splash.SlatchSplash
import com.example.schedulelink.ui.theme.ScheduleLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // システムのスプラッシュ画面(Theme.Slatch.Starting)を使う。super.onCreateより前に呼ぶ。
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as ScheduleLinkApplication
        // ウィジェットから特定の画面(やること一覧など)を開く指定。画面回転などの再生成では繰り返さない。
        // ウィジェットなどから特定の画面を開いた起動のときは、起動画面を出さない。
        var launchedFromDeepLink = false
        if (savedInstanceState == null) {
            DeepLinks.destinationOf(intent)?.let {
                DeepLinks.pending.value = it
                launchedFromDeepLink = true
            }
        }

        setContent {
            val isDarkTheme by app.themePreferences.isDarkTheme.collectAsState()
            val isPhotoFeatureEnabled by app.photoFeaturePreferences.isEnabled.collectAsState()

            // ステータスバー・ナビゲーションバーを透明にしてアプリの背景を画面端まで広げる。
            // アイコンの明暗は、端末設定ではなくアプリ内で選んだライト/ダークに合わせる。
            DisposableEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkTheme }
                )
                onDispose {}
            }

            // 独自のライト/ダーク配色を使うため、端末の壁紙から色を作る
            // ダイナミックカラーは無効にする(そうしないと視認性を保証できない)。
            ScheduleLinkTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 起動のたびに1度だけ、ロゴとサブタイトルの起動画面を重ねて出す(画面回転では繰り返さない)。
                    // 下の本編は先に読み込みを始める。ウィジェットなどから開いたときは出さない。
                    var showSplash by rememberSaveable { mutableStateOf(!launchedFromDeepLink) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppRoot(
                            app = app,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { app.themePreferences.setDarkTheme(!isDarkTheme) },
                            isPhotoFeatureEnabled = isPhotoFeatureEnabled,
                            onTogglePhotoFeature = { app.photoFeaturePreferences.setEnabled(it) }
                        )
                        if (showSplash) SlatchSplash(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.destinationOf(intent)?.let { DeepLinks.pending.value = it }
    }
}
