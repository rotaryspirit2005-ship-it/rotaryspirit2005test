package com.example.schedulelink.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private fun DocumentSnapshot.toTag(): TagEntity = TagEntity(
    id = id,
    name = getString("name").orEmpty(),
    colorIndex = (getLong("colorIndex") ?: 0L).toInt(),
    createdAt = getLong("createdAt") ?: 0L
)

private fun TagEntity.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "colorIndex" to colorIndex,
    "createdAt" to createdAt
)

/**
 * タグ(families/{familyId}/tags)。家族のメンバー用のFirestoreルール(サブコレクションは
 * メンバーなら読み書き可)にそのまま含まれるため、ルールの変更は不要。
 */
class TagRepository(
    private val firestore: FirebaseFirestore,
    private val familyId: String
) {
    private val collection get() = firestore.collection("families").document(familyId).collection("tags")

    private val byCreated = compareBy<TagEntity>({ it.createdAt }, { it.id })

    fun allTags(): Flow<List<TagEntity>> =
        collection.observeAsFlow().endOnPermissionDenied()
            .map { snap -> snap.documents.map { it.toTag() }.sortedWith(byCreated) }

    /** 単発で取得する(ウィジェット用)。CLAUDE.mdのとおり購読ではなくget()で取りに行く。 */
    suspend fun allTagsOnce(): List<TagEntity> =
        collection.get().await().documents.map { it.toTag() }.sortedWith(byCreated)

    /** タグを保存してIDを返す。書き込みの完了は待たない(オフラインでも画面が止まらないように)。 */
    fun saveTag(tag: TagEntity): String {
        val docRef = if (tag.id.isBlank()) collection.document() else collection.document(tag.id)
        val toSave = if (tag.createdAt == 0L) tag.copy(createdAt = System.currentTimeMillis()) else tag
        docRef.set(toSave.toMap())
        return docRef.id
    }

    /**
     * タグだけを消す。付いていた項目の tagIds は書き換えない(全項目への一斉書き込みを避けるため)。
     * 表示側は、存在しないタグのIDを無視する。
     */
    fun deleteTag(id: String) {
        collection.document(id).delete()
    }
}
