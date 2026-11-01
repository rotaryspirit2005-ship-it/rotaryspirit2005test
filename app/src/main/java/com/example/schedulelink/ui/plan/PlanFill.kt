package com.example.schedulelink.ui.plan

// 「計画の具体度」: 各行の5W1Hがどれだけ埋まっているか。行ごとの6つの点と、全体の割合・次のひと言に使う。

/** 5W1Hの各欄。行の段によって対象外(null)になる欄がある。 */
enum class FillSlot(val label: String) { Why("なぜ"), How("どう"), What("何を"), When("いつ"), Who("誰が"), Where("どこで") }

/** 1行の埋まり具合。値がnullの欄はその行では対象外。 */
data class RowFill(val slots: Map<FillSlot, Boolean?>) {
    val filled: Int get() = slots.values.count { it == true }
    val applicable: Int get() = slots.values.count { it != null }
}

fun fillOf(row: PlanRow, parent: PlanRow?, grandParent: PlanRow?): RowFill = RowFill(
    when (row.level) {
        PlanLevel.Why -> mapOf(
            FillSlot.Why to row.title.isNotBlank(),
            FillSlot.How to null,
            FillSlot.What to null,
            FillSlot.When to (row.start != null),
            FillSlot.Who to row.tagIds.isNotEmpty(),
            FillSlot.Where to null
        )
        PlanLevel.How -> mapOf(
            FillSlot.Why to (parent != null),
            FillSlot.How to row.title.isNotBlank(),
            FillSlot.What to null,
            FillSlot.When to (row.start != null),
            FillSlot.Who to row.tagIds.isNotEmpty(),
            FillSlot.Where to null
        )
        PlanLevel.What -> mapOf(
            FillSlot.Why to (grandParent != null),
            FillSlot.How to (parent != null),
            FillSlot.What to row.title.isNotBlank(),
            FillSlot.When to (row.start != null),
            FillSlot.Who to row.tagIds.isNotEmpty(),
            FillSlot.Where to row.location.isNotBlank()
        )
    }
)

/** 次に埋めるとよい所(ヒント)。[rowId]はその最初の行、[slot]は開く欄。 */
data class FillHint(val text: String, val rowId: String?, val slot: FillSlot?)

data class PlanFillSummary(val ratio: Float, val fills: Map<String, RowFill>, val hint: FillHint)

/** 表全体の具体度と、優先順で1つだけのヒント(必須の不足 → いつ → 誰が → どこで)。登録済みの行は数えない。 */
fun summarizeFill(rows: List<PlanRow>): PlanFillSummary {
    val parents = parentsOf(rows)
    val targets = rows.filter { it.existingId == null }
    val fills = targets.associate { row ->
        val parent = parents[row.id]
        row.id to fillOf(row, parent, parent?.let { parents[it.id] })
    }
    val filled = fills.values.sumOf { it.filled }
    val applicable = fills.values.sumOf { it.applicable }
    val ratio = if (applicable == 0) 0f else filled.toFloat() / applicable

    fun missing(slot: FillSlot, level: PlanLevel? = null) = targets.filter { row ->
        (level == null || row.level == level) && fills[row.id]?.slots?.get(slot) == false
    }
    val noTitle = targets.filter { it.title.isBlank() }
    val orphans = targets.filter { it.level != PlanLevel.Why && parents[it.id] == null }
    val noWhen = missing(FillSlot.When, PlanLevel.What)
    val noWho = missing(FillSlot.Who, PlanLevel.What)
    val noWhere = missing(FillSlot.Where, PlanLevel.What)
    val hint = when {
        targets.isEmpty() -> FillHint("なぜ(大目的)から書いてみましょう", null, null)
        noTitle.isNotEmpty() -> FillHint("タイトルが空の行が${noTitle.size}件", noTitle.first().id, FillSlot.What)
        orphans.isNotEmpty() -> FillHint("上の段がない行が${orphans.size}件(字下げを直しましょう)", orphans.first().id, null)
        targets.none { it.level == PlanLevel.What } -> FillHint("何を(予定)を書くと、具体的になります", targets.last().id, null)
        noWhen.isNotEmpty() -> FillHint("いつが未定の予定が${noWhen.size}件", noWhen.first().id, FillSlot.When)
        noWho.isNotEmpty() -> FillHint("誰がが未定の予定が${noWho.size}件", noWho.first().id, FillSlot.Who)
        noWhere.isNotEmpty() -> FillHint("どこでが未定の予定が${noWhere.size}件", noWhere.first().id, FillSlot.Where)
        else -> FillHint("すべて具体的です！「確認へ」で登録できます", null, null)
    }
    return PlanFillSummary(ratio, fills, hint)
}
