package com.example.schedulelink.ui.help

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulelink.ui.theme.GoalColorDark
import com.example.schedulelink.ui.theme.GoalColorLight
import com.example.schedulelink.ui.theme.LocalIsDarkTheme
import com.example.schedulelink.ui.theme.MilestoneColorDark
import com.example.schedulelink.ui.theme.MilestoneColorLight
import com.example.schedulelink.ui.theme.PeerLinkColorDark
import com.example.schedulelink.ui.theme.PeerLinkColorLight
import com.example.schedulelink.ui.theme.ScheduleColorDark
import com.example.schedulelink.ui.theme.ScheduleColorLight
import com.example.schedulelink.ui.theme.TodayLineColorDark
import com.example.schedulelink.ui.theme.TodayLineColorLight
import com.example.schedulelink.ui.theme.tagColor

// 使い方ガイドの絵。画像素材は使わず、アプリの部品を簡略化した見本をComposeで描く。
// 色はすべてテーマと階層色から取るので、明るい/暗いテーマの両方にそのまま合う。
// 絵は飾りなので読み上げの対象にしない(説明は本文に書く)。

private data class ArtColors(
    val goal: Color,
    val milestone: Color,
    val schedule: Color,
    val today: Color,
    val link: Color,
    val isDark: Boolean
) {
    /** 階層色の上に載せる文字色(ライトは濃い色の上なので白、ダークは明るい色の上なので黒)。 */
    val onAccent: Color get() = if (isDark) Color.Black else Color.White
}

@Composable
private fun artColors(): ArtColors {
    val dark = LocalIsDarkTheme.current
    return ArtColors(
        goal = if (dark) GoalColorDark else GoalColorLight,
        milestone = if (dark) MilestoneColorDark else MilestoneColorLight,
        schedule = if (dark) ScheduleColorDark else ScheduleColorLight,
        today = if (dark) TodayLineColorDark else TodayLineColorLight,
        link = if (dark) PeerLinkColorDark else PeerLinkColorLight,
        isDark = dark
    )
}

@Composable
fun HelpIllustration(art: HelpArt, modifier: Modifier = Modifier) {
    val c = artColors()
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (art) {
            HelpArt.Hierarchy -> HierarchyDiagram(c, highlight = null)
            HelpArt.HierarchyGoal -> HierarchyDiagram(c, highlight = 0)
            HelpArt.HierarchyMilestone -> HierarchyDiagram(c, highlight = 1)
            HelpArt.HierarchySchedule -> HierarchyDiagram(c, highlight = 2)
            HelpArt.LinkChain -> LinkChainArt(c)
            HelpArt.DateShift -> DateShiftArt(c)
            HelpArt.Tags -> TagChipsArt(c)
            HelpArt.TagRemove -> TagRemoveArt(c)
            HelpArt.MonthSwipe -> MonthSwipeArt(c)
            HelpArt.Fab -> FabArt(c)
            HelpArt.MapMenu -> MapMenuArt()
            HelpArt.WeekCols -> WeekColsArt(c)
            HelpArt.WeekMore -> WeekMoreArt(c)
            HelpArt.DayList -> DayListArt(c)
            HelpArt.Tap -> TapArt(c)
            HelpArt.Checklist -> ChecklistArt(done = false)
            HelpArt.ChecklistDone -> ChecklistArt(done = true)
            HelpArt.Progress -> ProgressArt(c)
            HelpArt.Status -> StatusArt()
            HelpArt.Form -> FormArt()
            HelpArt.Swimlane -> SwimlaneArt(c)
            HelpArt.CollapseMemo -> CollapseMemoArt()
            HelpArt.FamilyCreate -> FamilyCreateArt()
            HelpArt.FamilyJoin -> FamilyJoinArt()
            HelpArt.ImportFile -> ImportArt(c, Icons.Default.FileOpen)
            HelpArt.ImportCalendar -> ImportArt(c, Icons.Default.Event)
        }
    }
}

// ---- 共通の小さな部品 ----

/** 文字の代わりの灰色の線。 */
@Composable
private fun TextBar(width: Dp, height: Dp = 6.dp, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(color, RoundedCornerShape(50))
    )
}

/** 左端に色の帯がある、予定カードの見本。 */
@Composable
private fun MiniCard(color: Color, width: Dp = 120.dp, label: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(width)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(6.dp)
                .fillMaxSize()
                .background(color, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (label != null) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            } else {
                TextBar(width * 0.55f, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            }
            TextBar(width * 0.35f)
        }
    }
}

/** 色付きの角丸ラベル(大目的・中日程・予定など)。 */
@Composable
private fun Pill(text: String, color: Color, onColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = onColor)
    }
}

