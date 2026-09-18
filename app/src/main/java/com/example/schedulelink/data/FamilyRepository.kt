package com.example.schedulelink.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

data class FamilyInfo(val id: String, val name: String, val inviteCode: String, val memberCount: Int)

class FamilyRepository(private val firestore: FirebaseFirestore) {

    /** サインイン中のユーザーが所属している家族グループのID(未所属ならnull)。 */
    fun observeFamilyId(uid: String): Flow<String?> =
        firestore.collection("users").document(uid).observeAsFlow()
            .map { it.getString("familyId") }

    fun observeFamily(familyId: String): Flow<FamilyInfo?> =
        firestore.collection("families").document(familyId).observeAsFlow()
            .map { snap ->
                if (!snap.exists()) return@map null
                val memberCount = (snap.get("members") as? List<*>)?.size ?: 0
                FamilyInfo(
                    id = snap.id,
                    name = snap.getString("name").orEmpty(),
                    inviteCode = snap.getString("inviteCode").orEmpty(),
                    memberCount = memberCount
                )
            }

    suspend fun createFamily(uid: String, displayName: String, familyName: String): String {
        val docRef = firestore.collection("families").document()
        docRef.set(
            mapOf(
                "name" to familyName,
                "inviteCode" to generateInviteCode(),
                "members" to listOf(uid),
                "createdBy" to uid
            )
        ).await()
        firestore.collection("users").document(uid)
            .set(mapOf("familyId" to docRef.id, "displayName" to displayName)).await()
        return docRef.id
    }

    suspend fun joinFamily(uid: String, displayName: String, inviteCode: String): Result<String> {
        val normalized = inviteCode.trim().uppercase()
        val query = firestore.collection("families").whereEqualTo("inviteCode", normalized).get().await()
        val doc = query.documents.firstOrNull()
            ?: return Result.failure(IllegalArgumentException("招待コードが見つかりませんでした"))
        firestore.collection("families").document(doc.id)
            .update("members", FieldValue.arrayUnion(uid)).await()
        firestore.collection("users").document(uid)
            .set(mapOf("familyId" to doc.id, "displayName" to displayName)).await()
        return Result.success(doc.id)
    }

    suspend fun leaveFamily(uid: String, familyId: String) {
        firestore.collection("families").document(familyId)
            .update("members", FieldValue.arrayRemove(uid)).await()
        firestore.collection("users").document(uid).update("familyId", null).await()
    }

    private fun generateInviteCode(): String {
        // 見間違えやすい文字(0/O, 1/I)を除いた6桁コード
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
