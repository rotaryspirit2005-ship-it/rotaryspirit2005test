package com.example.schedulelink.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = MilestoneEntity::class,
            parentColumns = ["id"],
            childColumns = ["milestoneId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("milestoneId")]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val memo: String = "",
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    /** この小日程が紐づく中日程(任意)。 */
    val milestoneId: Long? = null
)
