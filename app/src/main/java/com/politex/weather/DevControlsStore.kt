package com.politex.weather

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

/**
 * Ручные переключатели погоды/неба/сезона для отладки (AdminPlus).
 *
 * Хранятся в SharedPreferences по строковым id enum'ов (R8-безопасно).
 * По умолчанию всё в AUTO — приложение ведёт себя как обычно.
 * [revision] — Compose-счётчик: растёт при каждой записи, чтобы слой перечитал сцену сразу.
 */
object DevControlsStore {
    val revision = mutableIntStateOf(0)
    private const val PREFS_NAME = "mpk_dev_controls"
    private const val KEY_EFFECTS = "effects_mode"
    private const val KEY_SKY = "sky_mode"
    private const val KEY_SEASON = "season"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readEffects(context: Context): EffectsMode = EffectsMode.from(prefs(context).getString(KEY_EFFECTS, null))
    fun readSky(context: Context): SkyMode = SkyMode.from(prefs(context).getString(KEY_SKY, null))
    fun readSeason(context: Context): Season = Season.from(prefs(context).getString(KEY_SEASON, null))

    fun writeEffects(context: Context, mode: EffectsMode) {
        prefs(context).edit().putString(KEY_EFFECTS, mode.id).apply()
        revision.intValue++
    }

    fun writeSky(context: Context, mode: SkyMode) {
        prefs(context).edit().putString(KEY_SKY, mode.id).apply()
        revision.intValue++
    }

    fun writeSeason(context: Context, season: Season) {
        prefs(context).edit().putString(KEY_SEASON, season.id).apply()
        revision.intValue++
    }
}
