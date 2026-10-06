package com.example.schedulelink.ui.theme

import androidx.compose.ui.graphics.Color

// ブランドの緑(#2E7D32)をシードに、HCT(Material Color Utilities)で導出したトーナルパレット。
// Material3のColorSchemeは指定しなかったロールを既定の紫で埋めるため、全ロールを明示する。
// ライトのprimaryはシードそのもの(T46)ではなくT40にしている: T46だと色付きの面の上で
// primary色の文字がWCAG AA(4.5:1)を割るため。

val LightPrimary = Color(0xFF1B6D24)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFBCF0B4)
val LightOnPrimaryContainer = Color(0xFF002204)
val LightInversePrimary = Color(0xFF88D982)
val LightSecondary = Color(0xFF52634F)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD6E8CE)
val LightOnSecondaryContainer = Color(0xFF111F0F)
val LightTertiary = Color(0xFF38656A)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFBCEBF0)
val LightOnTertiaryContainer = Color(0xFF002023)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFF9FAF3)
val LightOnBackground = Color(0xFF1A1C19)
val LightSurface = Color(0xFFF9FAF3)
val LightOnSurface = Color(0xFF1A1C19)
val LightSurfaceVariant = Color(0xFFDEE5D8)
val LightOnSurfaceVariant = Color(0xFF424940)
val LightOutline = Color(0xFF72796F)
val LightOutlineVariant = Color(0xFFC2C9BD)
val LightInverseSurface = Color(0xFF2F312D)
val LightInverseOnSurface = Color(0xFFF0F1EB)
val LightSurfaceBright = Color(0xFFF9FAF3)
val LightSurfaceDim = Color(0xFFDADAD4)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF3F4EE)
val LightSurfaceContainer = Color(0xFFEEEEE8)
val LightSurfaceContainerHigh = Color(0xFFE8E9E2)
val LightSurfaceContainerHighest = Color(0xFFE2E3DD)

val DarkPrimary = Color(0xFF88D982)
val DarkOnPrimary = Color(0xFF003909)
val DarkPrimaryContainer = Color(0xFF245024)
val DarkOnPrimaryContainer = Color(0xFFBCF0B4)
val DarkInversePrimary = Color(0xFF1B6D24)
val DarkSecondary = Color(0xFFBACCB3)
val DarkOnSecondary = Color(0xFF253423)
val DarkSecondaryContainer = Color(0xFF3B4B38)
val DarkOnSecondaryContainer = Color(0xFFD6E8CE)
val DarkTertiary = Color(0xFFA0CFD4)
val DarkOnTertiary = Color(0xFF00363B)
val DarkTertiaryContainer = Color(0xFF1F4D52)
val DarkOnTertiaryContainer = Color(0xFFBCEBF0)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF121411)
val DarkOnBackground = Color(0xFFE2E3DD)
val DarkSurface = Color(0xFF121411)
val DarkOnSurface = Color(0xFFE2E3DD)
val DarkSurfaceVariant = Color(0xFF424940)
val DarkOnSurfaceVariant = Color(0xFFC2C9BD)
val DarkOutline = Color(0xFF8C9388)
val DarkOutlineVariant = Color(0xFF424940)
val DarkInverseSurface = Color(0xFFE2E3DD)
val DarkInverseOnSurface = Color(0xFF2F312D)
val DarkSurfaceBright = Color(0xFF383A36)
val DarkSurfaceDim = Color(0xFF121411)
val DarkSurfaceContainerLowest = Color(0xFF0C0F0C)
val DarkSurfaceContainerLow = Color(0xFF1A1C19)
val DarkSurfaceContainer = Color(0xFF1E201D)
val DarkSurfaceContainerHigh = Color(0xFF282B27)
val DarkSurfaceContainerHighest = Color(0xFF333531)

// 全体マップ・フロー表示で使う「階層」や「つながり」を示す意味を持つアクセント色。
// ライトは色付きノード上の文字でもAAを満たす濃さ、ダークはMaterial3のダーク配色に
// 合わせたパステル寄り(T80前後)にしている。
val GoalColorLight = Color(0xFF825500)
val GoalColorDark = Color(0xFFFBBB4A)
val MilestoneColorLight = Color(0xFF006B5F)
val MilestoneColorDark = Color(0xFF51DBCC)
val ScheduleColorLight = Color(0xFF1557B0)
val ScheduleColorDark = Color(0xFFA9C7FF)
val TodayLineColorLight = Color(0xFFD32F2F)
val TodayLineColorDark = Color(0xFFFF897D)
val PeerLinkColorLight = Color(0xFF9C1AAD)
val PeerLinkColorDark = Color(0xFFF8ADFB)

// 全体マップのタイムラインで、同じ親から出る線をひとまとまりに見せるための色。階層色(橙・ティール・青)
// や今日の線(赤)、予定どうしのリンク(赤紫)と間違えにくい色相を、明度も交互にして並べている。
// ライトは背景に対して十分なコントラストが出る濃さ、ダークはパステル寄り。
private val ConnectorPaletteLight = listOf(
    Color(0xFF8A7A00), Color(0xFF1F7A3A), Color(0xFF0B6AA0), Color(0xFF5B4BC4),
    Color(0xFFB8431F), Color(0xFF7A6240), Color(0xFF00798A), Color(0xFF546E7A)
)
private val ConnectorPaletteDark = listOf(
    Color(0xFFE6D84A), Color(0xFF6FD98B), Color(0xFF5CC8FF), Color(0xFFA59CFF),
    Color(0xFFFF9B7A), Color(0xFFD8BE8F), Color(0xFF4FD1D9), Color(0xFFB0BEC5)
)

/** 親ごとの線の色。[slot]が色の数を超えたら循環する。 */
fun connectorColor(slot: Int, isDark: Boolean): Color {
    val palette = if (isDark) ConnectorPaletteDark else ConnectorPaletteLight
    return palette[Math.floorMod(slot, palette.size)]
}

// 曜日色。ダークでは選択セル(primaryContainer)の上でも読める明るさにしている。
val SundayColorLight = Color(0xFFB3261E)
val SundayColorDark = Color(0xFFFFB4AB)
val SaturdayColorLight = Color(0xFF1557B0)
val SaturdayColorDark = Color(0xFFA8C7FA)
