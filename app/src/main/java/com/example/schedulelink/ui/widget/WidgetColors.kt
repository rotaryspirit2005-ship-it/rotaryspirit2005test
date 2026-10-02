package com.example.schedulelink.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider

// ウィジェットはアプリ本編の「手動ライト/ダーク切り替え」の外側(ホーム画面)で
// 描画されるため、端末のシステム設定(ダークモード)にだけ追従させる。
// 色の値はアプリ本体のColorScheme(ui/theme/Color.kt)と揃えている。

/** surface(ライト)/ surfaceContainer(ダーク) */
val WidgetBackground = ColorProvider(day = Color(0xFFF9FAF3), night = Color(0xFF1E201D))
/** onSurface */
val WidgetOnBackground = ColorProvider(day = Color(0xFF1A1C19), night = Color(0xFFE2E3DD))
/** onSurfaceVariant */
val WidgetSubText = ColorProvider(day = Color(0xFF424940), night = Color(0xFFC2C9BD))
/** primary */
val WidgetAccent = ColorProvider(day = Color(0xFF1B6D24), night = Color(0xFF88D982))
/** primaryの上に乗せる文字(onPrimary)。今日の日付の丸の中で使う。 */
val WidgetOnAccent = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF003909))
/** 区切り線(outlineVariant) */
val WidgetOutlineVariant = ColorProvider(day = Color(0xFFC2C9BD), night = Color(0xFF424940))
/** 選択中の日のセルの塗り(primaryContainer) */
val WidgetSelectedContainer = ColorProvider(day = Color(0xFFBCF0B4), night = Color(0xFF245024))
/** 選択中のセルの上の文字(onPrimaryContainer) */
val WidgetOnSelected = ColorProvider(day = Color(0xFF002204), night = Color(0xFFBCF0B4))
/** 期限切れなどの警告(アプリのerror色) */
val WidgetError = ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFFB4AB))
val WidgetSunday = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFFFB4AB))
val WidgetSaturday = ColorProvider(day = Color(0xFF1557B0), night = Color(0xFFA8C7FA))

/** 日曜(0)・土曜(6)は曜日色、平日は[weekdayDefault]を返す。 */
fun widgetWeekdayColor(columnIndex: Int, weekdayDefault: GlanceColorProvider): GlanceColorProvider = when (columnIndex) {
    0 -> WidgetSunday
    6 -> WidgetSaturday
    else -> weekdayDefault
}
