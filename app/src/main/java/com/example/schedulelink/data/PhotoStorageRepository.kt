package com.example.schedulelink.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * 目的/中日程/小日程に添付する写真をFirebase Storageへ保存する。
 * 家族グループ単位(familyId)でパスを分けるので、Storageのセキュリティルール側で
 * 同じ家族のメンバーだけがアクセスできるよう制限する必要がある。
 */
class PhotoStorageRepository(
    private val storage: FirebaseStorage,
    private val familyId: String
) {
    suspend fun upload(entityType: String, entityId: String, uri: Uri): String {
        val ref = storage.reference.child(
            "families/$familyId/$entityType/$entityId/${UUID.randomUUID()}.jpg"
        )
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun delete(url: String) {
        runCatching { storage.getReferenceFromUrl(url).delete().await() }
    }
}
