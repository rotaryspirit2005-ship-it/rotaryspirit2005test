package com.example.schedulelink.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

data class GoalProgress(val doneCount: Int, val totalCount: Int) {
    val ratio: Float get() = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount
}

private fun DocumentSnapshot.toMilestone(): MilestoneEntity = MilestoneEntity(
    id = id,
    goalId = getString("goalId").orEmpty(),
    title = getString("title").orEmpty(),
    memo = getString("memo").orEmpty(),
    startDate = getString("startDate")?.let { LocalDate.parse(it) },
    endDate = getString("endDate")?.let { LocalDate.parse(it) },
    status = getString("status")?.let { runCatching { MilestoneStatus.valueOf(it) }.getOrNull() } ?: MilestoneStatus.UPCOMING,
    orderIndex = (getLong("orderIndex") ?: 0L).toInt(),
    photoUrls = (get("photoUrls") as? List<*>)?.filterIsInstance<String>().orEmpty()
)

private fun MilestoneEntity.toMap(): Map<String, Any?> = mapOf(
    "goalId" to goalId,
    "title" to title,
    "memo" to memo,
    "startDate" to startDate?.toString(),
    "endDate" to endDate?.toString(),
    "status" to status.name,
    "orderIndex" to orderIndex,
    "photoUrls" to photoUrls
)

class MilestoneRepository(
    private val firestore: FirebaseFirestore,
    private val familyId: String
) {
    private val collection get() = firestore.collection("families").document(familyId).collection("milestones")
    private val byOrder = compareBy<MilestoneEntity>({ it.orderIndex }, { it.id })

    fun milestonesForGoal(goalId: String): Flow<List<MilestoneEntity>> =
        collection.whereEqualTo("goalId", goalId).observeAsFlow()
            .map { snap -> snap.documents.map { it.toMilestone() }.sortedWith(byOrder) }

    fun allMilestones(): Flow<List<MilestoneEntity>> =
        collection.observeAsFlow().map { snap -> snap.documents.map { it.toMilestone() }.sortedWith(byOrder) }

    fun milestoneById(id: String): Flow<MilestoneEntity?> =
        collection.document(id).observeAsFlow().map { snap -> if (snap.exists()) snap.toMilestone() else null }

    fun progressForGoal(goalId: String): Flow<GoalProgress> =
        milestonesForGoal(goalId).map { list ->
            GoalProgress(doneCount = list.count { it.status == MilestoneStatus.DONE }, totalCount = list.size)
        }

    suspend fun saveMilestone(milestone: MilestoneEntity): String {
        val docRef = if (milestone.id.isBlank()) collection.document() else collection.document(milestone.id)
        docRef.set(milestone.toMap()).await()
        return docRef.id
    }

    suspend fun deleteMilestone(id: String) {
        collection.document(id).delete().await()
    }

    suspend fun deleteAllForGoal(goalId: String) {
        milestonesForGoal(goalId).first().forEach { deleteMilestone(it.id) }
    }
}
