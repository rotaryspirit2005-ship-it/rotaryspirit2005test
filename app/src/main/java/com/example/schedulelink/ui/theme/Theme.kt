package com.example.schedulelink.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 全体マップなどで使う、Material3のColorSchemeに乗らない独自アクセント色を
 * ライト/ダークどちらで出し分けるかの判定に使う。ユーザーが手動で
 * ライト/ダークを切り替えた場合はその選択(=[ScheduleLinkTheme]のdarkTheme引数)を、
 * 端末のシステム設定ではなくこちらが正としてツリー全体に伝える。
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    secondary = GreenSecondary,
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    secondary = GreenSecondaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorRed
)

@Composable
fun ScheduleLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
