package com.example.schedulelink.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch

/** Firestoreのクエリ結果をリアルタイムに流すFlow。家族間の同時編集を反映するために使う。 */
fun Query.observeAsFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.observeAsFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/**
 * 家族のデータ(予定・目的・中日程)の購読用。家族グループを抜けた直後やサインアウト直後は、
 * 開いたままの画面の購読が権限切れ(PERMISSION_DENIED)で終わる。その例外をそのまま流すと
 * 画面側(stateIn等)で捕まらずアプリが落ちるため、この場合だけは静かに購読を終える
 * (画面はすぐに家族選択・サインイン画面へ切り替わる)。それ以外のエラーは従来どおり流す。
 */
fun <T> Flow<T>.endOnPermissionDenied(): Flow<T> = catch { e ->
    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return@catch
    throw e
}
