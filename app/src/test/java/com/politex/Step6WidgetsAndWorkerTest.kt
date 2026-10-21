package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.util.GroupParser
import com.politex.util.NotificationHelper
import com.politex.util.SubjectFormatter
import com.politex.worker.ScheduleCheckWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step6WidgetsAndWorkerTest {
    @Test
    fun testNextStudyDayTransition_1stCourse_Vs_HigherCourses() {
        val group1stCourse = GroupParser.parse("11Т")
        assertNotNull(group1stCourse)
        assertEquals(1, group1stCourse?.course)
        assertTrue(group1stCourse?.hasSaturdayClasses == true)

        val group4thCourse = GroupParser.parse("41О")
        assertNotNull(group4thCourse)
        assertEquals(4, group4thCourse?.course)
        assertFalse(group4thCourse?.hasSaturdayClasses == true)
    }

    @Test
    fun testSubjectAcronymAndShortNameForWidgets() {
        val rawToe = "Теоретические основы электротехники"
        assertEquals("ТОЭ", SubjectFormatter.getAcronym(rawToe))
        assertTrue(SubjectFormatter.getShortName(rawToe).length <= 25)

        val rawMath = "Математика"
        assertEquals("МАТ", SubjectFormatter.getAcronym(rawMath))
        assertEquals("Математика", SubjectFormatter.getShortName(rawMath))

        val rawPhys = "Физическая культура и здоровье"
        assertEquals("ФКиЗ", SubjectFormatter.getAcronym(rawPhys))
        assertEquals("Физкультура", SubjectFormatter.getShortName(rawPhys))
    }

    @Test
    fun testLessonSplitAndRoomDisplay() {
        val splitLesson =
            LessonEntity(
                groupName = "41О",
                dayOfWeek = 1,
                lessonNumber = 1,
                timeStart = "08:15",
                timeEnd = "09:55",
                subjectRaw = "Иностранный язык (профессиональная лексика)",
                roomFirst = "215",
                teacherFirst = "Иванова А.А.",
                roomSecond = "308",
                teacherSecond = "Петрова Б.Б.",
                isSplit = true,
            )

        assertTrue(splitLesson.isSplit)
        assertEquals("215", splitLesson.roomFirst)
        assertEquals("308", splitLesson.roomSecond)
    }

    @Test
    fun testBellTimesForDayOfWeek() {

        val (monStart, monEnd) = CollegeBellSchedule.getTimeForLessonNumber(1, 1)
        assertEquals("08:15", monStart)
        assertEquals("09:00", monEnd)

        val (thu7Start, thu7End) = CollegeBellSchedule.getTimeForLessonNumber(7, 4)
        assertEquals("14:45", thu7Start)
        assertEquals("15:30", thu7End)

        val (satStart, satEnd) = CollegeBellSchedule.getTimeForLessonNumber(1, 6)
        assertEquals("08:15", satStart)
        assertEquals("09:00", satEnd)
    }

    @Test
    fun testNotificationChannelConstants() {
        assertEquals("mpk_schedule_updates", NotificationHelper.CHANNEL_ID)
        assertEquals("Обновления расписания МГПК", NotificationHelper.CHANNEL_NAME)
        assertEquals(2026, NotificationHelper.NOTIFICATION_ID)
    }

    @Test
    fun testWorkManagerConstants() {
        assertEquals("mpk_schedule_check_worker", ScheduleCheckWorker.WORK_NAME)
    }

    private fun lesson(
        number: Int,
        date: String,
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = 1,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Математика",
        roomFirst = "215",
        teacherFirst = "Иванова А.А.",
        dateString = date,
    )

    @Test
    fun testFilterTodayLessons_prefersExactToday() {
        val all =
            listOf(
                lesson(1, "21.09.2026"),
                lesson(2, ""),
                lesson(3, "28.09.2026"),
            )
        val result =
            com.politex.widget.WidgetData
                .filterTodayLessons(all, "28.09.2026")
        assertEquals(listOf(3), result.map { it.lessonNumber })
    }

    @Test
    fun testFilterTodayLessons_fallsBackToWeeklyTemplate() {
        val all =
            listOf(
                lesson(2, "21.09.2026"),
                lesson(1, ""),
            )
        val result =
            com.politex.widget.WidgetData
                .filterTodayLessons(all, "28.09.2026")
        assertEquals(listOf(1), result.map { it.lessonNumber })
    }

    @Test
    fun testFilterTodayLessons_onlyPastDate_noTemplate_returnsEmpty() {
        val all = listOf(lesson(1, "21.09.2026"), lesson(2, "22.09.2026"))
        val result =
            com.politex.widget.WidgetData
                .filterTodayLessons(all, "28.09.2026")
        assertTrue(result.isEmpty())
    }
}
