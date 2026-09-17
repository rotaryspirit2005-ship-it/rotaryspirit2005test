package com.example.schedulelink.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val memo: String = "",
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime
)
