package com.example.schedulelink.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.ScheduleEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// 予定・中日程・大日程の編集で、日付を変えたときに「リンクした予定も同じ日数ずらす」ための共通部品。

val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.JAPAN)

/** 「+2日」「−3日」(マイナスは−(U+2212)で、プラスと同じ幅に見えるようにする)。 */
fun signedDays(days: Long): String = if (days > 0) "+${days}日" else "−${-days}日"

/**
 * 開始日・終了日の変更が「同じ日数だけ平行移動」のときだけ、その日数を返す。
 * 片方だけ変えた(期間が伸び縮みした)・別々の日数で変えた・変えていない・日付がない
 * (新規作成や習慣型など)ときはnullで、ずらすオプションは出さない。
 */
fun rangeShiftDays(oldStart: LocalDate?, oldEnd: LocalDate?, newStart: LocalDate?, newEnd: LocalDate?): Long? {
    if (oldStart == null || oldEnd == null || newStart == null || newEnd == null) return null
    val startShift = ChronoUnit.DAYS.between(oldStart, newStart)
    val endShift = ChronoUnit.DAYS.between(oldEnd, newEnd)
    return if (startShift == endShift && startShift != 0L) startShift else null
}

/**
 * 「期間をずらしても使えない」ときの案内文。期間(開始・終了)が元から無い・習慣型になった・
 * まだ動かしていない・長さが変わった、のどれなのかを書き分ける。[targets]は「中日程・予定」など。
 */
fun periodShiftHint(
    oldStart: LocalDate?,
    oldEnd: LocalDate?,
    newStart: LocalDate?,
    newEnd: LocalDate?,
    targets: String
): String = when {
    oldStart == null || oldEnd == null -> "もともと期間が未設定のため、ずらせません"
    newStart == null || newEnd == null -> "継続する習慣には期間がないため、ずらせません"
    newStart == oldStart && newEnd == oldEnd -> "日付を変えると、${targets}も同じ日数ずらせます"
    else -> "開始日と終了日を同じ日数だけ動かすと使えます(いまは期間の長さが変わっています)"
}

/**
 * ずらすかどうかを選ぶ行。行全体を1つのスイッチとして操作できる(読み上げでも対象が重ならない)。
 * スイッチは常に操作できる。ずらせる状態でない間は、[hint]で理由を見せる(選んだ状態は保たれ、
 * 条件がそろった時点で効く)。[hint]がnullならずらせる状態。
 */
@Composable
fun ShiftLinkedRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, hint: String? = null) {
    val supporting = hint ?: "日付だけを同じ日数ずらします(時刻はそのまま)"
    InfoPanel(modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** ずらす対象を選ぶ1行。[highlighted]のとき([detail]がずらした後の日付のとき)は強調する。 */
@Composable
fun ShiftTargetRow(
    title: String,
    detail: String,
    highlighted: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox自身は行全体のtoggleableに任せる(タップ領域は行の高さ48dpで確保)。
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (highlighted) FontWeight.Bold else null,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 見出しと「すべて選択/解除」。 */
@Composable
fun ShiftSectionHeader(text: String, allSelected: Boolean, onToggleAll: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(text, modifier = Modifier.weight(1f))
        TextButton(onClick = onToggleAll) { Text(if (allSelected) "すべて解除" else "すべて選択") }
    }
}

/**
 * ずらす予定の一覧。最初は全件チェック済みで、外したものだけを[excludedIds]に持つ
 * (あとから増えた予定も自動でチェック済みになる)。ずらした後の日付を先に見せる。
 * [prefixOf]は日付の前に付ける補足(所属の中日程名など)。
 */
@Composable
fun ShiftScheduleChecklist(
    header: String,
    schedules: List<ScheduleEntity>,
    excludedIds: Set<String>,
    onExcludedChange: (Set<String>) -> Unit,
    days: Long,
    prefixOf: (ScheduleEntity) -> String? = { null }
) {
    val allSelected = schedules.all { it.id !in excludedIds }
    ShiftSectionHeader(
        text = header,
        allSelected = allSelected,
        onToggleAll = { onExcludedChange(if (allSelected) schedules.map { it.id }.toSet() else emptySet()) }
    )
    schedules.forEach { schedule ->
        val checked = schedule.id !in excludedIds
        val prefix = prefixOf(schedule)?.let { "$it ・ " }.orEmpty()
        ShiftTargetRow(
            title = schedule.title,
            detail = prefix + if (checked) {
                "${schedule.date.format(shortDateFormatter)} → ${schedule.date.plusDays(days).format(shortDateFormatter)}"
            } else {
                schedule.date.format(shortDateFormatter)
            },
            highlighted = checked,
            checked = checked,
            onCheckedChange = { isChecked ->
                onExcludedChange(if (isChecked) excludedIds - schedule.id else excludedIds + schedule.id)
            }
        )
    }
}

/** 大目的でずらす中日程の一覧(期間は「開始〜終了」の形で、ずらした後を先に見せる)。 */
@Composable
fun ShiftMilestoneChecklist(
    header: String,
    milestones: List<MilestoneEntity>,
    excludedIds: Set<String>,
    onExcludedChange: (Set<String>) -> Unit,
    days: Long
) {
    fun range(milestone: MilestoneEntity, offset: Long): String =
        "${milestone.startDate?.plusDays(offset)?.format(monthDayFormatter).orEmpty()}〜" +
            milestone.endDate?.plusDays(offset)?.format(monthDayFormatter).orEmpty()

    val allSelected = milestones.all { it.id !in excludedIds }
    ShiftSectionHeader(
        text = header,
        allSelected = allSelected,
        onToggleAll = { onExcludedChange(if (allSelected) milestones.map { it.id }.toSet() else emptySet()) }
    )
    milestones.forEach { milestone ->
        val checked = milestone.id !in excludedIds
        ShiftTargetRow(
            title = milestone.title,
            detail = if (checked) "${range(milestone, 0)} → ${range(milestone, days)}" else range(milestone, 0),
            highlighted = checked,
            checked = checked,
            onCheckedChange = { isChecked ->
                onExcludedChange(if (isChecked) excludedIds - milestone.id else excludedIds + milestone.id)
            }
        )
    }
}
