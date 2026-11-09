package com.politex.weather

import android.content.Context
import org.json.JSONObject

/**
 * Разбор снимка в JSON и обратно.
 *
 * Плоские ключи и строковый enum: снимок переживает перезапуск, смену версии
 * и потерю сети. Битый или чужой JSON читается как `null`, а не роняет приложение.
 */
object WeatherSnapshotCodec {

    fun encode(snapshot: WeatherSnapshot): String = JSONObject()
        .put("p", snapshot.precipitation.name)
        .put("day", snapshot.isDay)
        .put("t", snapshot.temperatureC ?: JSONObject.NULL)
        .put("code", snapshot.weatherCode)
        .put("mm", snapshot.precipitationMm)
        .put("snow", snapshot.snowfallCm)
        .put("at", snapshot.fetchedAtMillis)
        .toString()

    fun decode(raw: String?): WeatherSnapshot? {
        if (raw.isNullOrBlank()) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (!json.has("p") || !json.has("at")) return null
        return WeatherSnapshot(
            precipitation = Precipitation.from(json.optString("p")),
            isDay = json.optBoolean("day", true),
            temperatureC = if (json.isNull("t")) null else json.optInt("t"),
            weatherCode = json.optInt("code", -1),
            precipitationMm = json.optDouble("mm", 0.0),
            snowfallCm = json.optDouble("snow", 0.0),
            fetchedAtMillis = json.optLong("at", 0L),
        )
    }
}

/**
 * Хранилище прогноза в SharedPreferences.
 *
 * DataStore в проекте отключён (закомментирован в зависимостях), поэтому —
 * те же SharedPreferences, что у остальных настроек.
 */
object WeatherStore {

    private const val PREFS_NAME = "mpk_weather_prefs"
    private const val KEY_SNAPSHOT = "weather_snapshot"
    private const val KEY_FETCHED_AT = "weather_fetched_at"
    private const val KEY_LAST_ATTEMPT = "weather_last_attempt"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readSnapshot(context: Context): WeatherSnapshot? =
        WeatherSnapshotCodec.decode(prefs(context).getString(KEY_SNAPSHOT, null))

    fun writeSnapshot(
        context: Context,
        snapshot: WeatherSnapshot,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_SNAPSHOT, WeatherSnapshotCodec.encode(snapshot))
            .putLong(KEY_FETCHED_AT, snapshot.fetchedAtMillis)
            .apply()
    }

    fun readFetchedAt(context: Context): Long? {
        val store = prefs(context)
        if (!store.contains(KEY_FETCHED_AT)) return null
        return store.getLong(KEY_FETCHED_AT, 0L)
    }

    fun readLastAttempt(context: Context): Long? {
        val store = prefs(context)
        if (!store.contains(KEY_LAST_ATTEMPT)) return null
        return store.getLong(KEY_LAST_ATTEMPT, 0L)
    }

    fun writeLastAttempt(
        context: Context,
        atMillis: Long,
    ) {
        prefs(context).edit().putLong(KEY_LAST_ATTEMPT, atMillis).apply()
    }
}