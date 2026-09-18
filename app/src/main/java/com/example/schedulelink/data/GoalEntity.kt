package com.example.schedulelink.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class GoalType {
    /** 期間の決まった目的(例: 資格試験に合格する) */
    PHASED,
    /** 期限のない継続的な習慣目的(例: 健康的な生活を築く) */
    HABIT
}

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val memo: String = "",
    val type: GoalType,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)
