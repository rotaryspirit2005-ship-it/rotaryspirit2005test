package com.example.schedulelink.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
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

    /**
     * 招待コードで家族グループに参加する。通信・権限エラーも例外で投げずにResult.failureで返す
     * (以前は例外がそのまま画面のコルーチンに伝わり、アプリが落ちていた)。
     */
    suspend fun joinFamily(uid: String, displayName: String, inviteCode: String): Result<String> {
        val normalized = inviteCode.trim().uppercase()
        return try {
            val query = firestore.collection("families").whereEqualTo("inviteCode", normalized).get().await()
            val doc = query.documents.firstOrNull()
                ?: return Result.failure(IllegalArgumentException("招待コードが見つかりませんでした"))
            // 先にメンバーに加わってから所属を書き込む(予定データはメンバーだけが読めるため)。
            firestore.collection("families").document(doc.id)
                .update("members", FieldValue.arrayUnion(uid)).await()
            firestore.collection("users").document(uid)
                .set(mapOf("familyId" to doc.id, "displayName" to displayName)).await()
            Result.success(doc.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(familyErrorMessage(e), e))
        }
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

/** 家族グループの操作の失敗を、画面にそのまま出せる文言に直す。 */
fun familyErrorMessage(error: Throwable): String = when ((error as? FirebaseFirestoreException)?.code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "サーバーに拒否されました。Firebaseのセキュリティルールの更新が必要です(FIREBASE_SETUP.md の手順4)"
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
        "通信できませんでした。電波の良い所でもう一度お試しください"
    else -> error.message ?: "処理に失敗しました(${error::class.simpleName})"
}
