package com.example.schedulelink.ui.flow

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp

private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 8f

/** 1本指ダブルタップ+上下ドラッグでズームする際の、2回目のタップとみなす時間・距離のしきい値。 */
private const val DOUBLE_TAP_TIMEOUT_MILLIS = 300L
private const val DOUBLE_TAP_SLOP_DP = 32f
/** ドラッグ何dp分で拡大率がおよそe倍(≒2.7倍)変わるか。小さいほど指の動きに敏感になる。 */
private const val ONE_FINGER_ZOOM_SENSITIVITY_DP = 250f

/**
 * [ZoomPanBox]の現在の拡大率・移動量・大きさ。コンテンツの外側(画面の座標)に背景やルーラーを
 * 描きたいとき([ZoomPanBox]の`backdrop`/`overlay`)に使う。再コンポーズを避けるため、
 * 描画のラムダの中で読むこと。
 */
@Stable
class ZoomPanState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
}

/**
 * ピンチで拡大・縮小、ドラッグでパンできるコンテナ。
 * フロー表示全体を俯瞰したり、一部を拡大して読みやすくしたりするために使う。
 * 現在の拡大率を[content]に渡すので、呼び出し側は「どれくらい拡大されているか」に
 * 応じて表示する情報の細かさ(例: 月の目盛りだけ出すか、日の目盛りまで出すか)を変えられる。
 *
 * 地図アプリと同じく、1本指でダブルタップした状態のまま上下にドラッグしても
 * 拡大縮小できる(片手操作用)。
 *
 * [initialFocusX]を指定すると、最初に表示されたときにその横位置が画面の中央に
 * 来るようパン位置を合わせる(例: 「今日」の日付を中央にして開く)。
 */
@Composable
fun ZoomPanBox(
    modifier: Modifier = Modifier,
    initialFocusX: Dp? = null,
    backdrop: @Composable (ZoomPanState) -> Unit = {},
    overlay: @Composable (ZoomPanState) -> Unit = {},
    content: @Composable (scale: Float) -> Unit
) {
    val state = remember { ZoomPanState() }
    var hasAppliedInitialFocus by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                state.size = size
                if (!hasAppliedInitialFocus && initialFocusX != null) {
                    hasAppliedInitialFocus = true
                    val focusPx = with(density) { initialFocusX.toPx() }
                    state.offset = Offset(size.width / 2f - focusPx, state.offset.y)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    state.scale = (state.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    state.offset += pan
                }
            }
            .pointerInput(Unit) {
                val slopPx = DOUBLE_TAP_SLOP_DP.dp.toPx()
                val sensitivityPx = ONE_FINGER_ZOOM_SENSITIVITY_DP.dp.toPx()
                var lastUpTimeMillis = 0L
                var lastUpPosition = Offset.Zero

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val isSecondTapOfDoubleTap =
                        (down.uptimeMillis - lastUpTimeMillis) < DOUBLE_TAP_TIMEOUT_MILLIS &&
                            (down.position - lastUpPosition).getDistance() < slopPx

                    if (isSecondTapOfDoubleTap) {
                        val referenceY = down.position.y
                        val referenceScale = state.scale
                        // 2回目にタップした場所を拡大縮小の中心にする。その場所が
                        // 指す「コンテンツ上の点」を先に求めておき、スケールが変わる
                        // たびにその点が同じ画面位置に留まるようoffsetを補正し続ける。
                        val focalScreen = down.position
                        val focalContent = (focalScreen - state.offset) / referenceScale
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            val deltaY = change.position.y - referenceY
                            // 上にドラッグ(deltaYが負)ほど縮小、下にドラッグほど拡大する。
                            val factor = exp(deltaY / sensitivityPx)
                            val newScale = (referenceScale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
                            state.scale = newScale
                            state.offset = focalScreen - focalContent * newScale
                            change.consume()
                        }
                        lastUpTimeMillis = 0L
                    } else {
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null) {
                            lastUpTimeMillis = up.uptimeMillis
                            lastUpPosition = up.position
                        } else {
                            lastUpTimeMillis = 0L
                        }
                    }
                }
            }
    ) {
        // 背景は画面の座標のまま描く(線の太さが拡大縮小で変わらず、見えている範囲だけ描ける)。
        backdrop(state)
        Box(
            modifier = Modifier.graphicsLayer(
                scaleX = state.scale,
                scaleY = state.scale,
                translationX = state.offset.x,
                translationY = state.offset.y,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            )
        ) {
            content(state.scale)
        }
        overlay(state)
    }
}
