package com.politex

import com.politex.util.SubjectFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectFormatterDigitsTest {
    private fun assertNoDigits(result: String) {
        assertTrue("результат «$result» содержит цифры", result.none { it.isDigit() })
    }

    @Test
    fun `пустой raw — результат без цифр`() {
        assertNoDigits(SubjectFormatter.getShortName(""))
    }

    @Test
    fun `пробелы — пустой результат (без fallback'а на случайный ключ карты)`() {
        assertTrue(SubjectFormatter.getShortName("   ").isBlank())
    }

    @Test
    fun `цифровой raw — результат без цифр`() {
        assertNoDigits(SubjectFormatter.getShortName("123"))
        assertNoDigits(SubjectFormatter.getShortName("320"))
        assertNoDigits(SubjectFormatter.getShortName("7"))
    }

    @Test
    fun `цифровой raw — пустая строка для заглушки «Предмет уточняется»`() {
        assertTrue(SubjectFormatter.getShortName("123").isBlank())
        assertTrue(SubjectFormatter.getShortName("").isBlank())
    }

    @Test
    fun `обычные предметы guard не ломает`() {
        assertEquals("Математика", SubjectFormatter.getShortName("Математика"))
        assertEquals("Охр. труда", SubjectFormatter.getShortName("Охрана труда"))
        assertEquals("Физика", SubjectFormatter.getShortName("Физика"))
    }
}
