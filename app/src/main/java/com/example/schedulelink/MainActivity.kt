package com.example.schedulelink

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.schedulelink.ui.AppRoot
import com.example.schedulelink.ui.theme.ScheduleLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as ScheduleLinkApplication

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
                    AppRoot(
                        app = app,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { app.themePreferences.setDarkTheme(!isDarkTheme) },
                        isPhotoFeatureEnabled = isPhotoFeatureEnabled,
                        onTogglePhotoFeature = { app.photoFeaturePreferences.setEnabled(it) }
                    )
                }
            }
        }
    }
}
