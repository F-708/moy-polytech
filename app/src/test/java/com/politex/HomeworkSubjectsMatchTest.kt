package com.politex

import com.politex.util.HomeworkMarkers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkSubjectsMatchTest {
    private fun match(lesson: String, task: String) = HomeworkMarkers.subjectsMatch(lesson, task)

    @Test
    fun `точный предмет совпадает`() {
        assertTrue(match("Математика", "Математика"))
        assertTrue(match("Физика", "физика"))
    }

    @Test
    fun `split-склейка матчит обе подгруппы`() {
        assertTrue(match("Математика / Информатика", "Математика"))
        assertTrue(match("Математика / Информатика", "Информатика"))
        assertTrue(match("Физкультура / Математика", "Физкультура"))
    }

    @Test
    fun `split с КОРОТКИМ предметом больше не теряется (корень бага)`() {
        assertTrue(match("ТОЭ / Электронные приборы", "ТОЭ"))
        assertTrue(match("Химия / Биология", "Химия"))
        assertTrue(match("Химия / Биология", "Биология"))
    }

    @Test
    fun `длинный предмет через contains`() {
        assertTrue(match("История Беларуси в контексте всемирной истории / Обществоведение", "История"))
        assertTrue(match("Иностранный язык (профессиональная лексика) / Физическая культура и здоровье", "Физическая культура и здоровье"))
    }

    @Test
    fun `аббревиатура расписания ДиагнТО матчит ДЗ по диагностике`() {
        val full = "Диагностика, техническое обслуживание и ремонт механических транспортных средств"
        assertTrue(match("ДиагнТО", "Диагностика"))
        assertTrue(match("ДиагнТО / ДиагнТО", "Диагностика"))
        assertTrue(match("ДиагнТО", full))
        assertTrue(match("ДиагнТОиР", full))
    }

    @Test
    fun `несовпадение остаётся несовпадением`() {
        assertFalse(match("Математика", "Информатика"))
        assertFalse(match("ТОЭ / Электронные приборы", "Химия"))
        assertFalse(match("", "Математика"))
        assertFalse(match("Математика", ""))
    }
}
