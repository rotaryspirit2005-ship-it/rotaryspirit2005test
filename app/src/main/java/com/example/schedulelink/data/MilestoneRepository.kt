package com.example.schedulelink.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
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
    photoUrls = (get("photoUrls") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    tagIds = (get("tagIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
)

private fun MilestoneEntity.toMap(): Map<String, Any?> = mapOf(
    "goalId" to goalId,
    "title" to title,
    "memo" to memo,
    "startDate" to startDate?.toString(),
    "endDate" to endDate?.toString(),
    "status" to status.name,
    "orderIndex" to orderIndex,
    "photoUrls" to photoUrls,
    "tagIds" to tagIds
)

class MilestoneRepository(
    private val firestore: FirebaseFirestore,
    private val familyId: String
) {
    private val collection get() = firestore.collection("families").document(familyId).collection("milestones")
    private val byOrder = compareBy<MilestoneEntity>({ it.orderIndex }, { it.id })

    fun milestonesForGoal(goalId: String): Flow<List<MilestoneEntity>> =
        collection.whereEqualTo("goalId", goalId).observeAsFlow().endOnPermissionDenied()
            .map { snap -> snap.documents.map { it.toMilestone() }.sortedWith(byOrder) }

    fun allMilestones(): Flow<List<MilestoneEntity>> =
        collection.observeAsFlow().endOnPermissionDenied().map { snap -> snap.documents.map { it.toMilestone() }.sortedWith(byOrder) }

    fun milestoneById(id: String): Flow<MilestoneEntity?> =
        collection.document(id).observeAsFlow().endOnPermissionDenied().map { snap -> if (snap.exists()) snap.toMilestone() else null }

    fun progressForGoal(goalId: String): Flow<GoalProgress> =
        milestonesForGoal(goalId).map { list ->
            GoalProgress(doneCount = list.count { it.status == MilestoneStatus.DONE }, totalCount = list.size)
        }

    suspend fun saveMilestone(milestone: MilestoneEntity): String {
        val docRef = if (milestone.id.isBlank()) collection.document() else collection.document(milestone.id)
        docRef.set(milestone.toMap()).await()
        return docRef.id
    }

    /**
     * 指定した中日程の開始日・終了日を[days]日ずらす(負なら前へ)。書き込みは完了を待たない
     * (オフラインで応答待ちになり保存まで止まらないようにするため)。日付のない側は触らず、
     * 見つからない・読めない中日程は飛ばす。中日程ごとに独立して書く。
     */
    suspend fun shiftMilestonesByDays(ids: Set<String>, days: Long) {
        ids.forEach { id ->
            val ref = collection.document(id)
            val snap = try {
                ref.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (snap != null && snap.exists()) {
                val milestone = snap.toMilestone()
                val updates = mutableMapOf<String, Any>()
                milestone.startDate?.let { updates["startDate"] = it.plusDays(days).toString() }
                milestone.endDate?.let { updates["endDate"] = it.plusDays(days).toString() }
                if (updates.isNotEmpty()) ref.update(updates)
            }
        }
    }

    suspend fun deleteMilestone(id: String) {
        collection.document(id).delete().await()
    }

    suspend fun deleteAllForGoal(goalId: String) {
        milestonesForGoal(goalId).first().forEach { deleteMilestone(it.id) }
    }
}
