package com.politex.weather

import java.util.Calendar

/**
 * Сезонное оформление эмбиент-слоя поверх погоды.
 *
 * Отдельно от осадков: снег идёт по прогнозу, а гирлянда — по календарю.
 * Новый год не отменяет снег — декор рисуется поверх.
 * Хэллоуин убран по решению заказчика (2026-11-12): тыквы не в стиле продукта.
 */
enum class Season(val id: String) {
    NONE("none"),
    NEW_YEAR("new_year"),
    ;

    companion object {
        fun from(id: String?): Season = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Активный сезон по дате.
 *
 * Окно выбрано по «вайбу», а не по точному дню:
 *  - Новый год: 20 декабря – 10 января (когда уже снег и чувствуется приближение).
 *
 * Чистая функция от (месяц, день) — тестируется без Android.
 */
fun seasonForDate(month: Int, dayOfMonth: Int): Season = when {
    month == Calendar.DECEMBER && dayOfMonth >= 20 -> Season.NEW_YEAR
    month == Calendar.JANUARY && dayOfMonth <= 10 -> Season.NEW_YEAR
    else -> Season.NONE
}

/** Сезон прямо сейчас по системным часам. */
fun currentSeason(now: Calendar = Calendar.getInstance()): Season =
    seasonForDate(now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
