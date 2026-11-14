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
    val season: Season = Season.NONE,
) {
    /** Нужно ли вообще что-то рисовать поверх фона. */
    val hasEffects: Boolean
        get() = precipitation != Precipitation.NONE || stars || night || season != Season.NONE
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

/**
 * Ночь ли сейчас: ручной выбор «Небо» сильнее астрономии.
 *
 * AUTO считает восход/заход солнца в точке колледжа ([SolarNight]): заказчик
 * просил звёзды «когда на улице реально темно», а `is_day` прогноза может и не
 * приехать вовсе. [nowMillis] явный — правило тестируется без подмены часов.
 */
fun resolveNight(
    mode: SkyMode,
    snapshot: WeatherSnapshot?,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean = when (mode) {
    SkyMode.DAY -> false
    SkyMode.NIGHT -> true
    SkyMode.AUTO -> SolarNight.isNight(nowMillis)
}

/**
 * Звёзды видны только ночью, только в ясном небе и только в тёмной теме.
 *
 * Дождь и снег закрывают небо целиком — звёзды в осадки не рисуются никогда.
 * Заказчик (2026-11-13): при светлой теме звёзд быть не должно вовсе — раньше
 * они рисовались приглушённо-синими на светлом фоне, теперь тема гейтит слой.
 */
fun resolveStars(
    night: Boolean,
    darkTheme: Boolean,
    precipitation: Precipitation,
    deepNight: Boolean = true,
): Boolean = night && darkTheme && deepNight && precipitation == Precipitation.NONE

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
    season: Season = currentSeason(),
    nowMillis: Long = System.currentTimeMillis(),
): WeatherScene {
    val precipitation = resolvePrecipitation(effects, snapshot)
    val night = resolveNight(sky, snapshot, nowMillis)
    // Заказчик (2026-11-13): в AUTO звёзды только глубокой ночью. Ручные режимы
    // не гейтим: включил «Ночь» — получил ночное небо сразу.
    val deepNight = sky != SkyMode.AUTO || SolarNight.isDeepNight(nowMillis)
    return WeatherScene(
        precipitation = precipitation,
        night = night,
        stars = resolveStars(night, darkTheme, precipitation, deepNight),
        animated = animationsAllowed,
        // Декор не рисуется при выключенных эффектах: OFF гасит и погоду, и сезон.
        season = if (effects == EffectsMode.OFF) Season.NONE else season,
    )
}