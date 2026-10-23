package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.ui.screens.groupScheduleDayOfWeek
import com.politex.ui.screens.groupScheduleDbDate
import com.politex.ui.screens.groupScheduleIsSchoolDay
import com.politex.ui.screens.groupScheduleNextSchoolDay
import com.politex.ui.screens.needsFetch
import com.politex.ui.screens.pickForeignGroupLessons
import com.politex.util.GroupParser
import com.politex.util.MpkCurriculum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GroupScheduleLogicTest {
    private fun lesson(
        date: String,
        dow: Int,
        num: Int,
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = dow,
        lessonNumber = num,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Тест",
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
        dateString = date,
    )

    @Test
    fun `день недели из Calendar пн-вс`() {
        assertEquals(1, groupScheduleDayOfWeek(Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 21) }))
        assertEquals(5, groupScheduleDayOfWeek(Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 25) }))
        assertEquals(6, groupScheduleDayOfWeek(Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 26) }))
        assertEquals(7, groupScheduleDayOfWeek(Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 27) }))
    }

    @Test
    fun `учебный день - пн-пт, суббота только для 1 курса`() {
        assertTrue(groupScheduleIsSchoolDay(1, saturdayIsSchoolDay = false))
        assertFalse(groupScheduleIsSchoolDay(6, saturdayIsSchoolDay = false))
        assertTrue(groupScheduleIsSchoolDay(6, saturdayIsSchoolDay = true))
        assertFalse(groupScheduleIsSchoolDay(7, saturdayIsSchoolDay = true))
    }

    @Test
    fun `следующий учебный день пропускает воскресенье`() {
        val monday = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 21) }
        val fromSunday = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 27) }
        assertEquals(2, groupScheduleDayOfWeek(groupScheduleNextSchoolDay(monday, false)))
        val next = groupScheduleNextSchoolDay(fromSunday, false)
        assertEquals(1, groupScheduleDayOfWeek(next))
        assertEquals(28, next.get(Calendar.DAY_OF_MONTH))

        assertEquals(27, fromSunday.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `дата БД формата dd_MM_yyyy`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 3) }
        assertEquals("03.09.2026", groupScheduleDbDate(cal))
    }

    @Test
    fun `точная дата приоритетнее шаблона дня недели`() {
        val rows =
            listOf(
                lesson("22.09.2026", 2, 1),
                lesson("", 2, 2),
            )

        assertEquals(1, pickForeignGroupLessons(rows, "22.09.2026").size)
        assertEquals(1, pickForeignGroupLessons(rows, "22.09.2026")[0].lessonNumber)

        assertEquals(1, pickForeignGroupLessons(rows, "23.09.2026").size)
        assertEquals(2, pickForeignGroupLessons(rows, "23.09.2026")[0].lessonNumber)

        assertTrue(pickForeignGroupLessons(emptyList(), "22.09.2026").isEmpty())
    }

    @Test
    fun `группы выбора берутся из учебного плана а не из базы учащихся`() {
        val groups = GroupParser.generateCommonGroups()
        assertTrue(groups.isNotEmpty())

        groups.forEach { g ->
            assertNotNull(MpkCurriculum.getSpecialty(g.specialtyCode))
            assertTrue(g.course in 1..4)
            assertEquals(g.canonicalName, GroupParser.parse(g.canonicalName)?.canonicalName)
        }

        assertTrue(groups.map { it.course }.distinct().size >= 4)
        assertTrue(groups.map { it.specialtyCode }.distinct().size >= 5)
    }

    @Test
    fun `needsFetch - сеть только при пустом кэше или явном действии пользователя`() {
        assertFalse(needsFetch(hasCachedLessons = true, userForced = false))
        assertTrue(needsFetch(hasCachedLessons = true, userForced = true))
        assertTrue(needsFetch(hasCachedLessons = false, userForced = false))
        assertTrue(needsFetch(hasCachedLessons = false, userForced = true))
    }
}
