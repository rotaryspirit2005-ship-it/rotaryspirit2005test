package com.example.schedulelink.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.schedulelink.data.GoalEntity
import com.example.schedulelink.data.MilestoneEntity
import com.example.schedulelink.data.ScheduleEntity
import com.example.schedulelink.ui.flow.NodeStatus
import com.example.schedulelink.ui.flow.SwimBand
import com.example.schedulelink.ui.flow.SwimBandKind
import com.example.schedulelink.ui.flow.buildSwimlanes
import com.example.schedulelink.ui.theme.TodayLineColorDark
import com.example.schedulelink.ui.theme.TodayLineColorLight
import com.example.schedulelink.ui.theme.connectorColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

// 月表示ウィジェットの下に出す、縮小版のタイムライン。Glanceには自由に描く部品がないため、
// ウィジェットを更新するときに画像(Bitmap)として描いて、Imageで表示する。
// 配置はアプリの全体マップ(SwimlaneLayout.kt)と同じ計算・同じ色を使い、大目的ごとの帯に
// 中日程の期間のバーと予定の点、月の区切りと今日の線を描く。単位はdp(最後に画面の密度で拡大)。

/** 表示する週数(先週の日曜から。カレンダーと同じ日曜始まりで週の区切りをそろえる)。 */
private const val WEEKS = 8L
private const val PAD_X = 2f
private const val RULER_DP = 16f
private const val HEADER_DP = 16f
private const val BAR_DP = 14f
private const val DOT_ROW_DP = 8f
private const val TRACK_GAP_DP = 2f
private const val BAND_GAP_DP = 6f
/** 帯の最大件数(超えた分は「ほか N件」)。 */
private const val MAX_BANDS = 4
/** 1つの帯の中の中日程の段の上限。 */
private const val MAX_TRACKS = 2
/** 画像のピクセル数の上限(ウィジェットに渡せる大きさに収める)。 */
private const val MAX_PIXELS = 140_000f

class WidgetTimeline(
    val bitmap: Bitmap,
    /** 画像の表示上の高さ(dp)。幅はウィジェットの幅いっぱい。 */
    val heightDp: Float,
    val start: LocalDate,
    val end: LocalDate,
    /** 期間内にあるのに表示しきれなかった大目的の数。 */
    val hiddenBandCount: Int,
    /** 段が多すぎて描けなかった中日程の数。 */
    val hiddenMilestoneCount: Int,
    /** 読み上げ用の説明。 */
    val description: String
)

