package com.politex.weather

/**
 * Тумблер «Погодные эффекты» в настройках.
 *
 * Хранится по [id], а не по `name`: R8 не может сломать сохранённый выбор
 * (тот же приём, что в [com.politex.ui.theme.ThemeMode]).
 * [AUTO] — рисуем то, что реально идёт за окном; ручной выбор сильнее прогноза.
 */
enum class EffectsMode(
    val id: String,
    val title: String,
) {
    AUTO("auto", "Авто"),
    RAIN("rain", "Дождь"),
    SNOW("snow", "Снег"),
    OFF("off", "Выключено"),
    ;

    companion object {
        fun from(id: String?): EffectsMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Тумблер «Небо»: ночной градиент и звёзды. */
enum class SkyMode(
    val id: String,
    val title: String,
) {
    AUTO("auto", "Авто"),
    DAY("day", "День"),
    NIGHT("night", "Ночь"),
    ;

    companion object {
        fun from(id: String?): SkyMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Готовое решение для отрисовки: что рисовать, ночь ли и можно ли анимировать.
 *
 * Собирается [resolveWeatherScene]; слой эффектов ничего не знает про настройки,
 * прогноз и тему — только про эти пять полей.
 */
data class WeatherScene(
    val precipitation: Precipitation,
    val night: Boolean,
    val stars: Boolean,
    val animated: Boolean,
) {
    /** Нужно ли вообще что-то рисовать поверх фона. */
    val hasEffects: Boolean
        get() = precipitation != Precipitation.NONE || stars || night
}

/**
 * Осадки с учётом ручного тумблера.
 *
 * Приоритет ручного выбора над авто — требование заказчика: заказчик на превью
 * включает «Снег» и должен увидеть снег независимо от прогноза.
 */
fun resolvePrecipitation(
    mode: EffectsMode,
    snapshot: WeatherSnapshot?,
): Precipitation = when (mode) {
    EffectsMode.OFF -> Precipitation.NONE
    EffectsMode.RAIN -> Precipitation.RAIN
    EffectsMode.SNOW -> Precipitation.SNOW
    EffectsMode.AUTO -> snapshot?.precipitation ?: Precipitation.NONE
}

/** Ночь ли сейчас: ручной выбор «Небо» сильнее `is_day` из прогноза. */
fun resolveNight(
    mode: SkyMode,
    snapshot: WeatherSnapshot?,
): Boolean = when (mode) {
    SkyMode.DAY -> false
    SkyMode.NIGHT -> true
    SkyMode.AUTO -> snapshot?.isNight ?: false
}

/**
 * Звёзды видны только ночью и только в ясном небе.
 *
 * Дождь и снег закрывают небо целиком — звёзды в осадки не рисуются никогда.
 * Тема на само решение больше не влияет: в светлой теме ночные звёзды рисуются
 * приглушённым тёмно-синим (иначе белые точки на белом фоне не читались бы),
 * а фон контента остаётся светлым — см. `Modifier.weatherScene`.
 *
 * [darkTheme] сохранён в сигнатуре: решение о цвете принимает слой отрисовки,
 * а не правило видимости, но старые вызовы и тесты остаются совместимыми.
 */
fun resolveStars(
    night: Boolean,
    darkTheme: Boolean,
    precipitation: Precipitation,
): Boolean = night && precipitation == Precipitation.NONE

/**
 * Можно ли анимировать слой.
 *
 * Анимация в системе отключена (ANIMATOR_DURATION_SCALE == 0) или включён режим
 * энергосбережения — рисуем один статичный кадр: батарея и доступность важнее.
 */
fun effectsAnimationAllowed(
    animatorScaleZero: Boolean,
    powerSaveMode: Boolean,
): Boolean = !animatorScaleZero && !powerSaveMode

/** Сборка решения для слоя: единственная точка, где встречаются настройки и прогноз. */
fun resolveWeatherScene(
    effects: EffectsMode,
    sky: SkyMode,
    snapshot: WeatherSnapshot?,
    darkTheme: Boolean,
    animationsAllowed: Boolean,
): WeatherScene {
    val precipitation = resolvePrecipitation(effects, snapshot)
    val night = resolveNight(sky, snapshot)
    return WeatherScene(
        precipitation = precipitation,
        night = night,
        stars = resolveStars(night, darkTheme, precipitation),
        animated = animationsAllowed,
    )
}