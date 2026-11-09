package com.politex.weather

/**
 * Правила кэша прогноза: часовой TTL и не чаще одной попытки обновления в час.
 *
 * Всё на чистых функциях и с внешними часами: юнит-тесты не зависят от устройства,
 * а [WeatherHub] передаёт реальное время. Ошибка сети не должна стирать последний
 * снимок — поэтому здесь нет ни одного «сброса» кэша.
 */
object WeatherCache {

    /** Свежий прогноз живёт час: Open-Meteo обновляется примерно с той же частотой. */
    const val TTL_MS: Long = 60L * 60L * 1000L

    /** Если данных нет вообще (первый запуск без сети) — пробуем раз в 15 минут. */
    const val RETRY_WITHOUT_DATA_MS: Long = 15L * 60L * 1000L

    /** Есть ли свежий снимок: он показывается без обращения к сети. */
    fun isFresh(
        fetchedAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (fetchedAtMillis == null) return false
        val age = nowMillis - fetchedAtMillis
        return age in 0 until TTL_MS
    }

    /**
     * Пора ли идти в сеть.
     *
     * [fetchedAtMillis] — когда получен последний удачный ответ (null — данных нет),
     * [lastAttemptMillis] — когда последний раз пытались (успешно или нет).
     * Часы, уехавшие назад, трактуются как «не время»: лишний запрос хуже, чем
     * отсутствие обновления в этом кадре.
     */
    fun shouldFetch(
        fetchedAtMillis: Long?,
        lastAttemptMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (isFresh(fetchedAtMillis, nowMillis)) return false
        if (lastAttemptMillis == null) return true
        val sinceAttempt = nowMillis - lastAttemptMillis
        if (sinceAttempt < 0) return false
        if (fetchedAtMillis == null) return sinceAttempt >= RETRY_WITHOUT_DATA_MS
        return sinceAttempt >= TTL_MS
    }

    /** Оставшееся время жизни снимка в миллисекундах (0 — уже протух). */
    fun remainingMs(
        fetchedAtMillis: Long?,
        nowMillis: Long,
    ): Long {
        if (fetchedAtMillis == null) return 0
        return (TTL_MS - (nowMillis - fetchedAtMillis)).coerceIn(0, TTL_MS)
    }
}