/** 丸い背景に大きなアイコン。絵にしにくい操作の代わり。 */
@Composable
private fun IconBubble(icon: ImageVector, size: Dp = 64.dp, container: Color = MaterialTheme.colorScheme.primary, content: Color = MaterialTheme.colorScheme.onPrimary) {
    Box(
        modifier = Modifier.size(size).background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun ForwardArrow(color: Color = MaterialTheme.colorScheme.primary) {
    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
}

/** スマホ画面の外枠。中に縮小見本を入れる。 */
@Composable
private fun PhoneFrame(width: Dp = 120.dp, height: Dp = 150.dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(8.dp)
    ) {
        Column {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(width * 0.3f)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

// ---- 各ページの絵 ----

@Composable
private fun HierarchyDiagram(c: ArtColors, highlight: Int?) {
    val levels = listOf("大目的" to c.goal, "中日程" to c.milestone, "予定" to c.schedule)
    val line = MaterialTheme.colorScheme.outline
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        levels.forEachIndexed { index, (label, color) ->
            val dim = highlight != null && highlight != index
            if (index > 0) {
                // 親から子へ下りる線(L字)
                Canvas(
                    Modifier
                        .padding(start = (index - 1) * 36.dp + 18.dp)
                        .size(width = 36.dp, height = 18.dp)
                        .alpha(if (dim) 0.35f else 1f)
                ) {
                    val stroke = 2.dp.toPx()
                    drawLine(line, Offset(0f, 0f), Offset(0f, size.height / 2), stroke)
                    drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                }
            }
            Row(
                modifier = Modifier
                    .padding(start = index * 36.dp)
                    .alpha(if (dim) 0.35f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(label, color, c.onAccent)
                if (highlight == index) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Add, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LinkChainArt(c: ArtColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniCard(c.schedule, width = 96.dp, label = "見積もり")
        DashedLink(c.link)
        MiniCard(c.schedule, width = 96.dp, label = "契約")
    }
}

@Composable
private fun DashedLink(color: Color) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 44.dp, height = 12.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }
        Box(Modifier.size(26.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DayBox(month: String, day: String, accent: Color) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(2.dp, accent, RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(accent, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(month, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
        }
        Text(day, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DateShiftArt(c: ArtColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayBox("5月", "3", c.schedule)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("+3日", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                ForwardArrow()
            }
            DayBox("5月", "6", c.schedule)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.CheckBox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("リンクした予定もいっしょに", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.Link, contentDescription = null, tint = c.link, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TagChip(name: String, colorIndex: Int, c: ArtColors, modifier: Modifier = Modifier, strike: Boolean = false) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).background(tagColor(colorIndex, c.isDark), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (strike) TextDecoration.LineThrough else null
        )
    }
}

@Composable
private fun TagChipsArt(c: ArtColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip("パパ", 2, c)
            TagChip("ママ", 0, c)
            TagChip("たろう", 4, c)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniCard(tagColor(0, c.isDark), width = 110.dp, label = "保護者会")
            MiniCard(tagColor(4, c.isDark), width = 110.dp, label = "サッカー")
        }
    }
}

@Composable
private fun TagRemoveArt(c: ArtColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            TagChip("たろう", 4, c, strike = true)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(14.dp))
            }
        }
        ForwardArrow()
        Box {
            MiniCard(c.schedule, width = 104.dp, label = "サッカー")
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-8).dp).size(20.dp)
            )
        }
    }
}

@Composable
private fun MonthSwipeArt(c: ArtColors) {
    Box(contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(5) { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(7) { day ->
                        val isToday = week == 2 && day == 3
                        val hasEvent = (week * 7 + day) % 5 == 1
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .then(if (isToday) Modifier.border(2.dp, c.today, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(if (hasEvent) 8.dp else 5.dp)
                                    .background(
                                        if (hasEvent) c.schedule else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 28.dp, y = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun FabArt(c: ArtColors) {
    PhoneFrame {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniCard(c.schedule, width = 100.dp)
            MiniCard(c.milestone, width = 100.dp)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun MapMenuArt() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        IconBubble(Icons.Default.Flag)
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.End))
            listOf("やること", "家族", "タグ", "読み込み").forEach {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun WeekColsArt(c: ArtColors) {
    val days = listOf("日", "月", "火", "水", "木", "金", "土")
    val counts = listOf(0, 2, 1, 3, 1, 2, 1)
    val colors = listOf(c.schedule, c.milestone, c.goal)
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEachIndexed { i, d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                repeat(counts[i]) { k ->
                    Box(Modifier.size(width = 26.dp, height = 14.dp).background(colors[(i + k) % 3], RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

@Composable
private fun WeekMoreArt(c: ArtColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { Box(Modifier.size(width = 54.dp, height = 14.dp).background(c.schedule, RoundedCornerShape(4.dp))) }
            Text("ほか2件", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        ForwardArrow()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { MiniCard(c.schedule, width = 100.dp, modifier = Modifier.height(26.dp)) }
        }
    }
}

@Composable
private fun DayListArt(c: ArtColors) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("9:00" to c.schedule, "13:00" to c.milestone, "18:30" to c.schedule).forEach { (time, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                MiniCard(color, width = 140.dp)
            }
        }
    }
}

@Composable
private fun TapArt(c: ArtColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            MiniCard(c.schedule, width = 120.dp, label = "歯医者")
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 18.dp).size(34.dp)
            )
        }
        ForwardArrow()
        PhoneFrame(width = 96.dp, height = 120.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("歯医者", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                TextBar(60.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    TextBar(40.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = c.link, modifier = Modifier.size(14.dp))
                    TextBar(40.dp)
                }
            }
        }
    }
}

@Composable
private fun ChecklistArt(done: Boolean) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val items = listOf("書類をそろえる", "印鑑を持つ", "予約の電話")
        items.forEachIndexed { index, text ->
            val checked = done && index == 0 || index == 2 && done
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    fontSize = 13.sp,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (checked) TextDecoration.LineThrough else null
                )
            }
        }
    }
}

@Composable
private fun ProgressArt(c: ArtColors) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("資格をとる", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(10.dp)
                    .background(c.goal, RoundedCornerShape(50))
            )
        }
        Text("中日程 3/5 完了", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusArt() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(Icons.Outlined.RadioButtonUnchecked, "未着手", MaterialTheme.colorScheme.outline),
            Triple(Icons.Default.Loop, "進行中", MaterialTheme.colorScheme.tertiary),
            Triple(Icons.Default.CheckCircle, "完了", MaterialTheme.colorScheme.primary)
        ).forEach { (icon, label, color) ->
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                    .border(1.5.dp, color, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun FormField(label: String, highlighted: Boolean) {
    val color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .border(if (highlighted) 2.dp else 1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, fontSize = 10.sp, color = if (highlighted) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FormArt() {
    PhoneFrame(width = 140.dp, height = 156.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormField("タイトル", highlighted = true)
            FormField("日付", highlighted = true)
            FormField("メモ", highlighted = false)
            FormField("タグ", highlighted = false)
        }
    }
}

@Composable
private fun SwimlaneArt(c: ArtColors) {
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(width = 240.dp, height = 130.dp)) {
        drawRoundRect(surface, cornerRadius = CornerRadius(16.dp.toPx()))
        val pad = 12.dp.toPx()
        // 週の区切り線
        for (i in 1..5) {
            val x = pad + (size.width - pad * 2) * i / 6f
            drawLine(grid, Offset(x, pad), Offset(x, size.height - pad), 1.dp.toPx())
        }
        val laneH = 18.dp.toPx()
        val bars = listOf(
            Triple(0.05f, 0.80f, c.goal),
            Triple(0.15f, 0.45f, c.milestone),
            Triple(0.50f, 0.95f, c.milestone),
            Triple(0.30f, 0.38f, c.schedule)
        )
        val rows = listOf(0, 1, 1, 2)
        bars.forEachIndexed { i, (from, to, color) ->
            val w = size.width - pad * 2
            val y = pad + 8.dp.toPx() + rows[i] * (laneH + 10.dp.toPx())
            drawRoundRect(
                color,
                topLeft = Offset(pad + w * from, y),
                size = Size(w * (to - from), laneH),
                cornerRadius = CornerRadius(laneH / 2)
            )
        }
        // 今日の線(点線)
        val todayX = pad + (size.width - pad * 2) * 0.6f
        drawLine(
            c.today,
            Offset(todayX, pad / 2),
            Offset(todayX, size.height - pad / 2),
            strokeWidth = 2.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
        )
    }
}

@Composable
private fun CollapseMemoArt() {
    Column(
        modifier = Modifier
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TextBar(180.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        TextBar(170.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        TextBar(186.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        TextBar(120.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Text("続きを表示", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FamilyCreateArt() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBubble(Icons.Default.Groups)
        ForwardArrow()
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("招待コード", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("AB12CD", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun FamilyJoinArt() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PhoneFrame(width = 88.dp, height = 112.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("AB12CD", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                TextBar(50.dp)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text("共有", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        PhoneFrame(width = 88.dp, height = 112.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { Box(Modifier.fillMaxWidth().height(14.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))) }
            }
        }
    }
}

@Composable
private fun ImportArt(c: ArtColors, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBubble(icon, container = MaterialTheme.colorScheme.tertiary, content = MaterialTheme.colorScheme.onTertiary)
        ForwardArrow()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(true, false, true).forEach { checked ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    MiniCard(c.schedule, width = 96.dp, modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}
