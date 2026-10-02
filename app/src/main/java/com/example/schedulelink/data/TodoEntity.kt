package com.example.schedulelink.data

import java.time.LocalDate

/** やること(家族で共有するTo-Do)。 */
data class TodoEntity(
    val id: String = "",
    val title: String = "",
    val memo: String = "",
    val done: Boolean = false,
    val dueDate: LocalDate? = null,
    /** リンクした小日程(任意・1件まで)。リンクはやること側だけに持つ(予定側には書かない)。 */
    val scheduleId: String? = null,
    /** 並び順用の作成時刻(端末時刻)。サーバー時刻だとオフライン中にnullになり並びが跳ねるため。 */
    val createdAt: Long = 0L,
    val completedAt: Long? = null
)

/** 未完了の並び: 期限の近い順(期限なしは後ろ)→作成順。アプリとウィジェットで共通。 */
val openTodoOrder: Comparator<TodoEntity> = compareBy({ it.dueDate ?: LocalDate.MAX }, { it.createdAt })

/** 完了済みの並び: 新しく完了したものから。 */
val doneTodoOrder: Comparator<TodoEntity> = compareByDescending { it.completedAt ?: 0L }
