package com.example.schedulelink.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider

/**
 * ウィジェットはアプリ本編の「手動ライト/ダーク切り替え」の外側(ホーム画面)で
 * 描画されるため、端末のシステム設定(ダークモード)にだけ追従させる。
 */
val WidgetBackground = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF242722))
val WidgetOnBackground = ColorProvider(day = Color(0xFF1A1C19), night = Color(0xFFE2E3DE))
val WidgetSubText = ColorProvider(day = Color(0xFF6B6B6B), night = Color(0xFFB0B0B0))
val WidgetAccent = ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF81C995))
/** 選択中の日のセル(WidgetAccent地)に重ねる文字色。背景の濃さがライト/ダークで逆転するため、
 * 常にコントラストが保てるよう明暗を切り替える(Material3のonPrimaryと同じ考え方)。 */
val WidgetSelectedText = ColorProvider(day = Color.White, night = Color(0xFF1A1C19))
