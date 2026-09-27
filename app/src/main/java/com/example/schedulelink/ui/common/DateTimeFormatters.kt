package com.example.schedulelink.ui.common

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * アプリ全体で使う日付・時刻の表示フォーマット。
 * 一部の画面でLocalDate/LocalTimeの生toString()(例: "2026-09-27")が
 * そのまま表示されてしまっていたため、月表示などで既に使っていた
 * 日本語形式に揃えるための共通定義。
 */
val commonDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
val commonTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
