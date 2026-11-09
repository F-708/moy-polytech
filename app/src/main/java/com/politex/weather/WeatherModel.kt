package com.politex.weather

/**
 * Осадки, которые рисует эмбиент-слой.
 *
 * Отдельный enum, а не code погоды: UI должен знать только три состояния,
 * а правило перевода кода Open-Meteo живёт в [classifyPrecipitation].
 */
enum class Precipitation {
    NONE,
    RAIN,
    SNOW,
    ;

    companion object {
        fun from(name: String?): Precipitation = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Человеческая подпись для настроек и виджета. */
fun Precipitation.label(): String = when (this) {
    Precipitation.NONE -> "ясно"
    Precipitation.RAIN -> "дождь"
    Precipitation.SNOW -> "снег"
}

/**
 * Один снимок погоды в точке колледжа (53.9239, 27.6033).
 *
 * [fetchedAtMillis] — момент получения ответа, по нему считается TTL кэша.
 * [isDay] приходит из Open-Meteo (`is_day`: 1 — день, 0 — ночь).
 */
data class WeatherSnapshot(
    val precipitation: Precipitation,
    val isDay: Boolean,
    val temperatureC: Int?,
    val weatherCode: Int,
    val precipitationMm: Double,
    val snowfallCm: Double,
    val fetchedAtMillis: Long,
) {
    val isNight: Boolean get() = !isDay
}

/**
 * Правило «что рисовать» — единственное место, где код погоды превращается в осадки.
 *
 * Порядок проверок задан заказчиком: снег по фактическому snowfall, затем дождь
 * по precipitation, затем снежные и грозовые коды. Всё остальное — ясно.
 * Функция чистая: её проверяют юнит-тесты без Android.
 */
fun classifyPrecipitation(
    precipitationMm: Double,
    snowfallCm: Double,
    weatherCode: Int,
): Precipitation {
    if (snowfallCm > 0.0) return Precipitation.SNOW
    if (precipitationMm > 0.0) return Precipitation.RAIN
    val snowCodes = weatherCode in 71..77 || weatherCode == 85 || weatherCode == 86
    if (snowCodes) return Precipitation.SNOW
    if (weatherCode in 95..99) return Precipitation.RAIN
    return Precipitation.NONE
}