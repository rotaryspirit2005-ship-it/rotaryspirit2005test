package com.example.schedulelink.ui.help

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.schedulelink.ui.theme.Dimens
import com.example.schedulelink.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * 画面右上に置く「この画面の使い方」ボタン。押すと[topic]のガイドを下から開く。
 * 各画面のTopAppBarのactionsに1行足すだけで使えるよう、開閉の状態もここで持つ。
 */
@Composable
fun HelpAction(topic: HelpTopic) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "この画面の使い方")
    }
    if (open) HelpSheet(topic = topic, onDismiss = { open = false })
}

/**
 * 図解つきの使い方ガイド。1ページ1枚のカードを横にめくる。どの画面からでも
 * 「Slatchとは？」でアプリ全体の紹介に切り替えられる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(topic: HelpTopic, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var shownName by rememberSaveable { mutableStateOf(topic.name) }
    val shown = HelpTopic.valueOf(shownName)

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // トピックを切り替えたら、ページの位置を最初からにする
        key(shown) {
            val pages = shown.pages
            val pagerState = rememberPagerState { pages.size }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 横向きや分割画面でも「次へ」まで届くようにする
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.ScreenPadding)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (shown == HelpTopic.Intro) shown.label else "${shown.label}の使い方",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    if (shown != HelpTopic.Intro) {
                        AssistChip(
                            onClick = { shownName = HelpTopic.Intro.name },
                            label = { Text("Slatchとは？") },
                            leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    } else if (topic != HelpTopic.Intro) {
                        AssistChip(
                            onClick = { shownName = topic.name },
                            label = { Text("戻る") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { index ->
                    val appear by animateFloatAsState(
                        targetValue = if (pagerState.currentPage == index) 1f else 0.9f,
                        animationSpec = tween(Motion.DurationLong, easing = Motion.EmphasizedDecelerate),
                        label = "helpPageAppear"
                    )
                    HelpPageCard(page = pages[index], number = index + 1, total = pages.size, appear = appear)
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0
                    ) { Text("戻る") }
                    PageDots(
                        count = pages.size,
                        current = pagerState.currentPage,
                        modifier = Modifier.weight(1f)
                    )
                    val last = pagerState.currentPage == pages.lastIndex
                    Button(onClick = {
                        if (last) close() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) { Text(if (last) "わかった！" else "次へ") }
                }
            }
        }
    }
}

@Composable
private fun HelpPageCard(page: HelpPage, number: Int, total: Int, appear: Float) {
    // ページごとに絵の背景色を変えて、めくったときの変化を楽しくする
    val containers = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(containers[(number - 1) % containers.size], RoundedCornerShape(28.dp))
                .clearAndSetSemantics { }
        ) {
            HelpIllustration(
                page.art,
                modifier = Modifier.graphicsLayer {
                    scaleX = appear
                    scaleY = appear
                    alpha = (appear - 0.9f) * 10f * 0.6f + 0.4f
                }
            )
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // 文の長さが違ってもボタンの位置が上下しないよう、本文の高さをそろえる
        Column(
            modifier = Modifier
                .heightIn(min = 120.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${total}ページ中${number}ページ目。${page.title}。${page.body}" }
        ) {
            Text(page.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                page.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        // ページ数はカード側で読み上げるので、点は読み上げない
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateFloatAsState(
                targetValue = if (selected) 18f else 8f,
                animationSpec = tween(Motion.DurationMedium),
                label = "helpDot"
            )
            Box(
                Modifier
                    .width(width.dp)
                    .height(8.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}
