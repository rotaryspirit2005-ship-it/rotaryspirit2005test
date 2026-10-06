package com.example.schedulelink.ui.flow

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// タイムラインの背景(月・週・日の線と今日の線)と、上端に固定するルーラー(月名・日付・「今日」)。
// ズーム・パンの変換の外側で、画面の座標のまま描く。そのため線の太さは拡大縮小しても変わらず、
// 見えている範囲だけを描けばよい。色は onSurface などのテーマ色に透明度をかけて作り、
// ライト/ダークのどちらでも「背景は静かに、接続線や今日の線ははっきり」になるようにしている。

/** 画面上のルーラーの高さ(1段目が月名、2段目が日付と「今日」)。 */
private const val RULER_HEIGHT_DP = 44f
private const val RULER_ROW1_Y_DP = 4f
private const val RULER_ROW2_Y_DP = 27f
private const val PILL_Y_DP = 25f
private const val PILL_HEIGHT_DP = 16f

/** 1日の画面上の幅(dp)がこれ以上で、週の線と土日の塗りが出始める(10dpで全開)。 */
private const val WEEK_VISIBLE_DAY_DP = 5f
/** 1日の幅がこれ以上で、日の線と日付の数字が出始める。 */
private const val DAY_VISIBLE_DAY_DP = 10f
/** 日の線が最大の濃さになる1日の幅。 */
private const val DAY_FULL_DAY_DP = 24f
/** 1日の幅がこれ以上のとき、日付の数字を毎日出す(未満は月曜だけ)。 */
private const val EVERY_DAY_LABEL_DP = 22f

private const val RULER_BACKGROUND_ALPHA = 0.94f

private class GridAlphas(
    val band: Float,
    val month: Float,
    val year: Float,
    val week: Float,
    val day: Float,
    val weekend: Float,
    val todayBand: Float
)

private val LightAlphas = GridAlphas(
    band = 0.035f, month = 0.28f, year = 0.45f, week = 0.10f, day = 0.14f, weekend = 0.05f, todayBand = 0.10f
)
private val DarkAlphas = GridAlphas(
    band = 0.05f, month = 0.32f, year = 0.50f, week = 0.12f, day = 0.18f, weekend = 0.06f, todayBand = 0.14f
)

class GridColors(
    val isDark: Boolean,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val surface: Color,
    val outlineVariant: Color,
    val today: Color,
    val onToday: Color,
    val saturday: Color,
    val sunday: Color
)

/** 月の区切り(各月の1日と、ルーラーに出す月名)。 */
class GridModel(val minDate: LocalDate, val maxDate: LocalDate, val months: List<Pair<LocalDate, String>>)

class RulerStyles(val month: TextStyle, val day: TextStyle, val pill: TextStyle)

/** 日付から画面のx座標を求めるための対応(画面のpxで持つ)。 */
private class Axis(
    val minDate: LocalDate,
    val maxDate: LocalDate,
    val originX: Float,
    val dayPx: Float,
    val density: Float,
    val width: Float
) {
    val totalDays: Long = ChronoUnit.DAYS.between(minDate, maxDate)
    /** 1日の画面上の幅(dp)。表示する細かさの目安にする。 */
    val dayDp: Float = dayPx / density
    val firstVisible: Long = floor(-originX / dayPx).toLong().coerceIn(0L, totalDays)
    val lastVisible: Long = ceil((width - originX) / dayPx).toLong().coerceIn(0L, totalDays)

    fun x(days: Long): Float = originX + days * dayPx
    fun daysOf(date: LocalDate): Long = ChronoUnit.DAYS.between(minDate, date)
}

private fun DrawScope.axisOf(zp: ZoomPanState, model: GridModel): Axis {
    val unit = density * zp.scale
    return Axis(
        minDate = model.minDate,
        maxDate = model.maxDate,
        originX = zp.offset.x + MARGIN * unit,
        dayPx = PX_PER_DAY * unit,
        density = density,
        width = size.width
    )
}

private fun isWeekend(date: LocalDate): Boolean =
    date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

