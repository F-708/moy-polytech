package com.politex.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Часовой TTL кэша и правило «не чаще раза в час» — на чистых функциях,
 * без Android и без сети: [WeatherCache] принимает текущее время снаружи.
 */
class WeatherCacheTest {

    private val now = 1_762_600_000_000L
    private val minute = 60_000L

    @Test
    fun `свежий прогноз живёт час`() {
        assertTrue(WeatherCache.isFresh(now - minute, now))
        assertTrue(WeatherCache.isFresh(now, now))
        assertTrue(WeatherCache.isFresh(now - WeatherCache.TTL_MS + minute, now))
    }

    @Test
    fun `через час прогноз протух`() {
        assertFalse(WeatherCache.isFresh(now - WeatherCache.TTL_MS, now))
        assertFalse(WeatherCache.isFresh(now - WeatherCache.TTL_MS - minute, now))
    }

    @Test
    fun `без данных запрос разрешён`() {
        assertTrue(WeatherCache.shouldFetch(fetchedAtMillis = null, lastAttemptMillis = null, nowMillis = now))
    }

    @Test
    fun `пока прогноз свежий, сеть не трогаем`() {
        assertFalse(
            WeatherCache.shouldFetch(
                fetchedAtMillis = now - minute,
                lastAttemptMillis = now - minute,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `протухший прогноз обновляем не чаще раза в час`() {
        val fetchedAt = now - WeatherCache.TTL_MS - 5 * minute
        assertFalse(
            WeatherCache.shouldFetch(
                fetchedAtMillis = fetchedAt,
                lastAttemptMillis = now - 10 * minute,
                nowMillis = now,
            ),
        )
        assertTrue(
            WeatherCache.shouldFetch(
                fetchedAtMillis = fetchedAt,
                lastAttemptMillis = now - WeatherCache.TTL_MS,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `без данных повтор — не раньше пятнадцати минут`() {
        assertFalse(
            WeatherCache.shouldFetch(
                fetchedAtMillis = null,
                lastAttemptMillis = now - 5 * minute,
                nowMillis = now,
            ),
        )
        assertTrue(
            WeatherCache.shouldFetch(
                fetchedAtMillis = null,
                lastAttemptMillis = now - WeatherCache.RETRY_WITHOUT_DATA_MS,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `часы уехали назад — запрос не делаем`() {
        assertFalse(
            WeatherCache.shouldFetch(
                fetchedAtMillis = null,
                lastAttemptMillis = now + 10 * minute,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `остаток жизни снимка`() {
        assertEquals(WeatherCache.TTL_MS, WeatherCache.remainingMs(now, now))
        assertEquals(WeatherCache.TTL_MS - minute, WeatherCache.remainingMs(now - minute, now))
        assertEquals(0L, WeatherCache.remainingMs(now - WeatherCache.TTL_MS - minute, now))
        assertEquals(0L, WeatherCache.remainingMs(null, now))
    }
}