private fun isNight(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** 帯が表示期間にかかっているか(帯の期間・中日程・予定のどれかが期間内)。 */
private fun SwimBand.touches(start: LocalDate, end: LocalDate): Boolean {
    fun overlaps(s: LocalDate, e: LocalDate) = !e.isBefore(start) && !s.isAfter(end)
    if (spanStart != null && spanEnd != null && overlaps(spanStart, spanEnd)) return true
    if (bars.any { overlaps(it.start, it.end) }) return true
    if (bars.any { bar -> bar.chips.any { overlaps(it.schedule.date, it.schedule.date) } }) return true
    return looseChips.any { overlaps(it.schedule.date, it.schedule.date) }
}

/** 期間内にかかる中日程のバー。 */
private fun SwimBand.barsIn(start: LocalDate, end: LocalDate) =
    bars.filter { !it.end.isBefore(start) && !it.start.isAfter(end) }

/** 帯の高さ(dp)。期間内で使う中日程の段ごとにバーと点の行、中日程なしの予定は点の行だけ。 */
private fun SwimBand.heightDp(start: LocalDate, end: LocalDate, headerDp: Float): Float {
    val tracks = barsIn(start, end).maxOfOrNull { it.track + 1 } ?: 0
    return when {
        kind == SwimBandKind.UNASSIGNED -> headerDp + DOT_ROW_DP + TRACK_GAP_DP
        tracks == 0 -> headerDp + TRACK_GAP_DP
        else -> headerDp + min(tracks, MAX_TRACKS) * (BAR_DP + DOT_ROW_DP + TRACK_GAP_DP)
    }
}

/**
 * 縮小版のタイムラインを描く。表示期間に何もなければnull。
 */
fun renderWidgetTimeline(
    context: Context,
    /** 描く幅(dp)。ウィジェットの中身の実際の幅にして、拡大縮小せずに表示する。 */
    widthDp: Float,
    goals: List<GoalEntity>,
    milestones: List<MilestoneEntity>,
    schedules: List<ScheduleEntity>,
    today: LocalDate = LocalDate.now()
): WidgetTimeline? {
    // 先週の日曜から8週間。
    val start = today.minusDays((today.dayOfWeek.value % 7).toLong()).minusWeeks(1)
    val end = start.plusWeeks(WEEKS).minusDays(1)
    val totalDays = ChronoUnit.DAYS.between(start, end) + 1
    val widthUsedDp = widthDp.coerceIn(160f, 600f)
    val dayDp = (widthUsedDp - PAD_X * 2) / totalDays
    // 文字の大きさの設定に合わせる(画像の中の文字も読めるように)。
    val fontScale = context.resources.configuration.fontScale.coerceIn(1f, 1.3f)
    val headerDp = HEADER_DP * fontScale
    val rulerDp = RULER_DP * fontScale
    // 段の割り当ては、名前の幅を考えず(ウィジェットでは名前を外に出さない)期間が重なるかだけで決める。
    val layout = buildSwimlanes(goals, milestones, schedules, 1000f, 1f, today)
    val inWindow = layout.bands.filter { it.touches(start, end) }
    if (inWindow.isEmpty()) return null
    val bands = inWindow.take(MAX_BANDS)

    val isDark = isNight(context)
    val onSurface = if (isDark) 0xFFE2E3DD.toInt() else 0xFF1A1C19.toInt()
    val subText = if (isDark) 0xFFC2C9BD.toInt() else 0xFF424940.toInt()
    val outline = if (isDark) 0xFF424940.toInt() else 0xFFC2C9BD.toInt()
    val todayColor = (if (isDark) TodayLineColorDark else TodayLineColorLight).toArgb()
    val doneBar = if (isDark) 0xFF333531.toInt() else 0xFFE2E3DD.toInt()
    val primary = if (isDark) 0xFF88D982.toInt() else 0xFF1B6D24.toInt()

    val heightDp = rulerDp + bands.sumOf { (it.heightDp(start, end, headerDp) + BAND_GAP_DP).toDouble() }.toFloat()
    // 大きすぎる画像はウィジェットに載せられないので、密度は2倍まで・ピクセル数も上限までにする。
    var density = min(context.resources.displayMetrics.density, 2f)
    val pixels = widthUsedDp * heightDp * density * density
    if (pixels > MAX_PIXELS) density *= kotlin.math.sqrt(MAX_PIXELS / pixels)
    // 背景を不透明に塗ってRGB_565にし、ウィジェットに渡すデータ量を半分にする。
    val bitmap = Bitmap.createBitmap(
        (widthUsedDp * density).toInt(),
        (heightDp * density).toInt().coerceAtLeast(1),
        Bitmap.Config.RGB_565
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(if (isDark) 0xFF1E201D.toInt() else 0xFFF9FAF3.toInt())
    canvas.scale(density, density)

    fun xOf(date: LocalDate): Float = PAD_X + ChronoUnit.DAYS.between(start, date) * dayDp
    val left = PAD_X
    val right = widthUsedDp - PAD_X

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG)

    // 月の区切り線と月名。
    var month = start.withDayOfMonth(1)
    while (!month.isAfter(end)) {
        if (!month.isBefore(start)) {
            val x = xOf(month)
            stroke.color = outline
            stroke.strokeWidth = 1f
            stroke.pathEffect = null
            canvas.drawLine(x, rulerDp - 4f, x, heightDp, stroke)
            text.color = subText
            text.textSize = 10f * fontScale
            text.isFakeBoldText = false
            canvas.drawText("${month.monthValue}月", x + 3f, 12f * fontScale, text)
        } else {
            text.color = subText
            text.textSize = 10f * fontScale
            canvas.drawText("${month.monthValue}月", left + 1f, 12f * fontScale, text)
        }
        month = month.plusMonths(1)
    }

    // 帯ごとに、大目的の名前・期間の塗り・中日程のバー・予定の点を描く。
    var y = rulerDp
    var hiddenMilestones = 0
    bands.forEach { band ->
        val color = if (band.colorSlot >= 0) connectorColor(band.colorSlot, isDark).toArgb() else primary
        val bandHeight = band.heightDp(start, end, headerDp)
        hiddenMilestones += band.barsIn(start, end).count { it.track >= MAX_TRACKS }
        val spanStart = band.spanStart
        val spanEnd = band.spanEnd
        if (band.kind == SwimBandKind.GOAL && spanStart != null && spanEnd != null) {
            val l = max(xOf(spanStart), left)
            val r = min(xOf(spanEnd.plusDays(1)), right)
            if (r > l) {
                fill.color = color
                fill.alpha = 36
                canvas.drawRect(l, y, r, y + bandHeight, fill)
            }
        }
        // 見出し(大目的の名前)。
        fill.color = color
        fill.alpha = 255
        canvas.drawCircle(left + 4f, y + headerDp / 2f, 3.5f, fill)
        text.color = onSurface
        text.textSize = 11f * fontScale
        text.isFakeBoldText = true
        val title = TextUtils.ellipsize(band.title, text, right - left - 14f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, left + 11f, y + headerDp / 2f + text.textSize * 0.35f, text)
        text.isFakeBoldText = false

        val bodyTop = y + headerDp
        fun drawDot(date: LocalDate, cy: Float, past: Boolean) {
            if (date.isBefore(start) || date.isAfter(end)) return
            val cx = xOf(date) + dayDp / 2f
            if (past) {
                stroke.color = subText
                stroke.strokeWidth = 1.2f
                stroke.pathEffect = null
                canvas.drawCircle(cx, cy, 2.2f, stroke)
            } else {
                fill.color = color
                fill.alpha = 255
                canvas.drawCircle(cx, cy, 2.5f, fill)
            }
        }
        band.bars.forEach { bar ->
            // 段の上限を超えた中日程は描かない(アプリの全体マップで見られる)。
            if (bar.track >= MAX_TRACKS) return@forEach
            val top = bodyTop + bar.track * (BAR_DP + DOT_ROW_DP + TRACK_GAP_DP)
            val l = max(xOf(bar.start), left)
            val r = min(xOf(bar.end.plusDays(1)), right)
            if (r > l) {
                val done = bar.status == NodeStatus.DONE
                fill.color = if (done) doneBar else color
                fill.alpha = if (bar.estimated) 110 else 255
                canvas.drawRoundRect(RectF(l, top, max(r, l + 3f), top + BAR_DP), 4f, 4f, fill)
                // 名前は、バーの中に入るときだけ描く。
                text.textSize = 10f * fontScale
                text.color = when {
                    done -> subText
                    bar.estimated -> onSurface
                    androidx.compose.ui.graphics.Color(color).luminance() > 0.18f -> 0xFF1A1C19.toInt()
                    else -> 0xFFFFFFFF.toInt()
                }
                // 名前はバーの中に入るときだけ描く(外には出さない)。
                val available = r - l - 8f
                if (available > 24f) {
                    val name = TextUtils.ellipsize(bar.milestone.title, text, available, TextUtils.TruncateAt.END).toString()
                    if (name.isNotEmpty()) canvas.drawText(name, l + 4f, top + BAR_DP / 2f + text.textSize * 0.35f, text)
                }
            }
            val dotY = top + BAR_DP + DOT_ROW_DP / 2f
            bar.chips.forEach { drawDot(it.schedule.date, dotY, it.past) }
        }
        band.looseChips.forEach { drawDot(it.schedule.date, bodyTop + DOT_ROW_DP / 2f, it.past) }

        y += bandHeight
        // 帯の区切り線。
        stroke.color = outline
        stroke.strokeWidth = 0.8f
        stroke.pathEffect = null
        canvas.drawLine(left, y + BAND_GAP_DP / 2f, right, y + BAND_GAP_DP / 2f, stroke)
        y += BAND_GAP_DP
    }

    // 今日の線(アプリと同じ色の破線)と、上の「今日」。
    val todayX = xOf(today) + dayDp / 2f
    stroke.color = todayColor
    stroke.strokeWidth = 1.5f
    stroke.pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    canvas.drawLine(todayX, rulerDp - 2f, todayX, heightDp, stroke)
    stroke.pathEffect = null

    val hidden = inWindow.size - bands.size
    val description = "タイムライン ${start.monthValue}月${start.dayOfMonth}日〜${end.monthValue}月${end.dayOfMonth}日。" +
        bands.joinToString("、") { band ->
            val schedules = band.bars.sumOf { it.chips.size } + band.looseChips.size
            "${band.title}(中日程${band.bars.size}件・予定${schedules}件)"
        } + (if (hidden > 0) "。ほか${hidden}件" else "") + "。タップで全体マップを開く"
    return WidgetTimeline(bitmap, heightDp, start, end, hidden, hiddenMilestones, description)
}
