package com.example.schedulelink.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 2つの行動予定(ScheduleEntity)を関連付けるリンク。
 * 双方向に検索できるよう、リンク作成時に (A,B) と (B,A) の両方の行を挿入する。
 */
@Entity(
    tableName = "schedule_links",
    primaryKeys = ["scheduleId", "linkedScheduleId"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedScheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scheduleId"), Index("linkedScheduleId")]
)
data class ScheduleLinkCrossRef(
    val scheduleId: Long,
    val linkedScheduleId: Long
)
