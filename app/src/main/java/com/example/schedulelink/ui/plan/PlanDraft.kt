package com.example.schedulelink.ui.plan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

// 「表で計画」の下書き。紙に書くように行を並べ、5W1Hで整理してから大目的・中日程・予定に登録する。
// 行は字下げの深さ(level)だけを持つ平らな並びにし、親子は「直前にある1段浅い行」で決める
// (字下げの上げ下げや並べ替えが、行の数値を変えるだけで済むように)。

/** なぜ(大目的)・どう進める(中日程)・何を(予定)の3段。 */
enum class PlanLevel(val depth: Int, val label: String, val hint: String) {
    Why(0, "なぜ", "大目的"),
    How(1, "どう進める", "中日程"),
    What(2, "何を", "予定");

    companion object {
        fun ofDepth(depth: Int): PlanLevel = entries.first { it.depth == depth.coerceIn(0, 2) }
    }
}

data class PlanRow(
    val id: String = UUID.randomUUID().toString(),
    val level: PlanLevel,
    /** なぜ・どう進める・何を(段によって意味が変わる) */
    val title: String = "",
    /** いつ。予定は[start]の1日だけを使う。 */
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    /** 誰が(タグ) */
    val tagIds: List<String> = emptyList(),
    /** どこで(予定だけ) */
    val location: String = "",
    /** 登録済みの大目的・中日程を指すとき、そのID(この行は新しく作らず、下に追加する先になる)。 */
    val existingId: String? = null,
    /** ひと言入力で読み取った欄と、その元の語(キーは "when" / "who" / "where")。チップに✨を出し、×で元に戻す。 */
    val autoTokens: Map<String, String> = emptyMap()
)

/** 行の問題(登録前の確認で赤く出す)。 */
fun PlanRow.issue(parentLevelMissing: Boolean): String? = when {
    existingId != null -> null
    title.isBlank() -> "${level.label}を入力してください"
    parentLevelMissing && level == PlanLevel.How -> "上に「なぜ(大目的)」の行が必要です"
    parentLevelMissing && level == PlanLevel.What -> "上に「どう進める(中日程)」の行が必要です"
    start != null && end != null && end.isBefore(start) -> "終わりの日が始まりの日より前です"
    else -> null
}

/** 行ごとに、親の行(なければnull)を求める。 */
fun parentsOf(rows: List<PlanRow>): Map<String, PlanRow?> {
    var goal: PlanRow? = null
    var milestone: PlanRow? = null
    val result = mutableMapOf<String, PlanRow?>()
    rows.forEach { row ->
        when (row.level) {
            PlanLevel.Why -> {
                goal = row
                milestone = null
                result[row.id] = null
            }
            PlanLevel.How -> {
                milestone = row
                result[row.id] = goal
            }
            PlanLevel.What -> result[row.id] = milestone
        }
    }
    return result
}

/** 端末内に1つだけ保存する下書き。家族には見えない(書きかけを共有しないため)。 */
class PlanDraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("plan_table_draft", Context.MODE_PRIVATE)

    fun load(): List<PlanRow> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).toRow() }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(rows: List<PlanRow>) {
        val array = JSONArray()
        rows.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun PlanRow.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("level", level.depth)
        .put("title", title)
        .put("start", start?.toString() ?: "")
        .put("end", end?.toString() ?: "")
        .put("tagIds", JSONArray(tagIds))
        .put("location", location)
        .put("existingId", existingId ?: "")
        .put("autoTokens", JSONObject(autoTokens))

    private fun JSONObject.toRow(): PlanRow {
        val tags = optJSONArray("tagIds")
        return PlanRow(
            id = optString("id").ifBlank { UUID.randomUUID().toString() },
            level = PlanLevel.ofDepth(optInt("level")),
            title = optString("title"),
            start = optString("start").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            end = optString("end").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            tagIds = if (tags == null) emptyList() else (0 until tags.length()).map { tags.getString(it) },
            location = optString("location"),
            existingId = optString("existingId").takeIf { it.isNotBlank() },
            autoTokens = optJSONObject("autoTokens")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.optString(it) }
            }.orEmpty()
        )
    }

    private companion object {
        const val KEY = "rows"
    }
}
