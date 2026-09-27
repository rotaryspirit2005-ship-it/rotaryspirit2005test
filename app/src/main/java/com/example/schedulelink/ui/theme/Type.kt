package com.example.schedulelink.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material3デフォルトの見出しスタイルはfontSize/lineHeightをそのまま使い、
// fontWeightだけ少し太くしてアプリ全体の見出しに統一感を持たせる
// (端末のフォントサイズ設定に追従できるよう、sp指定はデフォルトのまま変えない)。
private val defaultTypography = Typography()

val Typography = defaultTypography.copy(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = defaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = defaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
)

/** 時刻の桁幅を揃える(等幅数字)。予定一覧で時刻の縦位置がずれないようにする。 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = "tnum")
