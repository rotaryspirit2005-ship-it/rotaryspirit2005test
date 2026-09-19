package com.example.schedulelink.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

private fun DocumentSnapshot.toGoal(): GoalEntity = GoalEntity(
    id = id,
    title = getString("title").orEmpty(),
    memo = getString("memo").orEmpty(),
    type = getString("type")?.let { runCatching { GoalType.valueOf(it) }.getOrNull() } ?: GoalType.PHASED,
    startDate = getString("startDate")?.let { LocalDate.parse(it) },
    endDate = getString("endDate")?.let { LocalDate.parse(it) },
    photoUrls = (get("photoUrls") as? List<*>)?.filterIsInstance<String>().orEmpty()
)

private fun GoalEntity.toMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "memo" to memo,
    "type" to type.name,
    "startDate" to startDate?.toString(),
    "endDate" to endDate?.toString(),
    "photoUrls" to photoUrls
)

class GoalRepository(
    private val firestore: FirebaseFirestore,
    private val familyId: String
) {
    private val collection get() = firestore.collection("families").document(familyId).collection("goals")

    fun allGoals(): Flow<List<GoalEntity>> =
        collection.observeAsFlow().map { snap -> snap.documents.map { it.toGoal() } }

    fun goalById(id: String): Flow<GoalEntity?> =
        collection.document(id).observeAsFlow().map { snap -> if (snap.exists()) snap.toGoal() else null }

    suspend fun saveGoal(goal: GoalEntity): String {
        val docRef = if (goal.id.isBlank()) collection.document() else collection.document(goal.id)
        docRef.set(goal.toMap()).await()
        return docRef.id
    }

    suspend fun deleteGoal(id: String) {
        collection.document(id).delete().await()
    }
}
