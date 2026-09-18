package com.example.schedulelink.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime

private fun DocumentSnapshot.toSchedule(): ScheduleEntity = ScheduleEntity(
    id = id,
    title = getString("title").orEmpty(),
    memo = getString("memo").orEmpty(),
    date = getString("date")?.let { LocalDate.parse(it) } ?: LocalDate.now(),
    startTime = getString("startTime")?.let { LocalTime.parse(it) } ?: LocalTime.MIDNIGHT,
    endTime = getString("endTime")?.let { LocalTime.parse(it) } ?: LocalTime.MIDNIGHT,
    milestoneId = getString("milestoneId"),
    linkedIds = (get("linkedIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
)

private fun ScheduleEntity.toMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "memo" to memo,
    "date" to date.toString(),
    "startTime" to startTime.toString(),
    "endTime" to endTime.toString(),
    "milestoneId" to milestoneId
)

class ScheduleRepository(
    private val firestore: FirebaseFirestore,
    private val familyId: String
) {
    private val collection get() = firestore.collection("families").document(familyId).collection("schedules")

    // 並び替えはあえてFirestoreクエリではなくクライアント側で行う。
    // 複合インデックス(equalityフィルタ+複数orderBy)をFirebaseコンソールで
    // 事前に作らなくても動くようにするため。
    private val byDateTime = compareBy<ScheduleEntity>({ it.date }, { it.startTime })

    fun allSchedules(): Flow<List<ScheduleEntity>> =
        collection.observeAsFlow()
            .map { snap -> snap.documents.map { it.toSchedule() }.sortedWith(byDateTime) }

    fun schedulesForDate(date: LocalDate): Flow<List<ScheduleWithLinkCount>> =
        collection.whereEqualTo("date", date.toString()).observeAsFlow()
            .map { snap ->
                snap.documents.map { it.toSchedule() }
                    .sortedWith(byDateTime)
                    .map { ScheduleWithLinkCount(it, it.linkedIds.size) }
            }

    fun scheduleById(id: String): Flow<ScheduleEntity?> =
        collection.document(id).observeAsFlow().map { snap -> if (snap.exists()) snap.toSchedule() else null }

    fun schedulesForMilestone(milestoneId: String): Flow<List<ScheduleEntity>> =
        collection.whereEqualTo("milestoneId", milestoneId).observeAsFlow()
            .map { snap -> snap.documents.map { it.toSchedule() }.sortedWith(byDateTime) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun linkedSchedules(id: String): Flow<List<ScheduleEntity>> =
        scheduleById(id).flatMapLatest { schedule ->
            val ids = schedule?.linkedIds.orEmpty()
            if (ids.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(ids.map { linkedId -> collection.document(linkedId).observeAsFlow().map { it.toSchedule() } }) { list ->
                    list.sortedWith(byDateTime)
                }
            }
        }

    fun linkedIds(id: String): Flow<List<String>> = scheduleById(id).map { it?.linkedIds.orEmpty() }

    suspend fun saveSchedule(schedule: ScheduleEntity, linkedIds: Set<String>): String {
        val docRef = if (schedule.id.isBlank()) collection.document() else collection.document(schedule.id)
        val id = docRef.id
        // 上書きしてしまう前に、差分を取るための「変更前のリンク」を読んでおく。
        val previousLinkedIds = if (schedule.id.isBlank()) {
            emptySet()
        } else {
            docRef.get().await().toSchedule().linkedIds.toSet()
        }
        docRef.set(schedule.copy(id = id).toMap() + mapOf("linkedIds" to linkedIds.toList())).await()
        updateReciprocalLinks(id, previousLinkedIds, linkedIds)
        return id
    }

    suspend fun deleteSchedule(id: String) {
        val current = collection.document(id).get().await().toSchedule()
        current.linkedIds.forEach { otherId ->
            runCatching { collection.document(otherId).update("linkedIds", FieldValue.arrayRemove(id)).await() }
        }
        collection.document(id).delete().await()
    }

    private suspend fun updateReciprocalLinks(scheduleId: String, previous: Set<String>, next: Set<String>) {
        val toAdd = next - previous
        val toRemove = previous - next

        // 相手側が家族の他のメンバーによって同時に削除されている可能性もあるため、
        // 1件の失敗で全体を止めないようにする。
        toAdd.forEach { otherId ->
            runCatching { collection.document(otherId).update("linkedIds", FieldValue.arrayUnion(scheduleId)).await() }
        }
        toRemove.forEach { otherId ->
            runCatching { collection.document(otherId).update("linkedIds", FieldValue.arrayRemove(scheduleId)).await() }
        }
    }
}
