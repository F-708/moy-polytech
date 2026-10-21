package com.politex

import com.politex.data.network.TeacherSlot
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.TeacherRowMatch
import com.politex.data.repository.teacherRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherRowMatchTest {
    private val savitskayaS = Teacher(name = "Савицкая Светлана Игоревна")
    private val savitskayaN = Teacher(name = "Савицкая Наталья Петровна")
    private val ivanov = Teacher(name = "Иванов Иван Иванович")
    private val petrov = Teacher(name = "Петров Пётр Петрович")
    private val krivoshey = Teacher(name = "Кривошей Дмитрий Александрович")

    private val all = listOf(savitskayaS, savitskayaN, ivanov, petrov, krivoshey)

    private fun match(row: String, selected: Teacher) =
        TeacherInsights.matchRowToTeacher(row, selected, all)

    @Test
    fun `однофамильцы с инициалами — каждая видит только своё`() {
        assertEquals(TeacherRowMatch.YES, match("Савицкая С.И.", savitskayaS))
        assertEquals(TeacherRowMatch.NO, match("Савицкая С.И.", savitskayaN))
        assertEquals(TeacherRowMatch.YES, match("Савицкая Н.П.", savitskayaN))
        assertEquals(TeacherRowMatch.NO, match("Савицкая Н.П.", savitskayaS))
    }

    @Test
    fun `голая фамилия при коллизии — ambiguous обеим`() {
        assertEquals(TeacherRowMatch.AMBIGUOUS, match("Савицкая", savitskayaS))
        assertEquals(TeacherRowMatch.AMBIGUOUS, match("Савицкая", savitskayaN))
    }

    @Test
    fun `ячейка Иванов_Петров видна обоим`() {
        assertEquals(TeacherRowMatch.YES, match("Иванов/Петров", ivanov))
        assertEquals(TeacherRowMatch.YES, match("Иванов/Петров", petrov))
        assertEquals(TeacherRowMatch.NO, match("Иванов/Петров", savitskayaS))
    }

    @Test
    fun `строка с инициалами при одном носителе фамилии — видна, не дропается`() {
        assertEquals(TeacherRowMatch.YES, match("Кривошей Д.А.", krivoshey))
        assertEquals(TeacherRowMatch.YES, match("Кривошей", krivoshey))
    }

    @Test
    fun `чужая фамилия — NO`() {
        assertEquals(TeacherRowMatch.NO, match("Зыбин О.Л.", savitskayaS))
        assertEquals(TeacherRowMatch.NO, match("", ivanov))
    }

    @Test
    fun `teacherRows сплитит ячейку с двумя фамилиями на две строки`() {
        val parsed =
            mapOf(
                "Иванов И.И./Петров П.П." to
                    listOf(TeacherSlot(1, listOf("41О"), "Математика", "301", 1)),
            )
        val rows = teacherRows(parsed, "05.10.2026", requestedDow = 1)
        assertEquals(2, rows.size)
        assertEquals(setOf("иванов", "петров"), rows.map { it.surnameKey }.toSet())
        assertTrue(rows.all { it.dateString == "05.10.2026" && it.lessonNumber == 1 })
    }

    @Test
    fun `ё и е в фамилии и инициалах не расходятся`() {
        val eremenkoCsv = Teacher(name = "Еременко Татьяна Викторовна")
        val list = listOf(eremenkoCsv)
        assertEquals(
            TeacherRowMatch.YES,
            TeacherInsights.matchRowToTeacher("Ерёменко Т.В.", eremenkoCsv, list),
        )
        assertEquals(TeacherInsights.surnameOf("Еременко А.Б."), TeacherInsights.surnameOf("Ерёменко А.Б."))
        assertEquals(TeacherInsights.initialsOf("Ерёменко Т.В."), TeacherInsights.initialsOf("Еременко Т.В."))
    }
}
