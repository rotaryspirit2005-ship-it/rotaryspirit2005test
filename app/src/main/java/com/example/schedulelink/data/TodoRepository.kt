package com.example.schedulelink.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

private fun DocumentSnapshot.toTodo(): TodoEntity = TodoEntity(
    id = id,
    title = getString("title").orEmpty(),
    memo = getString("memo").orEmpty(),
    done = getBoolean("done") ?: false,
    dueDate = getString("dueDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    scheduleId = getString("scheduleId"),
    createdAt = getLong("createdAt") ?: 0L,
    completedAt = getLong("completedAt"),
    tagIds = (get("tagIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
)

private fun TodoEntity.toMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "memo" to memo,
    "done" to done,
    "dueDate" to dueDate?.toString(),
    "scheduleId" to scheduleId,
    "createdAt" to createdAt,
    "completedAt" to completedAt,
    "tagIds" to tagIds
)

/**
 * やること(families/{familyId}/todos)。家族のメンバー用のFirestoreルール(サブコレクションは
 * メンバーなら読み書き可)にそのまま含まれるため、ルールの変更は不要。
 */
class TodoRepository(
    private val firestore: FirebaseFirestore,
    private val familyId: String
) {
    private val collection get() = firestore.collection("families").document(familyId).collection("todos")

    fun allTodos(): Flow<List<TodoEntity>> =
        collection.observeAsFlow().endOnPermissionDenied().map { snap -> snap.documents.map { it.toTodo() } }

    /** 予定にリンクされたやること(並べ替えは画面側。複合インデックスを不要にするため)。 */
    fun todosForSchedule(scheduleId: String): Flow<List<TodoEntity>> =
        collection.whereEqualTo("scheduleId", scheduleId).observeAsFlow().endOnPermissionDenied()
            .map { snap -> snap.documents.map { it.toTodo() } }

    /**
     * 未完了のやることを単発で取得する(ウィジェット用)。CLAUDE.mdのとおり、購読+first()だと
     * サーバー同期前のキャッシュだけの結果を拾うことがあるため、get()で取りに行く。
     */
    suspend fun openTodosOnce(): List<TodoEntity> =
        collection.whereEqualTo("done", false).get().await().documents.map { it.toTodo() }

    suspend fun saveTodo(todo: TodoEntity) {
        val docRef = if (todo.id.isBlank()) collection.document() else collection.document(todo.id)
        val toSave = if (todo.createdAt == 0L) todo.copy(createdAt = System.currentTimeMillis()) else todo
        docRef.set(toSave.toMap()).await()
    }

    /** 完了の切り替えは項目を絞って更新し、家族が同時に編集した他の項目を上書きしない。 */
    suspend fun setDone(id: String, done: Boolean) {
        collection.document(id).update(
            mapOf(
                "done" to done,
                "completedAt" to if (done) System.currentTimeMillis() else FieldValue.delete()
            )
        ).await()
    }

    suspend fun deleteTodo(id: String) {
        collection.document(id).delete().await()
    }
}
