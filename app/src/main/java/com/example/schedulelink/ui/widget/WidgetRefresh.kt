package com.example.schedulelink.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

/**
 * androidx.glance.appwidget.updateAll<T>(context) というトップレベルのreified関数は、
 * このプロジェクトのKotlin/AGPの組み合わせでは引数の型解決が崩れてビルドが通らなかったため、
 * GlanceAppWidgetManagerで対象ウィジェットのIDを取得し、インスタンスのupdate()を
 * 個別に呼ぶ形で代替する。
 */
suspend fun refreshAgendaWidgets(context: Context) {
    val ids = GlanceAppWidgetManager(context).getGlanceIds(AgendaWidget::class.java)
    ids.forEach { id -> AgendaWidget().update(context, id) }
}

suspend fun refreshMonthMiniWidgets(context: Context) {
    val ids = GlanceAppWidgetManager(context).getGlanceIds(MonthMiniWidget::class.java)
    ids.forEach { id -> MonthMiniWidget().update(context, id) }
}
