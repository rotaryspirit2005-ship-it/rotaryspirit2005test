package com.example.schedulelink.ui.plan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

// 「表で計画」のひな形。よくある計画の骨組みを、基準日からの日数で持つ。
// 自分で作った表も、ひな形として端末に保存できる(家族には共有しない)。

/** ひな形の1行。日付は基準日からの日数(nullは日付なし)。 */
data class TemplateItem(val level: PlanLevel, val title: String, val start: Int? = null, val end: Int? = null)

data class PlanTemplate(
    val id: String,
    val name: String,
    /** 基準日の意味(例: 「引越し当日」)。 */
    val baseLabel: String,
    val items: List<TemplateItem>,
    val builtIn: Boolean = true
)

/** 基準日に合わせて、下書きの行にする。 */
fun PlanTemplate.toRows(base: LocalDate): List<PlanRow> = items.map { item ->
    PlanRow(
        level = item.level,
        title = item.title,
        start = item.start?.let { base.plusDays(it.toLong()) },
        end = if (item.level == PlanLevel.What) null else item.end?.let { base.plusDays(it.toLong()) }
    )
}

private fun goal(title: String) = TemplateItem(PlanLevel.Why, title)
private fun stage(title: String, start: Int, end: Int) = TemplateItem(PlanLevel.How, title, start, end)
private fun todo(title: String, day: Int) = TemplateItem(PlanLevel.What, title, day)

val BUILT_IN_TEMPLATES = listOf(
    PlanTemplate(
        "moving", "引越し", "引越し当日",
        listOf(
            goal("引越し"),
            stage("業者選び", -40, -28), todo("見積もり依頼", -40), todo("業者決定", -30),
            stage("手続き", -21, -1), todo("転出届", -14), todo("ネット回線の移転", -10), todo("郵便の転送届", -7),
            stage("荷造り", -14, -1), todo("段ボールを用意", -14), todo("荷造りを終える", -2),
            stage("当日・引越し後", 0, 14), todo("引越し", 0), todo("転入届", 7)
        )
    ),
    PlanTemplate(
        "house", "家づくり", "着工日",
        listOf(
            goal("家づくり"),
            stage("土地・資金", -180, -90), todo("ローン事前審査", -150), todo("土地の契約", -100),
            stage("設計", -90, -14), todo("間取り確定", -45), todo("仕様決め", -30),
            stage("工事", 0, 120), todo("着工", 0), todo("上棟", 45),
            stage("引渡し", 120, 130), todo("引渡し", 120)
        )
    ),
    PlanTemplate(
        "trip", "旅行", "出発日",
        listOf(
            goal("旅行"),
            stage("計画", -60, -30), todo("行き先を決める", -60), todo("宿を予約", -45), todo("交通を予約", -40),
            stage("準備", -14, -1), todo("持ち物リスト", -7), todo("荷造り", -2),
            stage("旅行中", 0, 2), todo("出発", 0)
        )
    ),
    PlanTemplate(
        "exam", "受験", "試験本番",
        listOf(
            goal("受験"),
            stage("基礎固め", -180, -90), todo("参考書を決める", -180),
            stage("過去問", -90, -30), todo("模試", -60), todo("願書提出", -40),
            stage("直前", -30, -1), todo("受験票を確認", -7), todo("会場の下見", -5),
            stage("本番・発表", 0, 14), todo("試験", 0), todo("合格発表", 14)
        )
    ),
    PlanTemplate(
        "baby", "出産準備", "出産予定日",
        listOf(
            goal("出産準備"),
            stage("準備", -120, -30), todo("ベビー用品をそろえる", -60), todo("入院バッグ", -30),
            stage("手続き", 0, 14), todo("出生届", 13), todo("児童手当の申請", 14)
        )
    ),
    PlanTemplate(
        "school", "子どもの入学", "入学式",
        listOf(
            goal("入学準備"),
            stage("用品", -90, -14), todo("ランドセル", -90), todo("制服・体操服", -45),
            stage("書類", -30, -7), todo("提出書類を書く", -20),
            stage("入学式", 0, 0), todo("入学式", 0)
        )
    )
)

/** 今の表をひな形にする。基準日は表の中でいちばん早い日付。日付が1つもなければnull。 */
fun templateFromRows(name: String, rows: List<PlanRow>): PlanTemplate? {
    val target = rows.filter { it.existingId == null && it.title.isNotBlank() }
    val base = target.flatMap { listOfNotNull(it.start, it.end) }.minOrNull() ?: return null
    fun offset(date: LocalDate?) = date?.let { ChronoUnit.DAYS.between(base, it).toInt() }
    return PlanTemplate(
        id = UUID.randomUUID().toString(),
        name = name,
        baseLabel = "最初の日",
        items = target.map { TemplateItem(it.level, it.title, offset(it.start), offset(it.end)) },
        builtIn = false
    )
}

/** 自分で作ったひな形の保存先(端末内)。 */
class PlanTemplateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("plan_templates", Context.MODE_PRIVATE)

    fun load(): List<PlanTemplate> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val items = obj.getJSONArray("items")
            PlanTemplate(
                id = obj.getString("id"),
                name = obj.getString("name"),
                baseLabel = obj.optString("baseLabel", "最初の日"),
                items = (0 until items.length()).map { j ->
                    val item = items.getJSONObject(j)
                    TemplateItem(
                        level = PlanLevel.ofDepth(item.optInt("level")),
                        title = item.optString("title"),
                        start = if (item.has("start")) item.getInt("start") else null,
                        end = if (item.has("end")) item.getInt("end") else null
                    )
                },
                builtIn = false
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(template: PlanTemplate) = write(load() + template)

    fun delete(id: String) = write(load().filterNot { it.id == id })

    private fun write(templates: List<PlanTemplate>) {
        val array = JSONArray()
        templates.forEach { t ->
            val items = JSONArray()
            t.items.forEach { item ->
                items.put(
                    JSONObject().put("level", item.level.depth).put("title", item.title).apply {
                        item.start?.let { put("start", it) }
                        item.end?.let { put("end", it) }
                    }
                )
            }
            array.put(JSONObject().put("id", t.id).put("name", t.name).put("baseLabel", t.baseLabel).put("items", items))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "templates"
    }
}
