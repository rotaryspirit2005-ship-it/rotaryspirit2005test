// 起動画面(Slatchのロゴが連なって留まり、名前とサブタイトルが出る)。ロゴの形はアプリアイコンと同じ(108の座標系)。
package com.example.schedulelink.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val T = 6.5f; private const val R_OUT = 14f; private const val H = 19.75f
private const val S = 12.25f; private const val GAP = 1.8f
private val U = Offset(0.70710678f, -0.70710678f)   // axis (rising to upper right)
private val V = Offset(0.70710678f, 0.70710678f)    // perpendicular

/** Stadium centreline path. c = centre in viewport units, half = tip-to-centre minus T/2. */
private fun stadium(c: Offset, half: Float, r: Float): Path {
    val a = half - r
    fun p(x: Float, y: Float) = Offset(c.x + x * U.x + y * V.x, c.y + x * U.y + y * V.y)
    return Path().apply {
        // approximate with 4 lines + 2 half-circles via arcTo on rotated rects is awkward -> sample the outline
        val n = 24
        val pts = buildList {
            for (i in 0..n) { val th = -Math.PI / 2 + Math.PI * i / n; add(p(a + (r * Math.cos(th)).toFloat(), (r * Math.sin(th)).toFloat())) }
            for (i in 0..n) { val th = Math.PI / 2 + Math.PI * i / n; add(p(-a + (r * Math.cos(th)).toFloat(), (r * Math.sin(th)).toFloat())) }
        }
        moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
    }
}

private fun halfPlane(side: Float, shift: Float = 0f): Path = Path().apply {
    val b = -side * shift; val e = 80f; val c = Offset(54f, 54f)
    fun p(x: Float, y: Float) = Offset(c.x + x * U.x + y * V.x, c.y + x * U.y + y * V.y)
    val q = listOf(p(-e, b), p(e, b), p(e, side * e), p(-e, side * e))
    moveTo(q[0].x, q[0].y); q.drop(1).forEach { lineTo(it.x, it.y) }; close()
}

/** openOff = 0 latched; >0 pulled apart (units along the axis, each link). scale: 1.0 = 288dp box of the system splash. */
@Composable
fun SlatchLogo(modifier: Modifier, linkA: Color, linkB: Color, halo: Color, openOff: Float) {
    Canvas(modifier) {
        val k = size.minDimension / 108f
        scale(k, k, pivot = Offset.Zero) {
            val c = Offset(54f, 54f)
            val cA = c - U * (S + openOff); val cB = c + U * (S + openOff)
            val rc = R_OUT - T / 2; val hc = H - T / 2
            val ringA = stadium(cA, hc, rc); val ringB = stadium(cB, hc, rc)
            val st = Stroke(width = T); val stHalo = Stroke(width = T + 2 * GAP)
            drawPath(ringA, linkA, style = st)
            if (openOff <= 0.5f) {
                clipPath(halfPlane(-1f)) { clipPath(stadium(cA, H, R_OUT)) { drawPath(ringB, halo, style = stHalo) } }
            }
            drawPath(ringB, linkB, style = st)
            if (openOff <= 0.5f) {   // weave only while latched
                clipPath(halfPlane(+1f)) { clipPath(stadium(cB, H, R_OUT)) { drawPath(ringA, halo, style = stHalo) } }
                clipPath(halfPlane(+1f, shift = 0.5f)) { drawPath(ringA, linkA, style = st) }
            }
        }
    }
}

@Composable
fun SlatchSplash(onFinished: () -> Unit) {
    // 色は、アプリ内のライト/ダーク設定ではなく端末の設定に合わせる。起動直後のシステムの
    // スプラッシュ画面も端末の設定で決まるので、そこから継ぎ目なくつながる。
    val dark = isSystemInDarkTheme()
    val background = if (dark) Color(0xFF121411) else Color(0xFFF9FAF3)
    val titleColor = if (dark) Color(0xFFE2E3DD) else Color(0xFF1A1C19)
    val subtitleColor = if (dark) Color(0xFFC2C9BD) else Color(0xFF424940)
    val plusColor = if (dark) Color(0xFF88D982) else Color(0xFF1B6D24)
    val linkA = if (dark) Color(0xFFEAF8E6) else Color(0xFF12461A)
    val linkB = if (dark) Color(0xFF88D982) else Color(0xFF34A043)

    val open = remember { Animatable(0f) }          // 0 latched
    val scale = remember { Animatable(1f) }         // 1.0 = system splash size (288dp box)
    val lift = remember { Animatable(0f) }          // dp the logo moves up
    val pulse = remember { Animatable(1f) }
    val title = remember { Animatable(0f) }
    val sub = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // t=0: identical to the system splash (latched, 288dp box, screen centre) -> no visible jump
        launch { open.animateTo(9f, tween(140, easing = FastOutLinearInEasing)) ; // unlatch
                 open.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 380f)) } // snap back ("click")
        launch { scale.animateTo(0.62f, tween(620, easing = FastOutSlowInEasing)) }
        launch { lift.animateTo(60f, tween(620, easing = FastOutSlowInEasing)) }
        launch { delay(560); pulse.animateTo(1.05f, tween(70)); pulse.animateTo(1f, tween(130)) }
        launch { delay(480); title.animateTo(1f, tween(420, easing = LinearOutSlowInEasing)) }
        launch { delay(640); sub.animateTo(1f, tween(400, easing = LinearOutSlowInEasing)) }
        delay(1600)                                   // total visible time
        exit.animateTo(0f, tween(220)); onFinished()
    }

    // 動く値は描画の段階(graphicsLayer)で読み、アニメーション中に毎フレームの再構成・再測定をしない。
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(background)
            // 表示中は下の画面を操作できないよう、タッチをすべて受け止める。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // logo box = 288dp * scale, centred; lifts up by `lift`
        SlatchLogo(
            Modifier
                .size(288.dp)
                .graphicsLayer {
                    val s = scale.value * pulse.value
                    scaleX = s
                    scaleY = s
                    translationY = -lift.value * density
                },
            linkA, linkB, background, open.value
        )
        Column(Modifier.offset(y = 94.dp), horizontalAlignment = Alignment.CenterHorizontally) {  // title baseline ~ +58dp below centre
            Text(
                "Slatch", color = titleColor, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 12f * density
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    append("Schedule ")
                    withStyle(SpanStyle(color = plusColor, fontWeight = FontWeight.Bold)) { append("+") }
                    append(" Latch")
                },
                color = subtitleColor, fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 2.6.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = sub.value
                    translationY = (1f - sub.value) * 8f * density
                }
            )
        }
    }
}