/** カードや接続線の下に敷く背景: 月の交互の帯、月・年・週・日の線、土日の塗り、今日の線。 */
fun DrawScope.drawGridBackdrop(zp: ZoomPanState, model: GridModel, colors: GridColors, today: LocalDate?) {
    val axis = axisOf(zp, model)
    val alphas = if (colors.isDark) DarkAlphas else LightAlphas
    val h = size.height

    // 月。1つおきの月に薄い帯を敷くと、線や文字がなくても「いまどの月か」が分かる。
    model.months.forEach { (start, _) ->
        val startDays = axis.daysOf(start)
        val endDays = axis.daysOf(start.plusMonths(1))
        val startX = axis.x(startDays)
        val endX = axis.x(endDays)
        if (endX < 0f || startX > size.width) return@forEach
        if (start.monthValue % 2 == 1) {
            drawRect(colors.onSurface.copy(alpha = alphas.band), Offset(startX, 0f), Size(endX - startX, h))
        }
        val isYear = start.monthValue == 1
        drawLine(
            color = colors.onSurface.copy(alpha = if (isYear) alphas.year else alphas.month),
            start = Offset(startX, 0f),
            end = Offset(startX, h),
            strokeWidth = (if (isYear) 2.dp else 1.dp).toPx()
        )
    }

    // 週(月曜の左)・日の線、土日の塗り。縮小して1日が細いときは出さない。
    val weekProgress = ((axis.dayDp - WEEK_VISIBLE_DAY_DP) / (DAY_VISIBLE_DAY_DP - WEEK_VISIBLE_DAY_DP)).coerceIn(0f, 1f)
    val dayProgress = ((axis.dayDp - DAY_VISIBLE_DAY_DP) / (DAY_FULL_DAY_DP - DAY_VISIBLE_DAY_DP)).coerceIn(0f, 1f)
    if (weekProgress > 0f) {
        for (d in axis.firstVisible..axis.lastVisible) {
            val date = model.minDate.plusDays(d)
            val x = axis.x(d)
            if (isWeekend(date)) {
                drawRect(
                    colors.onSurface.copy(alpha = alphas.weekend * weekProgress),
                    Offset(x, 0f),
                    Size(axis.dayPx, h)
                )
            }
            if (date.dayOfWeek == DayOfWeek.MONDAY) {
                drawLine(
                    color = colors.onSurface.copy(alpha = alphas.week * weekProgress),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1.dp.toPx()
                )
            }
            if (dayProgress > 0f) {
                drawLine(
                    color = colors.onSurface.copy(alpha = alphas.day * dayProgress),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
        }
    }

    // 今日。カードや接続線の下に敷き、文字に重ならないようにする。帯の幅は1日分(狭いときは16dp)。
    if (today != null) {
        val d = axis.daysOf(today)
        if (d in 0..axis.totalDays) {
            val x = axis.x(d)
            // 帯は今日の1日分の枠(線は枠の左端)。1日が細いときは16dpまで広げる。
            val bandWidth = max(axis.dayPx, 16.dp.toPx())
            val bandLeft = x + axis.dayPx / 2f - bandWidth / 2f
            drawRect(colors.today.copy(alpha = alphas.todayBand), Offset(bandLeft, 0f), Size(bandWidth, h))
            drawLine(colors.today, Offset(x, 0f), Offset(x, h), strokeWidth = 2.dp.toPx())
        }
    }
}

/** 上端に固定するルーラー。パンやズームをしても、いまの月・日付と今日の位置が読める。 */
fun DrawScope.drawGridRuler(
    zp: ZoomPanState,
    model: GridModel,
    colors: GridColors,
    today: LocalDate?,
    measurer: TextMeasurer,
    styles: RulerStyles
) {
    val axis = axisOf(zp, model)
    val alphas = if (colors.isDark) DarkAlphas else LightAlphas
    val w = size.width
    // 文字を大きくする設定のときは、ルーラーの縦の寸法も同じ割合で広げる(文字がはみ出さないように)。
    val vs = fontScale.coerceIn(1f, 1.5f)
    val rulerH = RULER_HEIGHT_DP.dp.toPx() * vs

    fun measure(text: String, style: TextStyle) = measurer.measure(text, style, softWrap = false, maxLines = 1)

    drawRect(colors.surface.copy(alpha = RULER_BACKGROUND_ALPHA), Offset.Zero, Size(w, rulerH))
    drawLine(colors.outlineVariant, Offset(0f, rulerH), Offset(w, rulerH), strokeWidth = 1.dp.toPx())

    // 月名。画面の左端をまたぐ月は名前を左端に寄せ、次の月が来たら押し出す(常に何月か分かる)。
    model.months.forEach { (start, label) ->
        val startX = axis.x(axis.daysOf(start))
        val nextX = axis.x(axis.daysOf(start.plusMonths(1)))
        if (nextX < 0f || startX > w) return@forEach
        drawLine(
            color = colors.onSurface.copy(alpha = alphas.month),
            start = Offset(startX, 0f),
            end = Offset(startX, rulerH),
            strokeWidth = 1.dp.toPx()
        )
        val layout = measure(label, styles.month)
        val textW = layout.size.width.toFloat()
        val x = min(max(startX + 4.dp.toPx(), 4.dp.toPx()), nextX - textW - 8.dp.toPx())
        if (x + textW < 0f) return@forEach
        drawText(
            layout,
            color = colors.onSurface.copy(alpha = 0.87f),
            topLeft = Offset(x, RULER_ROW1_Y_DP.dp.toPx() * vs)
        )
    }

    // 日付の数字(2段目)。拡大するほど細かく(月曜だけ → 毎日)出る。土日は曜日の色。
    val labelProgress = ((axis.dayDp - DAY_VISIBLE_DAY_DP) / 6f).coerceIn(0f, 1f)
    if (labelProgress > 0f) {
        val everyDay = axis.dayDp >= EVERY_DAY_LABEL_DP
        for (d in axis.firstVisible..axis.lastVisible) {
            val date = model.minDate.plusDays(d)
            if (date == today) continue // 「今日」のピルに置き換える
            if (!everyDay && date.dayOfWeek != DayOfWeek.MONDAY) continue
            val base = when (date.dayOfWeek) {
                DayOfWeek.SATURDAY -> colors.saturday
                DayOfWeek.SUNDAY -> colors.sunday
                else -> colors.onSurfaceVariant
            }
            drawText(
                measure(date.dayOfMonth.toString(), styles.day),
                color = base.copy(alpha = labelProgress),
                topLeft = Offset(axis.x(d) + 3.dp.toPx(), RULER_ROW2_Y_DP.dp.toPx() * vs)
            )
        }
    }

    // 「今日」のピル。画面の外にあるときは端に寄せ、どちらにあるかを矢印で示す。
    if (today != null) {
        val d = axis.daysOf(today)
        if (d in 0..axis.totalDays) {
            val todayX = axis.x(d) + axis.dayPx / 2f
            val text = when {
                todayX < 0f -> "‹ 今日"
                todayX > w -> "今日 ›"
                axis.dayDp >= EVERY_DAY_LABEL_DP -> "今日 ${today.monthValue}/${today.dayOfMonth}"
                else -> "今日"
            }
            val layout = measure(text, styles.pill)
            val pillW = layout.size.width + 12.dp.toPx()
            val pillH = PILL_HEIGHT_DP.dp.toPx() * vs
            val pillX = (todayX - pillW / 2f).coerceIn(4.dp.toPx(), max(4.dp.toPx(), w - pillW - 4.dp.toPx()))
            val pillY = PILL_Y_DP.dp.toPx() * vs
            drawRoundRect(colors.today, Offset(pillX, pillY), Size(pillW, pillH), CornerRadius(pillH / 2f))
            drawText(
                layout,
                color = colors.onToday,
                topLeft = Offset(pillX + 6.dp.toPx(), pillY + (pillH - layout.size.height) / 2f)
            )
        }
    }
}
