package com.example.schedulelink.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * アプリ全体で揃える動きの時間とイージング(Material 3のモーション指針に準拠)。
 * material3のMotionTokensはinternalなので自前で定義する。400msを超える動きは使わない。
 * 端末の「アニメーションを削除」設定にはComposeが自動で従う。
 */
object Motion {
    /** 退場・フェードアウト */
    const val DurationShort = 100
    /** 選択色の切り替えなど */
    const val DurationSelect = 150
    /** 入場のフェード・一覧の切り替え */
    const val DurationMedium = 200
    /** スライド・サイズ変化・画面遷移 */
    const val DurationLong = 300

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** 入ってくる要素用(素早く出て、ゆっくり止まる) */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** 出ていく要素用(ゆっくり動き出して、素早く消える) */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * 月・週・日の切り替えで使う、方向つきの控えめな横スライド。
 * 全幅スライドは大げさになるため、移動量は幅の1/5に抑えてフェードと組み合わせる。
 */
fun <S> AnimatedContentTransitionScope<S>.directionalSlide(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(tween(Motion.DurationLong, easing = Motion.EmphasizedDecelerate)) { direction * it / 5 } +
            fadeIn(tween(Motion.DurationMedium, delayMillis = 50))
        ) togetherWith (
        slideOutHorizontally(tween(Motion.DurationMedium, easing = Motion.EmphasizedAccelerate)) { -direction * it / 5 } +
            fadeOut(tween(Motion.DurationShort))
        ) using SizeTransform(clip = true) { _, _ -> tween(Motion.DurationLong, easing = Motion.Emphasized) }
}

/** 同じ場所の中身が入れ替わるときのフェードスルー(日付選択に応じた予定一覧の切り替えなど)。 */
fun <S> AnimatedContentTransitionScope<S>.fadeThrough(): ContentTransform =
    fadeIn(tween(Motion.DurationMedium, delayMillis = 50)) togetherWith fadeOut(tween(Motion.DurationShort))
