package com.example.schedulelink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.schedulelink.ui.AppRoot
import com.example.schedulelink.ui.theme.ScheduleLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ScheduleLinkApplication

        setContent {
            val isDarkTheme by app.themePreferences.isDarkTheme.collectAsState()

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
                        onToggleTheme = { app.themePreferences.setDarkTheme(!isDarkTheme) }
                    )
                }
            }
        }
    }
}
