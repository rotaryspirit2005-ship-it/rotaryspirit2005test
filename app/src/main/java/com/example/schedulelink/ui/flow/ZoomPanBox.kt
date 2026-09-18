package com.example.schedulelink.ui.flow

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
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
 * ピンチで拡大・縮小、ドラッグでパンできるコンテナ。
 * フロー表示全体を俯瞰したり、一部を拡大して読みやすくしたりするために使う。
 * 現在の拡大率を[content]に渡すので、呼び出し側は「どれくらい拡大されているか」に
 * 応じて表示する情報の細かさ(例: 月の目盛りだけ出すか、日の目盛りまで出すか)を変えられる。
 *
 * 地図アプリと同じく、1本指でダブルタップした状態のまま上下にドラッグしても
 * 拡大縮小できる(片手操作用)。
 */
@Composable
fun ZoomPanBox(modifier: Modifier = Modifier, content: @Composable (scale: Float) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    offset += pan
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
                        val referenceScale = scale
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            val deltaY = change.position.y - referenceY
                            // 上にドラッグ(deltaYが負)ほど縮小、下にドラッグほど拡大する。
                            val factor = exp(deltaY / sensitivityPx)
                            scale = (referenceScale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
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
        Box(
            modifier = Modifier.graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            )
        ) {
            content(scale)
        }
    }
}
