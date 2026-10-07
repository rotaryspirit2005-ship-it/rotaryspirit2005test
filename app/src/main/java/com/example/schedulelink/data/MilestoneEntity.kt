package com.example.schedulelink.data

import java.time.LocalDate

enum class MilestoneStatus {
    UPCOMING,
    ACTIVE,
    DONE
}

data class MilestoneEntity(
    val id: String = "",
    val goalId: String = "",
    val title: String = "",
    val memo: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val status: MilestoneStatus = MilestoneStatus.UPCOMING,
    val orderIndex: Int = 0,
    val photoUrls: List<String> = emptyList(),
    /** 付けたタグ(誰の予定か)のID。タグ自体は families/{id}/tags にある。未設定は空。 */
    val tagIds: List<String> = emptyList()
)
