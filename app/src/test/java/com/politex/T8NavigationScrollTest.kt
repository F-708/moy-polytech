package com.politex

import com.politex.ui.screens.autoScrollLessonIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T8NavigationScrollTest {
    @Test
    fun `идёт урок 3 — скроллим к нему`() {
        assertEquals(2, autoScrollLessonIndex(listOf(1, 2, 3, 4), 3, true))
    }

    @Test
    fun `уроки кончились — к последнему`() {
        assertEquals(3, autoScrollLessonIndex(listOf(1, 2, 3, 4), 5, true))
    }

    @Test
    fun `null (перемена или до уроков) — остаёмся наверху`() {
        assertEquals(0, autoScrollLessonIndex(listOf(1, 2, 3, 4), null, true))
    }

    @Test
    fun `не сегодня — не скроллим`() {
        assertNull(autoScrollLessonIndex(listOf(1, 2, 3), 2, false))
    }

    @Test
    fun `пустой день — не скроллим`() {
        assertNull(autoScrollLessonIndex(emptyList(), 2, true))
    }

    @Test
    fun `номер урока вне списка но меньше максимума — наверх`() {
        assertEquals(0, autoScrollLessonIndex(listOf(1, 3, 4), 2, true))
    }
}
