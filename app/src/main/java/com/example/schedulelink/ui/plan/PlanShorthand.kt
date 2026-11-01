package com.example.schedulelink.ui.plan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// 「ひと言入力」: タイトル欄に「見積もり依頼 来週金曜 @駅前 #パパ」のように書くと、
// 日付・場所・誰が(タグ)を読み取って各欄に入れる。読み取れなかった語はタイトルに残す。
// 端末の中だけで処理する(外部には送らない)。

data class ParsedShorthand(
    val title: String,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val location: String? = null,
    val tagIds: List<String> = emptyList(),
    /** 読み取った元の語(「いつ」「どこで」「誰が」ごと)。取り消したときにタイトルへ戻すため。 */
    val dateText: String? = null,
    val locationText: String? = null,
    val tagText: String? = null
) {
    val foundAnything: Boolean get() = start != null || location != null || tagIds.isNotEmpty()
}

/** 読み取りに使うタグ(IDと名前)。 */
data class ShorthandTag(val id: String, val name: String)

private val SEPARATORS = Regex("[\\s\u3000]+")
private val RANGE_MARKS = Regex("[-〜~～]")
private val WEEKDAYS = mapOf(
    '月' to DayOfWeek.MONDAY, '火' to DayOfWeek.TUESDAY, '水' to DayOfWeek.WEDNESDAY,
    '木' to DayOfWeek.THURSDAY, '金' to DayOfWeek.FRIDAY, '土' to DayOfWeek.SATURDAY, '日' to DayOfWeek.SUNDAY
)

/** 全角の数字・記号を半角にそろえる(「１１／３」も読めるように)。 */
private fun normalize(text: String): String = buildString {
    text.forEach { ch ->
        append(
            when (ch) {
                in '０'..'９' -> '0' + (ch - '０')
                '／' -> '/'
                '－', 'ー' -> if (isEmpty() || !last().isDigit()) ch else '-'
                else -> ch
            }
        )
    }
}

