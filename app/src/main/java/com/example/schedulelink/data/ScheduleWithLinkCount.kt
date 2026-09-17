package com.example.schedulelink.data

import androidx.room.Embedded

data class ScheduleWithLinkCount(
    @Embedded
    val schedule: ScheduleEntity,
    val linkedCount: Int
)
