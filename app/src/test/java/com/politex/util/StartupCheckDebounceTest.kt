package com.politex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCheckDebounceTest {
    private val minute = 60_000L
    private val now = 1_800_000_000_000L

    @Test
    fun `дебаунс по умолчанию пять минут`() {
        assertEquals(5L, STARTUP_CHECK_DEBOUNCE_MINUTES)
    }

    @Test
    fun `первый запуск требует проверки`() {
        assertTrue(startupCheckNeeded(0L, now))
        assertTrue(startupCheckNeeded(-1L, now))
    }

    @Test
    fun `запуск подряд не требует новой проверки`() {
        val last = now - 30_000L
        assertTrue(startupCheckNeeded(0L, last))
        assertFalse(startupCheckNeeded(last, now))
    }

    @Test
    fun `проверка сразу после отметки не нужна`() {
        assertFalse(startupCheckNeeded(now, now))
    }

    @Test
    fun `меньше пяти минут недостаточно для новой проверки`() {
        assertFalse(startupCheckNeeded(now - 4 * minute, now))
        assertFalse(startupCheckNeeded(now - (5 * minute - 1L), now))
    }

    @Test
    fun `пять минут и больше запускают новую проверку`() {
        assertTrue(startupCheckNeeded(now - 5 * minute, now))
        assertTrue(startupCheckNeeded(now - 6 * minute, now))
        assertTrue(startupCheckNeeded(now - 12 * minute, now))
    }

    @Test
    fun `откат часов назад не блокирует проверку`() {
        assertTrue(startupCheckNeeded(now + minute, now))
    }

    @Test
    fun `собственный порог дебаунса соблюдается`() {
        assertFalse(startupCheckNeeded(now - 9 * minute, now, debounceMinutes = 10L))
        assertTrue(startupCheckNeeded(now - 11 * minute, now, debounceMinutes = 10L))
    }
}