/** 日付を表す1語を読む。読めなければnull。 */
fun parseDateWord(raw: String, today: LocalDate): LocalDate? {
    val word = normalize(raw.trim()).removeSuffix("まで").removeSuffix("に").removeSuffix("から")
    when (word) {
        "今日", "きょう" -> return today
        "明日", "あした", "あす" -> return today.plusDays(1)
        "明後日", "あさって" -> return today.plusDays(2)
        "昨日", "きのう" -> return today.minusDays(1)
        "月末", "今月末" -> return today.with(TemporalAdjusters.lastDayOfMonth())
        "来月末" -> return today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
        "来月" -> return today.plusMonths(1).withDayOfMonth(1)
        "来週" -> return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    }
    Regex("^(\\d+)日後$").matchEntire(word)?.let { return today.plusDays(it.groupValues[1].toLong()) }
    Regex("^(\\d+)週間?後$").matchEntire(word)?.let { return today.plusWeeks(it.groupValues[1].toLong()) }
    Regex("^(\\d+)[かヶケ]?月後$").matchEntire(word)?.let { return today.plusMonths(it.groupValues[1].toLong()) }

    // 曜日: 「金曜」「金曜日」「今週金曜」「来週金曜」「再来週の金曜」
    Regex("^(今週|来週|再来週)?の?([月火水木金土日])曜日?$").matchEntire(word)?.let { m ->
        val day = WEEKDAYS.getValue(m.groupValues[2][0])
        val thisWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when (m.groupValues[1]) {
            "今週" -> thisWeekMonday.with(TemporalAdjusters.nextOrSame(day))
            "来週" -> thisWeekMonday.plusWeeks(1).with(TemporalAdjusters.nextOrSame(day))
            "再来週" -> thisWeekMonday.plusWeeks(2).with(TemporalAdjusters.nextOrSame(day))
            else -> today.with(TemporalAdjusters.nextOrSame(day))
        }
    }

    // 年月日: 「2026/11/3」「2026-11-3」「2026年11月3日」
    Regex("^(\\d{4})[/年.-](\\d{1,2})[/月.-](\\d{1,2})日?(\\(.\\))?$").matchEntire(word)?.let { m ->
        return safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
    // 月日: 「11/3」「11月3日」「11/3(火)」。年がなければ、もう過ぎた日付(2か月より前)は来年とみなす
    Regex("^(\\d{1,2})[/月](\\d{1,2})日?(\\(.\\))?$").matchEntire(word)?.let { m ->
        val date = safeDate(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) ?: return null
        return if (date.isBefore(today.minusMonths(2))) date.plusYears(1) else date
    }
    // 日だけ: 「15日」→ 今月(過ぎていれば来月)の15日
    Regex("^(\\d{1,2})日$").matchEntire(word)?.let { m ->
        val day = m.groupValues[1].toInt()
        val date = safeDate(today.year, today.monthValue, day) ?: return null
        return if (date.isBefore(today)) safeDate(today.plusMonths(1).year, today.plusMonths(1).monthValue, day) else date
    }
    return null
}

private fun safeDate(year: Int, month: Int, day: Int): LocalDate? = try {
    LocalDate.of(year, month, day)
} catch (e: Exception) {
    null
}

/**
 * タイトル欄の文を読み取る。日付は1つ(予定)か範囲(「11/1〜11/15」「11/1 〜 11/15」)。
 * 「@」の後ろは場所、「#」の後ろはタグ名(知っているタグだけ)。それ以外はタイトルに残す。
 */
fun parseShorthand(text: String, tags: List<ShorthandTag>, today: LocalDate = LocalDate.now()): ParsedShorthand {
    val words = text.trim().split(SEPARATORS).filter { it.isNotEmpty() }
    val titleWords = mutableListOf<String>()
    val dates = mutableListOf<LocalDate>()
    var location: String? = null
    val tagIds = mutableListOf<String>()
    val dateWords = mutableListOf<String>()
    var locationWord: String? = null
    val tagWords = mutableListOf<String>()
    var expectRangeEnd = false

    words.forEach { word ->
        val first = word.first()
        when {
            (first == '@' || first == '＠') && word.length > 1 -> {
                location = word.drop(1)
                locationWord = word
            }
            (first == '#' || first == '＃') && word.length > 1 -> {
                val name = word.drop(1)
                val tag = tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (tag != null) {
                    if (tag.id !in tagIds) tagIds += tag.id
                    tagWords += word
                } else {
                    titleWords += word
                }
            }
            RANGE_MARKS.matches(normalize(word)) && dates.size == 1 -> {
                expectRangeEnd = true
                dateWords += word
            }
            else -> {
                // 「11/1〜11/15」のように1語に範囲が入っているとき
                val parts = normalize(word).split(Regex("[〜~～]|(?<=\\d)-(?=\\d{1,2}[/月])"))
                val parsed = parts.map { parseDateWord(it, today) }
                when {
                    dates.size < 2 && parts.size == 2 && parsed.all { it != null } -> {
                        dates.clear()
                        dates += parsed.filterNotNull()
                        dateWords.clear()
                        dateWords += word
                    }
                    dates.size < 2 && parts.size == 1 && parsed[0] != null &&
                        (dates.isEmpty() || expectRangeEnd) -> {
                        dates += parsed[0]!!
                        dateWords += word
                        expectRangeEnd = false
                    }
                    else -> titleWords += word
                }
            }
        }
    }
    val start = dates.minOrNull()
    val end = if (dates.size >= 2) dates.max() else null
    return ParsedShorthand(
        title = titleWords.joinToString(" "),
        start = start,
        end = end,
        location = location,
        tagIds = tagIds,
        dateText = dateWords.takeIf { dates.isNotEmpty() }?.joinToString(" "),
        locationText = locationWord,
        tagText = tagWords.takeIf { it.isNotEmpty() }?.joinToString(" ")
    )
}
