package com.example.schedulelink.data

import java.time.LocalDate
import java.time.LocalTime

data class ScheduleEntity(
    val id: String = "",
    val title: String = "",
    val memo: String = "",
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.MIDNIGHT,
    val endTime: LocalTime = LocalTime.MIDNIGHT,
    /** この小日程が紐づく中日程(任意)。 */
    val milestoneId: String? = null,
    /** 双方向にリンクされた他の小日程のID一覧。 */
    val linkedIds: List<String> = emptyList()
)

/** 一覧画面での表示用。リンク数はlinkedIdsの件数からその場で求められる。 */
data class ScheduleWithLinkCount(val schedule: ScheduleEntity, val linkedCount: Int)

/** 日表示のフロー風レイアウトで使う、リンク先の詳細(時刻・タイトル)まで含めた表示用データ。 */
data class ScheduleWithLinks(val schedule: ScheduleEntity, val linkedSchedules: List<ScheduleEntity>)
