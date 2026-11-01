package com.example.schedulelink.ui.plan

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

// 「AIで分解」: 大目的なら中日程と予定の案を、中日程なら予定の案をClaudeに出してもらう。
// 送るのはタイトルと日付だけ(誰が・どこでは送らない)。APIキーは利用者自身のもの。

private const val MODEL = "claude-opus-5-5"

private const val SYSTEM_PROMPT = """あなたは家族の予定づくりを手伝うアシスタントです。
利用者が書いた目的や段階を、実際に動ける具体的な段階(中日程)と予定に分解して提案します。
- 日本語で、短く具体的なタイトルにする(動詞で終わる「見積もりを依頼する」より、体言止めの「見積もり依頼」のように簡潔に)
- 日付は YYYY-MM-DD 形式。決めにくいときは空文字にする
- すでにある段階・予定と重ならないようにする
- 一般的な手順に沿い、現実的な順番と間隔にする"""

/** AIで分解できなかった理由(画面にそのまま出す文)。 */
class PlanAiException(message: String) : Exception(message)

/** 提案のまとまり。大目的の分解では「中日程1つ+その予定」、中日程の分解では「予定1つ」ずつ。 */
data class SuggestionGroup(val rows: List<PlanRow>)

private fun schema(): Map<String, JsonValue> {
    val schedule = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("title", "date"),
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "date" to mapOf("type" to "string")
        )
    )
    val milestone = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("title", "start", "end", "schedules"),
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "start" to mapOf("type" to "string"),
            "end" to mapOf("type" to "string"),
            "schedules" to mapOf("type" to "array", "items" to schedule)
        )
    )
    return mapOf(
        "type" to JsonValue.from("object"),
        "additionalProperties" to JsonValue.from(false),
        "required" to JsonValue.from(listOf("milestones")),
        "properties" to JsonValue.from(mapOf("milestones" to mapOf("type" to "array", "items" to milestone)))
    )
}

private fun parseDate(text: String): LocalDate? = try {
    if (text.isBlank()) null else LocalDate.parse(text.trim())
} catch (e: Exception) {
    null
}

/**
 * [anchor](大目的か中日程)を分解した案を返す。[context]は上位の大目的のタイトル(中日程の分解のとき)、
 * [existingChildren]はすでに下にある行のタイトル。通信はIOスレッドで行う。
 */
/** 送る前に、タイトルから「@場所」「#名前」の語を外す(場所と人は送らない約束のため)。 */
private fun privateFree(title: String): String =
    title.split(Regex("[\\s\u3000]+")).filterNot { it.startsWith("@") || it.startsWith("＠") || it.startsWith("#") || it.startsWith("＃") }
        .joinToString(" ")

suspend fun breakDownWithClaude(
    apiKey: String,
    anchor: PlanRow,
    anchorStart: LocalDate?,
    anchorEnd: LocalDate?,
    context: String?,
    existingChildren: List<String>,
    today: LocalDate = LocalDate.now()
): List<SuggestionGroup> = withContext(Dispatchers.IO) {
    val period = if (anchorStart != null) "期間: $anchorStart 〜 ${anchorEnd ?: anchorStart}" else "期間: 未定(今日から現実的な範囲で)"
    val existing = if (existingChildren.isEmpty()) "なし" else existingChildren.joinToString("、") { privateFree(it) }
    val prompt = if (anchor.level == PlanLevel.Why) {
        """今日は $today です。
大目的「${privateFree(anchor.title)}」を、3〜5個の中日程(進め方の段階)に分け、それぞれに2〜4個の予定を付けてください。
$period
すでにある中日程: $existing"""
    } else {
        """今日は $today です。
${context?.let { "大目的「${privateFree(it)}」の中の、" }.orEmpty()}中日程「${privateFree(anchor.title)}」でやることを、3〜6個の予定にしてください。
$period
すでにある予定: $existing
milestones には、この中日程「${privateFree(anchor.title)}」を1つだけ入れ、その schedules に予定を入れてください。"""
    }

    val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
    try {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .addUserMessage(prompt)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(
                        JsonOutputFormat.builder()
                            .schema(JsonOutputFormat.Schema.builder().putAllAdditionalProperties(schema()).build())
                            .build()
                    )
                    .build()
            )
            // 安全側の判定で断られたときは、サーバー側で別のモデルに切り替えて答えてもらう
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()
        val message = try {
            client.messages().create(params)
        } catch (e: AnthropicServiceException) {
            throw PlanAiException(
                when (e.statusCode()) {
                    401, 403 -> "APIキーが正しくないようです。「AIの設定」で確かめてください"
                    429 -> "利用の上限に達したか、混み合っています。少し待ってからお試しください"
                    in 500..599 -> "AIのサービスが混み合っています。少し待ってからお試しください"
                    else -> "AIに提案してもらえませんでした(${e.statusCode()})"
                }
            )
        }
        if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw PlanAiException("この内容ではAIが提案できませんでした。言い方を変えてお試しください")
        }
        val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        val milestones = try {
            JSONObject(text).getJSONArray("milestones")
        } catch (e: Exception) {
            throw PlanAiException("AIの答えを読み取れませんでした。もう一度お試しください")
        }
        val groups = mutableListOf<SuggestionGroup>()
        for (i in 0 until milestones.length()) {
            val m = milestones.getJSONObject(i)
            val schedules = m.optJSONArray("schedules")
            val scheduleRows = (0 until (schedules?.length() ?: 0)).mapNotNull { j ->
                val s = schedules!!.getJSONObject(j)
                val title = s.optString("title").trim()
                if (title.isEmpty()) null else PlanRow(level = PlanLevel.What, title = title, start = parseDate(s.optString("date")))
            }
            if (anchor.level == PlanLevel.Why) {
                val title = m.optString("title").trim()
                if (title.isEmpty()) continue
                val start = parseDate(m.optString("start"))
                val end = parseDate(m.optString("end"))?.takeIf { start == null || !it.isBefore(start) } ?: start
                groups += SuggestionGroup(listOf(PlanRow(level = PlanLevel.How, title = title, start = start, end = end)) + scheduleRows)
            } else {
                scheduleRows.forEach { groups += SuggestionGroup(listOf(it)) }
            }
        }
        if (groups.isEmpty()) throw PlanAiException("提案が見つかりませんでした。タイトルを具体的にしてお試しください")
        groups
    } catch (e: PlanAiException) {
        throw e
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw PlanAiException("通信できませんでした。通信状態を確かめてください")
    } finally {
        client.close()
    }
}
