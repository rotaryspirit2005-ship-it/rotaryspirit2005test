package com.example.schedulelink.data

import java.time.LocalDate

enum class GoalType {
    /** 期間の決まった目的(例: 資格試験に合格する) */
    PHASED,
    /** 期限のない継続的な習慣目的(例: 健康的な生活を築く) */
    HABIT
}

data class GoalEntity(
    val id: String = "",
    val title: String = "",
    val memo: String = "",
    val type: GoalType = GoalType.PHASED,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val photoUrls: List<String> = emptyList()
)
