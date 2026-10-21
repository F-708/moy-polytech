package com.politex.widget

import com.politex.data.model.CollegeBellSchedule
import com.politex.worker.ScheduleCheckWorker
import com.politex.worker.TeacherDayDecision
import com.politex.worker.scheduleDayTitle
import com.politex.worker.teacherDayDecision
import com.politex.worker.teacherMarkKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherModeWidgetLogicTest {
    private fun lesson(
        n: Int,
        groups: List<String> = listOf("11А"),
        room: String = "204",
        subject: String = "Математика",
    ): TeacherWidgetLesson {
        val bell = CollegeBellSchedule.getBellsForDay(2).first { it.lessonNumber == n }
        return TeacherWidgetLesson(
            lessonNumber = n,
            subject = subject,
            groups = groups,
            room = room,
            start = bell.start,
            end = bell.end,
            startMinutes = bell.startMinutes,
            endMinutes = bell.endMinutes,
        )
    }

    @Test
    fun `toEntity переносит группы в groupName и время из звонков`() {
        val entity = lesson(3, groups = listOf("11А", "21Б")).toEntity("20.10.2026", 2)
        assertEquals("11А, 21Б", entity.groupName)
        assertEquals(3, entity.lessonNumber)
        assertEquals("10:20", entity.timeStart)
        assertEquals("11:05", entity.timeEnd)
        assertEquals("204", entity.roomFirst)
        assertEquals("", entity.teacherFirst)
        assertFalse(entity.isSplit)
        assertEquals("20.10.2026", entity.dateString)
        assertEquals(2, entity.dayOfWeek)
    }

    @Test
    fun `toEntities маппит весь снимок с датой и днём недели`() {
        val snap = TeacherWidgetSnapshot(
            dateString = "20.10.2026",
            dow = 2,
            dayOfMonth = 20,
            month1 = 10,
            lessons = listOf(lesson(2), lesson(3)),
            next = null,
            lastSyncEpoch = 0L,
        )
        val entities = snap.toEntities()
        assertEquals(listOf(2, 3), entities.map { it.lessonNumber })
        assertTrue(entities.all { it.dateString == "20.10.2026" && it.dayOfWeek == 2 })
    }

    @Test
    fun `surnameLabel даёт фамилию с заглавной`() {
        assertEquals("Тестова", TeacherWidgetLogic.surnameLabel("Тестова Анна Петровна"))
        assertEquals("Еременко", TeacherWidgetLogic.surnameLabel("еременко ольга васильевна"))
    }

    @Test
    fun `widgetHeader в режиме преподавателя показывает фамилию вместо группы`() {
        assertEquals("СЕГОДНЯ • Тестова", TeacherWidgetLogic.widgetHeader("", "Тестова Анна Петровна"))
        assertEquals("СЕГОДНЯ • Тестова", TeacherWidgetLogic.widgetHeader("41О", "Тестова Анна Петровна"))
    }

    @Test
    fun `widgetHeader в студенческом режиме не меняется`() {
        assertEquals("СЕГОДНЯ • 41О", TeacherWidgetLogic.widgetHeader("41О", null))
        assertEquals("РАСПИСАНИЕ", TeacherWidgetLogic.widgetHeader("", null))
    }

    @Test
    fun `lessonTitleText добавляет группы только в режиме преподавателя`() {
        assertEquals("Математика • 11А, 21Б", TeacherWidgetLogic.lessonTitleText("Математика", "11А, 21Б", true))
        assertEquals("Математика", TeacherWidgetLogic.lessonTitleText("Математика", "11А", false))
        assertEquals("Математика", TeacherWidgetLogic.lessonTitleText("Математика", "", true))
    }

    @Test
    fun `nowNextEmptyText добавляет строку следующей пары`() {
        val next = NextLessonRef("21.10.2026", 3, lesson(1))
        val empty = TeacherWidgetLogic.nowNextEmptyText(lessonsEmpty = true, next = next)
        assertTrue(empty.startsWith("Сегодня уроков нет"))
        assertTrue(empty.contains("Следующая — ср"))

        val done = TeacherWidgetLogic.nowNextEmptyText(lessonsEmpty = false, next = next)
        assertTrue(done.startsWith("Уроки закончились"))
        assertTrue(done.contains("Следующая — ср"))
    }

    @Test
    fun `nowNextEmptyText без следующей пары остаётся однострочным`() {
        assertEquals("Сегодня уроков нет", TeacherWidgetLogic.nowNextEmptyText(true, null))
        assertEquals("Уроки закончились", TeacherWidgetLogic.nowNextEmptyText(false, null))
    }

    @Test
    fun `teacherDayDecision уведомляет только при уроках преподавателя`() {
        assertEquals(TeacherDayDecision.NOTIFY, teacherDayDecision(documentPresent = true, teacherRows = 3))
        assertEquals(TeacherDayDecision.MARK_CHECKED, teacherDayDecision(documentPresent = true, teacherRows = 0))
        assertEquals(TeacherDayDecision.RETRY_LATER, teacherDayDecision(documentPresent = false, teacherRows = 0))
    }

    @Test
    fun `teacherMarkKey совместим с очисткой устаревших меток`() {
        val key = teacherMarkKey("notified", "тестова", "21.10.2026")
        assertEquals("notified_T_тестова_21.10.2026", key)

        val now = 1_700_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ROOT)
        val oldDate = fmt.format(java.util.Date(now - 10 * day))
        val freshDate = fmt.format(java.util.Date(now - 2 * day))
        val stale = ScheduleCheckWorker.staleMarkKeys(
            setOf(
                teacherMarkKey("notified", "тестова", oldDate),
                teacherMarkKey("checked", "тестова", freshDate),
            ),
            now,
        )
        assertEquals(listOf(teacherMarkKey("notified", "тестова", oldDate)), stale)
    }

    @Test
    fun `scheduleDayTitle совпадает с прежней формулировкой`() {
        assertEquals("понедельник", scheduleDayTitle(1))
        assertEquals("среду", scheduleDayTitle(3))
        assertEquals("субботу", scheduleDayTitle(6))
        assertEquals("следующий учебный день", scheduleDayTitle(7))
    }
}
