package com.politex

import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.repository.LessonStatus
import com.politex.data.repository.NextLesson
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLogic
import com.politex.data.repository.matchRowsForTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherDayLogicTest {
    private fun lesson(
        n: Int,
        startMin: Int,
        endMin: Int,
        room: String = "241",
    ) = TeacherDayLesson(n, "Предмет", listOf("41О"), room, "%02d:%02d".format(startMin / 60, startMin % 60), "%02d:%02d".format(endMin / 60, endMin % 60), startMin, endMin)

    @Test
    fun `статусы сегодня — прошла, идёт, следующая, предстоящая`() {
        val lessons =
            listOf(
                lesson(1, 8 * 60 + 15, 9 * 60),
                lesson(2, 9 * 60 + 10, 9 * 60 + 55),
                lesson(3, 10 * 60 + 20, 11 * 60 + 5),
                lesson(4, 11 * 60 + 15, 12 * 60),
                lesson(5, 12 * 60 + 25, 13 * 60 + 10),
            )
        val now = 10 * 60 + 30
        assertEquals(
            listOf(LessonStatus.PAST, LessonStatus.PAST, LessonStatus.NOW, LessonStatus.NEXT, LessonStatus.UPCOMING),
            TeacherDayLogic.statuses(lessons, now, isToday = true),
        )
    }

    @Test
    fun `между парами — первая будущая становится СЛЕДУЮЩЕЙ`() {
        val lessons = listOf(lesson(1, 8 * 60 + 15, 9 * 60), lesson(2, 9 * 60 + 10, 9 * 60 + 55))
        assertEquals(
            listOf(LessonStatus.PAST, LessonStatus.NEXT),
            TeacherDayLogic.statuses(lessons, 9 * 60 + 5, isToday = true),
        )
    }

    @Test
    fun `завтра — статусы не назначаются`() {
        val lessons = listOf(lesson(1, 8 * 60 + 15, 9 * 60), lesson(2, 9 * 60 + 10, 9 * 60 + 55))
        assertEquals(
            listOf(LessonStatus.UPCOMING, LessonStatus.UPCOMING),
            TeacherDayLogic.statuses(lessons, 10 * 60, isToday = false),
        )
    }

    @Test
    fun `пара без известного времени не занимает СЛЕДУЮЩУЮ`() {
        val noTime = TeacherDayLesson(9, "X", emptyList(), "", "", "", -1, -1)
        val lessons = listOf(noTime, lesson(1, 9 * 60 + 10, 9 * 60 + 55))
        assertEquals(
            listOf(LessonStatus.UPCOMING, LessonStatus.NEXT),
            TeacherDayLogic.statuses(lessons, 8 * 60, isToday = true),
        )
    }

    @Test
    fun `плейсхолдер пустого дня`() {
        assertEquals("Выходной", TeacherDayLogic.emptyDayText(7, null))
        assertEquals("Сегодня пар нет.", TeacherDayLogic.emptyDayText(3, null))
        assertEquals("На этот день пар нет.", TeacherDayLogic.emptyDayText(3, null, isToday = false))
        val next = NextLesson("06.10.2026", 2, lesson(1, 9 * 60 + 50, 10 * 60, room = "241"))
        assertEquals("Сегодня пар нет. Следующая — вт 09:50, каб. 241", TeacherDayLogic.emptyDayText(1, next))
        val noRoom = NextLesson("06.10.2026", 2, lesson(1, 9 * 60 + 50, 10 * 60, room = ""))
        assertEquals("Сегодня пар нет. Следующая — вт 09:50", TeacherDayLogic.emptyDayText(1, noRoom))
    }

    @Test
    fun `короткие дни недели`() {
        assertEquals("пн", TeacherDayLogic.shortDow(1))
        assertEquals("ср", TeacherDayLogic.shortDow(3))
        assertEquals("сб", TeacherDayLogic.shortDow(6))
        assertEquals("вс", TeacherDayLogic.shortDow(7))
    }

    private fun row(
        teacherName: String,
        dow: Int = 1,
        n: Int = 1,
    ) = TeacherLessonEntity(
        id = 0,
        teacherName = teacherName,
        surnameKey = com.politex.data.repository.TeacherInsights.surnameOf(teacherName),
        dateString = "05.10.2026",
        dayOfWeek = dow,
        lessonNumber = n,
        groups = "41О",
        subject = "Математика",
        room = "241",
    )

    @Test
    fun `однофамильцы — инициалы фильтруют, голая фамилия помечает ambiguous`() {
        val savickayaS = Teacher("Савицкая Светлана Игоревна")
        val all = listOf(savickayaS, Teacher("Савицкая Наталья Петровна"))
        val rows = listOf(row("Савицкая С.И."), row("Савицкая Н.П."), row("Савицкая"), row("Иванов И.И."))
        val (matched, ambiguous) = matchRowsForTeacher(rows, savickayaS, all, dow = 1)
        assertTrue(ambiguous)
        assertEquals(listOf("Савицкая С.И.", "Савицкая"), matched.map { it.teacherName })
    }

    @Test
    fun `без однофамильцев — строка с инициалами точная, ambiguous нет`() {
        val ivanov = Teacher("Иванов Пётр Петрович")
        val all = listOf(ivanov)
        val (matched, ambiguous) = matchRowsForTeacher(listOf(row("Иванов П.П."), row("Петров А.А.")), ivanov, all, dow = 1)
        assertFalse(ambiguous)
        assertEquals(listOf("Иванов П.П."), matched.map { it.teacherName })
    }

    @Test
    fun `фильтр дня недели — чужой dow отсекается, dow 0 остаётся`() {
        val t = Teacher("Иванов Пётр Петрович")
        val rows = listOf(row("Иванов П.П.", dow = 1, n = 1), row("Иванов П.П.", dow = 2, n = 2), row("Иванов П.П.", dow = 0, n = 3))
        val (matched, _) = matchRowsForTeacher(rows, t, listOf(t), dow = 1)
        assertEquals(listOf(1, 3), matched.map { it.lessonNumber })
    }

    @Test
    fun `subtitle слота — только dd_MM без дня недели`() {
        val base = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.OCTOBER, 13)
        }
        assertEquals("13.10", com.politex.ui.teacher.slotSubtitle(0, base))
        assertEquals("14.10", com.politex.ui.teacher.slotSubtitle(1, base))

        listOf(0, 1).forEach { off ->
            val s = com.politex.ui.teacher.slotSubtitle(off, base)
            assertTrue("«$s» не должно содержать запятую", !s.contains(","))
            assertTrue("«$s» = только dd.MM", s.matches(Regex("""\d{2}\.\d{2}""")))
        }
    }

    @Test
    fun `teacherSlots — слоты 1_max с дырками как студенческий lessonSlots`() {
        val slots = com.politex.ui.teacher.teacherSlots(listOf(lesson(1, 0, 0), lesson(3, 0, 0)))
        assertEquals(listOf(1, 2, 3), slots.map { it.first })

        assertTrue(slots[1].second == null)
        assertTrue(slots[0].second != null && slots[2].second != null)
    }
}
