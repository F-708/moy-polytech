package com.politex.weather

import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Разбор ответа Open-Meteo.
 *
 * Берём не первый час из `hourly`, а текущий: Open-Meteo с `forecast_days=1`
 * отдаёт массивы с местной полуночи, поэтому индекс — это час из `current.time`.
 * Так «снег сейчас» не превращается в «снег в 00:00».
 *
 * Любая неожиданность (битый JSON, нет `current`) — `null`: вызывающий код
 * оставляет прошлый снимок и молчит, экран не показывает ошибку.
 */
object OpenMeteoParser {

    fun parse(
        json: String,
        nowMillis: Long,
    ): WeatherSnapshot? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val current = root.optJSONObject("current") ?: return null
        val weatherCode = current.optInt("weather_code", Int.MIN_VALUE)
        if (weatherCode == Int.MIN_VALUE) return null

        val hourly = root.optJSONObject("hourly")
        val hourIndex = hourIndexOf(current.optString("time"), hourly)

        val precipitationHourly = hourly?.optJSONArray("precipitation")?.optDoubleOrNull(hourIndex)
        val snowfallHourly = hourly?.optJSONArray("snowfall")?.optDoubleOrNull(hourIndex)
        val codeHourly = hourly?.optJSONArray("weather_code")?.optDoubleOrNull(hourIndex)

        val precipitationMm = precipitationHourly ?: current.optDouble("precipitation", 0.0)
        val snowfallCm = snowfallHourly ?: 0.0
        val effectiveCode = codeHourly?.roundToInt() ?: weatherCode

        val temperature = current.optDouble("temperature_2m", Double.NaN)

        return WeatherSnapshot(
            precipitation = classifyPrecipitation(precipitationMm, snowfallCm, effectiveCode),
            isDay = current.optInt("is_day", 1) == 1,
            temperatureC = if (temperature.isNaN()) null else temperature.roundToInt(),
            weatherCode = weatherCode,
            precipitationMm = precipitationMm,
            snowfallCm = snowfallCm,
            fetchedAtMillis = nowMillis,
        )
    }

    /** Час из `current.time` («2026-11-08T14:00») с поправкой на длину массива. */
    internal fun hourIndexOf(
        currentTime: String,
        hourly: JSONObject?,
    ): Int {
        val hour = currentTime.substringAfter('T', "").take(2).toIntOrNull() ?: 0
        val size = hourly?.optJSONArray("time")?.length() ?: 0
        if (size <= 0) return hour
        return hour.coerceIn(0, size - 1)
    }

    private fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? {
        if (index < 0 || index >= length()) return null
        if (isNull(index)) return null
        val value = optDouble(index, Double.NaN)
        return if (value.isNaN()) null else value
    }
}

/**
 * Клиент Open-Meteo для точки колледжа (53.9239, 27.6033).
 *
 * Сеть — тот же OkHttp, что и у расписания ([com.politex.data.network.MpkNetworkClient]):
 * без новых зависимостей и разрешений. Ключ не нужен: Open-Meteo бесплатен для
 * некоммерческого использования и отдаёт прогноз без регистрации.
 */
object OpenMeteoClient {

    const val URL =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=53.9239&longitude=27.6033" +
            "&current=temperature_2m,weather_code,precipitation,is_day" +
            "&hourly=precipitation,snowfall,weather_code" +
            "&timezone=Europe/Minsk&forecast_days=1"

    private val USER_AGENT = "MpkApp/2.5 (Android ${Build.VERSION.SDK_INT})"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6000, TimeUnit.MILLISECONDS)
            .readTimeout(6000, TimeUnit.MILLISECONDS)
            .callTimeout(9000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Один запрос прогноза. Возвращает `null` при любой проблеме — вызывающий
     * оставляет кэш как есть (правило «без ошибок на экране»).
     */
    fun fetch(
        nowMillis: Long = System.currentTimeMillis(),
        httpClient: OkHttpClient = client,
    ): WeatherSnapshot? = try {
        val request =
            Request.Builder()
                .url(URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.string()?.let { OpenMeteoParser.parse(it, nowMillis) }
            }
        }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}