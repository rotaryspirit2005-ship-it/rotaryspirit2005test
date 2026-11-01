package com.example.schedulelink.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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

// 予定・中日程・大日程の編集で、日付を変えて保存するときに「リンクした予定も同じ日数ずらすか」を聞くための共通部品。

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

/** 確認ダイアログに並べる1件(ずらす前・後の日付の表示文字列つき)。 */
data class ShiftDialogItem(val id: String, val title: String, val before: String, val after: String)

/** 確認ダイアログの見出しごとのまとまり(大目的の「中日程」「予定」など)。[header]がnullなら見出しなし。 */
data class ShiftDialogGroup(val header: String?, val items: List<ShiftDialogItem>)

// ずらす・ずらさないのどちらを選んでも、もう一方にした場合の日付が分かるように両方を書く。
fun ScheduleEntity.toShiftDialogItem(days: Long, prefix: String? = null): ShiftDialogItem {
    val head = prefix?.let { "$it ・ " }.orEmpty()
    val old = date.format(shortDateFormatter)
    val new = date.plusDays(days).format(shortDateFormatter)
    return ShiftDialogItem(id = id, title = title, before = "$head${old}のまま(ずらすと$new)", after = "$head$old → $new")
}

fun MilestoneEntity.toShiftDialogItem(days: Long): ShiftDialogItem {
    fun range(offset: Long): String =
        "${startDate?.plusDays(offset)?.format(monthDayFormatter).orEmpty()}〜" +
            endDate?.plusDays(offset)?.format(monthDayFormatter).orEmpty()
    return ShiftDialogItem(
        id = id,
        title = title,
        before = "${range(0)}のまま(ずらすと${range(days)})",
        after = "${range(0)} → ${range(days)}"
    )
}

/**
 * 日付を変えて保存するとき、リンクした項目も同じ日数ずらすかを聞くダイアログ。
 * 最初は全件チェック済みで、外したものはずらさない(全部外せば本体だけ保存する)。[onSave]には
 * 見出しごとに選ばれたidを[groups]と同じ順で渡す。キャンセル(戻る・枠外タップ)は保存しない。
 * 一覧は開いた時点の内容で固定する(呼び出し側で作って渡す)ので、操作中に並びが変わらない。
 */
@Composable
fun ShiftLinkedDialog(
    title: String,
    days: Long,
    groups: List<ShiftDialogGroup>,
    onSave: (List<Set<String>>) -> Unit,
    onCancel: () -> Unit
) {
    // 外したものを「見出しの番号:id」で持つ(中日程と予定でidが重なっても混ざらないように)
    var excluded by remember(groups) { mutableStateOf(setOf<String>()) }
    fun key(groupIndex: Int, id: String) = "$groupIndex:$id"
    val selected = groups.mapIndexed { index, group ->
        group.items.map { it.id }.filter { key(index, it) !in excluded }.toSet()
    }
    val selectedCount = selected.sumOf { it.size }
    val totalCount = groups.sumOf { it.items.size }
    val direction = if (days > 0) "${days}日後へ" else "${-days}日前へ"

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "日付を${signedDays(days)}変更しました。次の${totalCount}件も同じ日数ずらせます(時刻はそのまま)。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = "日付を${direction}変更しました。次の${totalCount}件も同じ日数ずらせます。時刻はそのままです。"
                    }
                )
                groups.forEachIndexed { index, group ->
                    // 件数にかかわらず、まとめてオン/オフできるようにする
                    run {
                        val allSelected = group.items.all { key(index, it.id) !in excluded }
                        ShiftSectionHeader(
                            text = group.header ?: "ずらす予定",
                            allSelected = allSelected,
                            onToggleAll = {
                                val keys = group.items.map { key(index, it.id) }.toSet()
                                excluded = if (allSelected) excluded + keys else excluded - keys
                            }
                        )
                    }
                    group.items.forEach { item ->
                        val itemKey = key(index, item.id)
                        val checked = itemKey !in excluded
                        ShiftTargetRow(
                            title = item.title,
                            detail = if (checked) item.after else item.before,
                            highlighted = checked,
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                excluded = if (isChecked) excluded - itemKey else excluded + itemKey
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text(if (selectedCount > 0) "${selectedCount}件ずらして保存" else "ずらさずに保存")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("キャンセル") } }
    )
}
