package com.example.schedulelink.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class MilestoneStatus {
    UPCOMING,
    ACTIVE,
    DONE
}

@Entity(
    tableName = "milestones",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId")]
)
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val goalId: Long,
    val title: String,
    val memo: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val status: MilestoneStatus = MilestoneStatus.UPCOMING,
    val orderIndex: Int = 0
)
