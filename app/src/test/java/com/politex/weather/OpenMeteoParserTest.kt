package com.politex.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Разбор ответа Open-Meteo: дождь, снег, ясно и мусор вместо JSON.
 *
 * Robolectric нужен только из-за `org.json` (в Android это часть платформы,
 * на чистой JVM её нет). Правила классификации проверяются без него —
 * в [com.politex.weather.WeatherPolicyTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenMeteoParserTest {

    private val now = 1_762_600_000_000L

    private fun response(
        currentHour: Int,
        precipitation: List<Double>,
        snowfall: List<Double>,
        code: Int,
        isDay: Int = 1,
        temperature: Double = 3.4,
        hourlyCode: Int? = null,
    ): String {
        val times = (0..23).joinToString(",") { hour ->
            "\"2026-11-08T${hour.toString().padStart(2, '0')}:00\""
        }
        val codes = (0..23).joinToString(",") { (hourlyCode ?: code).toString() }
        val currentTime = "2026-11-08T${currentHour.toString().padStart(2, '0')}:00"
        return """
            {
              "utc_offset_seconds": 10800,
              "timezone": "Europe/Minsk",
              "current": {
                "time": "$currentTime",
                "temperature_2m": $temperature,
                "weather_code": $code,
                "precipitation": ${precipitation.first()},
                "is_day": $isDay
              },
              "hourly": {
                "time": [$times],
                "precipitation": [${precipitation.joinToString(",")}],
                "snowfall": [${snowfall.joinToString(",")}],
                "weather_code": [$codes]
              }
            }
        """.trimIndent()
    }

    private fun zerosAt(hour: Int, value: Double): List<Double> =
        (0..23).map { if (it == hour) value else 0.0 }

    @Test
    fun `дождь в текущий час`() {
        val json = response(
            currentHour = 14,
            precipitation = zerosAt(hour = 14, value = 0.4),
            snowfall = zerosAt(hour = 14, value = 0.0),
            code = 61,
        )

        val snapshot = OpenMeteoParser.parse(json, now)

        assertEquals(Precipitation.RAIN, snapshot?.precipitation)
        assertEquals(0.4, snapshot?.precipitationMm ?: -1.0, 0.0001)
        assertEquals(61, snapshot?.weatherCode)
        assertEquals(now, snapshot?.fetchedAtMillis)
        assertEquals(3, snapshot?.temperatureC)
    }

    @Test
    fun `берётся текущий час, а не первый`() {
        val precipitation = zerosAt(hour = 0, value = 9.9).toMutableList()
        precipitation[14] = 0.4

        val snapshot = OpenMeteoParser.parse(
            response(
                currentHour = 14,
                precipitation = precipitation,
                snowfall = zerosAt(hour = 14, value = 0.0),
                code = 61,
            ),
            now,
        )

        assertEquals(Precipitation.RAIN, snapshot?.precipitation)
        assertEquals(0.4, snapshot?.precipitationMm ?: -1.0, 0.0001)
    }

    @Test
    fun `снег определяется по snowfall`() {
        val snapshot = OpenMeteoParser.parse(
            response(
                currentHour = 8,
                precipitation = zerosAt(hour = 8, value = 0.0),
                snowfall = zerosAt(hour = 8, value = 0.6),
                code = 3,
            ),
            now,
        )

        assertEquals(Precipitation.SNOW, snapshot?.precipitation)
        assertEquals(0.6, snapshot?.snowfallCm ?: -1.0, 0.0001)
    }

    @Test
    fun `ясно без осадков и без снежных кодов`() {
        val snapshot = OpenMeteoParser.parse(
            response(
                currentHour = 12,
                precipitation = zerosAt(hour = 12, value = 0.0),
                snowfall = zerosAt(hour = 12, value = 0.0),
                code = 0,
                temperature = -2.6,
            ),
            now,
        )

        assertEquals(Precipitation.NONE, snapshot?.precipitation)
        assertEquals(-3, snapshot?.temperatureC)
        assertTrue(snapshot?.isDay == true)
    }

    @Test
    fun `ночь берётся из is_day`() {
        val snapshot = OpenMeteoParser.parse(
            response(
                currentHour = 23,
                precipitation = zerosAt(hour = 23, value = 0.0),
                snowfall = zerosAt(hour = 23, value = 0.0),
                code = 0,
                isDay = 0,
            ),
            now,
        )

        assertEquals(false, snapshot?.isDay)
        assertEquals(true, snapshot?.isNight)
    }

    @Test
    fun `снежный код без осадков тоже снег`() {
        val snapshot = OpenMeteoParser.parse(
            response(
                currentHour = 6,
                precipitation = zerosAt(hour = 6, value = 0.0),
                snowfall = zerosAt(hour = 6, value = 0.0),
                code = 73,
            ),
            now,
        )

        assertEquals(Precipitation.SNOW, snapshot?.precipitation)
    }

    @Test
    fun `гроза без осадков считается дождём`() {
        val snapshot = OpenMeteoParser.parse(
            response(
                currentHour = 17,
                precipitation = zerosAt(hour = 17, value = 0.0),
                snowfall = zerosAt(hour = 17, value = 0.0),
                code = 95,
            ),
            now,
        )

        assertEquals(Precipitation.RAIN, snapshot?.precipitation)
    }

    @Test
    fun `битый json — null`() {
        assertNull(OpenMeteoParser.parse("не json", now))
        assertNull(OpenMeteoParser.parse("", now))
        assertNull(OpenMeteoParser.parse("""{"hourly":{}}""", now))
    }

    @Test
    fun `нет current — null`() {
        assertNull(OpenMeteoParser.parse("""{"latitude":53.9,"hourly":{"time":[]}}""", now))
    }

    @Test
    fun `снимок переживает запись и чтение`() {
        val snapshot = WeatherSnapshot(
            precipitation = Precipitation.SNOW,
            isDay = false,
            temperatureC = -4,
            weatherCode = 73,
            precipitationMm = 0.0,
            snowfallCm = 0.8,
            fetchedAtMillis = now,
        )

        val decoded = WeatherSnapshotCodec.decode(WeatherSnapshotCodec.encode(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `испорченный кэш читается как отсутствие данных`() {
        assertNull(WeatherSnapshotCodec.decode(null))
        assertNull(WeatherSnapshotCodec.decode(""))
        assertNull(WeatherSnapshotCodec.decode("{}"))
        assertNull(WeatherSnapshotCodec.decode("не json"))
    }
}