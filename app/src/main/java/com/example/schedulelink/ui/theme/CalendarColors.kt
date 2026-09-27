package com.example.schedulelink.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 日曜(columnIndex=0)・土曜(columnIndex=6)の色。平日は[Color.Unspecified]を返すので、
 * 呼び出し側で `takeOrElse { ... }` を使って場面に応じた既定色にする。
 */
@Composable
fun weekendColor(columnIndex: Int): Color {
    val isDark = LocalIsDarkTheme.current
    return when (columnIndex) {
        0 -> if (isDark) SundayColorDark else SundayColorLight
        6 -> if (isDark) SaturdayColorDark else SaturdayColorLight
        else -> Color.Unspecified
    }
}
