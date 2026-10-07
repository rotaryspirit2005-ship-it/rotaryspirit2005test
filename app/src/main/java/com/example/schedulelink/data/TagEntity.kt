package com.example.schedulelink.data

/**
 * 家族で使うタグ(誰の予定か、など)。名前と色の番号だけを持つ自由なラベルで、特定のメンバーには
 * 紐づけない(アカウントのない子どもや「家族全員」も表せるように)。色の番号は
 * ui/theme/Color.ktのタグ用パレットの番号で、ライト/ダークで同じ番号から色を引く。
 */
data class TagEntity(
    val id: String = "",
    val name: String = "",
    val colorIndex: Int = 0,
    /** 並び順用の作成時刻(端末時刻)。 */
    val createdAt: Long = 0L
)
