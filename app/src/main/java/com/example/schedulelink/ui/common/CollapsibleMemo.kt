package com.example.schedulelink.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.schedulelink.ui.theme.Motion

/**
 * 長いメモが画面を占拠しないよう、最初は数行にたたんで表示する。
 * はみ出すときだけ「続きを表示/たたむ」を出すので、短いメモの見た目は変わらない。
 * 開いた状態では長押しでコピーできる(たたんだ状態はタップで開く操作と競合するため選択不可)。
 *
 * @param expandedMaxHeight 開いたときの高さの上限。スクロールしない親の中に置く画面用で、
 *   超えた分はメモの中でスクロールする。nullなら全文をそのまま伸ばす。
 */
@Composable
fun CollapsibleMemo(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 4,
    expandedMaxHeight: Dp? = null
) {
    // メモが編集されたら、たたんだ状態からやり直す
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var hasOverflow by rememberSaveable(text) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Motion.DurationMedium),
        label = "memoChevron"
    )

    Column(modifier = modifier.animateContentSize(tween(Motion.DurationLong, easing = Motion.Emphasized))) {
        if (expanded) {
            val scrollModifier = if (expandedMaxHeight != null) {
                Modifier.heightIn(max = expandedMaxHeight).verticalScroll(rememberScrollState())
            } else {
                Modifier
            }
            SelectionContainer(modifier = scrollModifier) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = collapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { hasOverflow = it.hasVisualOverflow },
                modifier = if (hasOverflow) {
                    Modifier.clickable(onClickLabel = "メモの続きを表示") { expanded = true }
                } else {
                    Modifier
                }
            )
        }

        if (hasOverflow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClickLabel = if (expanded) "メモをたたむ" else "メモの続きを表示",
                        role = Role.Button
                    ) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "展開中" else "折りたたみ中" },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "たたむ" else "続きを表示",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).rotate(rotation)
                )
            }
        }
    }
}
