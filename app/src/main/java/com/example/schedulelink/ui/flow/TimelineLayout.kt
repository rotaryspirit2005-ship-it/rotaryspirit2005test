package com.example.schedulelink.ui.flow

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// タイムライン(全体マップ)の横軸の共通の値。単位はdp(拡大率1のとき)。

/** 拡大率1のときの1日の幅。 */
const val PX_PER_DAY = 8f
/** 左端の余白。 */
const val MARGIN = 24f

fun timelineX(minDate: LocalDate, date: LocalDate): Float =
    MARGIN + ChronoUnit.DAYS.between(minDate, date) * PX_PER_DAY
