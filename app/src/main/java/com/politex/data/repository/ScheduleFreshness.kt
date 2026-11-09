package com.politex.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Свежесть кэша расписания.
 *
 * Схему Room не трогаем: штампы «последний успешный фетч» по ключу `дата|группа` живут в
 * SharedPreferences (см. [PrefsFetchStamps]). TTL зависит от отношения даты к «сегодня»:
 *  - сегодня   — короткий (расписание правят в течение дня),
 *  - будущие   — длинный (публикуют заранее и меняют редко),
 *  - прошедшие — окончательны, обновлять не нужно.
 *
 * Свежесть — это про решение «идти ли в сеть», она не отменяет кэш-первый рендер: устаревший кэш
 * показывается сразу, а обновление идёт следом (AppViewModel).
 */

/** TTL кэша на сегодняшний день. */
internal const val FRESH_TODAY_MS = 15L * 60_000L

/** TTL кэша на будущие дни. */
internal const val FRESH_FUTURE_MS = 6L * 60L * 60_000L

/** Сколько живёт negative-метка «день разобран неполностью» (а не «навсегда»). */
internal const val INCOMPLETE_DAY_TTL_MS = 20L * 60_000L

/**
 * Доля каталога, при которой документ считается доказанно полным.
 * Ниже этой доли «нашей группы нет в документе» — не доказательство, а повод спросить сеть ещё раз.
 */
internal const val COMPLETE_DOCUMENT_PERCENT = 85

private val STAMP_DATE_REGEX = Regex("""(\d{2})\.(\d{2})\.(\d{4})""")

internal fun stampKey(
    date: String,
    groupName: String,
): String = "$date|$groupName"

/** Хранилище штампов последнего успешного фетча. Интерфейс — ради тестов без Android. */
interface FetchStamps {
    fun get(key: String): Long?

    fun put(
        key: String,
        atMillis: Long,
    )
}

class MemoryFetchStamps : FetchStamps {
    private val map = ConcurrentHashMap<String, Long>()

    override fun get(key: String): Long? = map[key]

    override fun put(
        key: String,
        atMillis: Long,
    ) {
        map[key] = atMillis
    }
}

class PrefsFetchStamps(
    private val prefs: SharedPreferences?,
) : FetchStamps {
    override fun get(key: String): Long? = prefs?.getLong(key, NO_STAMP)?.takeIf { it > 0L }

    override fun put(
        key: String,
        atMillis: Long,
    ) {
        prefs?.edit()?.putLong(key, atMillis)?.apply()
    }

    companion object {
        const val PREFS_NAME = "mpk_schedule_stamps"

        private const val NO_STAMP = -1L

        fun forContext(context: Context): PrefsFetchStamps = PrefsFetchStamps(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}

internal fun parseStampDate(date: String): Calendar? {
    val m = STAMP_DATE_REGEX.matchEntire(date.trim()) ?: return null
    val day = m.groupValues[1].toInt()
    val month = m.groupValues[2].toInt()
    val year = m.groupValues[3].toInt()
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, month - 1, day)
    return cal
}

/** Сравнение по календарным полям: миллисекунды не годятся, даты у нас без времени. */
internal fun compareDays(
    a: Calendar,
    b: Calendar,
): Int {
    val aYear = a.get(Calendar.YEAR)
    val bYear = b.get(Calendar.YEAR)
    if (aYear != bYear) return aYear.compareTo(bYear)
    return a.get(Calendar.DAY_OF_YEAR).compareTo(b.get(Calendar.DAY_OF_YEAR))
}

/**
 * TTL кэша для даты. `null` — «окончательно» (прошедший день): обновлять не нужно.
 */
internal fun dayCacheTtlMs(
    date: String,
    now: Calendar,
): Long? {
    val cal = parseStampDate(date) ?: return FRESH_TODAY_MS
    return when (compareDays(cal, now)) {
        0 -> FRESH_TODAY_MS
        -1 -> null
        else -> FRESH_FUTURE_MS
    }
}

/**
 * Устарел ли кэш даты.
 * `fetchedAt == null` — штампа нет (данные от старой версии/импорта): считаем устаревшим, чтобы
 * один раз после обновления приложения перезагрузить день.
 */
internal fun isStampedCacheStale(
    date: String,
    fetchedAt: Long?,
    nowMillis: Long,
    now: Calendar,
): Boolean {
    val ttl = dayCacheTtlMs(date, now) ?: return false
    if (fetchedAt == null) return true
    return nowMillis - fetchedAt >= ttl
}

internal fun isStampedCacheStale(
    date: String,
    fetchedAt: Long?,
    nowMillis: Long,
): Boolean = isStampedCacheStale(date, fetchedAt, nowMillis, Calendar.getInstance())