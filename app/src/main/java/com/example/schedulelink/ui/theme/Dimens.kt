package com.example.schedulelink.ui.theme

import androidx.compose.ui.unit.dp

/** 画面全体で揃える余白。半端な値(6/10/14/20dp)は使わずこのスケールに寄せる。 */
object Dimens {
    val ScreenPadding = 16.dp
    val PanelPadding = 16.dp
    val ListItemPaddingH = 16.dp
    val ListItemPaddingV = 12.dp
    val ListGap = 8.dp
    val FormGap = 12.dp
    val SectionGap = 24.dp

    /** FABのある画面で、最後の項目がFABに隠れないためのリスト下余白(56 + 16 + 16)。 */
    val FabClearance = 88.dp
}
