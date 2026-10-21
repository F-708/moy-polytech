package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.TaskType
import com.politex.data.network.PROBE_NET_FAIL
import com.politex.data.network.probeUrlOutcome
import com.politex.ui.screens.lessonsForDay
import com.politex.ui.screens.taskTypeBadgeColor
import com.politex.ui.theme.ColorBadgeCourse
import com.politex.ui.theme.ColorBadgeDiploma
import com.politex.ui.theme.ColorBadgeExam
import com.politex.ui.theme.ColorBadgeHW
import com.politex.ui.theme.ColorBadgeLab
import com.politex.ui.theme.ColorBadgePract
import com.politex.util.HomeworkMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class N1N4N18LogicTest {

    @Test
    fun `N1 HEAD 2xx — файл найден, GET-Range не нужен`() {
        val ok = probeUrlOutcome(headCode = 200, rangeGetCode = 0)
        assertTrue(ok.found)
        assertFalse(ok.networkError)
        assertEquals(200, ok.code)

        val partial = probeUrlOutcome(headCode = 206, rangeGetCode = 0)
        assertTrue(partial.found)
    }

    @Test
    fun `N1 HEAD не отвечает (405 или 404) — живой GET-Range спасает`() {
        val from405 = probeUrlOutcome(headCode = 405, rangeGetCode = 200)
        assertTrue("405 на HEAD, но GET живой → FOUND", from405.found)

        val from404 = probeUrlOutcome(headCode = 404, rangeGetCode = 206)
        assertTrue("404 на HEAD, но GET 206 → FOUND", from404.found)
    }

    @Test
    fun `N1 и HEAD и GET 404 — не опубликовано, запоминаем код`() {
        val r = probeUrlOutcome(headCode = 404, rangeGetCode = 404)
        assertFalse(r.found)
        assertFalse(r.networkError)
        assertEquals(404, r.code)
    }

    @Test
    fun `N1 HEAD 404 и GET не выполнялся (0) — not found по HEAD-коду`() {
        val r = probeUrlOutcome(headCode = 404, rangeGetCode = 0)
        assertFalse(r.found)
        assertFalse(r.networkError)
        assertEquals(404, r.code)
    }

    @Test
    fun `N1 IOException на HEAD — сетевая ошибка, не «не опубликовано»`() {
        val r = probeUrlOutcome(headCode = PROBE_NET_FAIL, rangeGetCode = 0)
        assertTrue(r.networkError)
        assertFalse(r.found)
    }

    @Test
    fun `N1 IOException на GET-Range (после не-2xx HEAD) — сетевая ошибка`() {
        val r = probeUrlOutcome(headCode = 500, rangeGetCode = PROBE_NET_FAIL)
        assertTrue(r.networkError)
        assertFalse(r.found)
    }

    @Test
    fun `V6a HEAD 200, но Content-Type text-html — НЕ документ (event-страница, soft-404)`() {
        val r = probeUrlOutcome(headCode = 200, rangeGetCode = 0, headIsHtml = true)
        assertFalse(r.found)
        assertFalse(r.networkError)
        assertEquals(200, r.code)
    }

    @Test
    fun `V6a HEAD 405, GET-Range 200 html — тоже НЕ документ`() {
        val r = probeUrlOutcome(headCode = 405, rangeGetCode = 200, rangeIsHtml = true)
        assertFalse(r.found)
        assertEquals(200, r.code)
    }

    @Test
    fun `V6a HEAD 405, GET-Range 200 не-html — документ найден`() {
        val r = probeUrlOutcome(headCode = 405, rangeGetCode = 200, rangeIsHtml = false)
        assertTrue(r.found)
    }

    @Test
    fun `N18 каждый тип задания отображается в свой цвет бейджа`() {
        assertEquals(ColorBadgeHW, taskTypeBadgeColor(TaskType.HOMEWORK))
        assertEquals(ColorBadgeLab, taskTypeBadgeColor(TaskType.LAB_WORK))
        assertEquals(ColorBadgePract, taskTypeBadgeColor(TaskType.PRACTICAL_WORK))
        assertEquals(ColorBadgeExam, taskTypeBadgeColor(TaskType.CONTROL_WORK))
        assertEquals(ColorBadgeCourse, taskTypeBadgeColor(TaskType.COURSE_WORK))
        assertEquals(ColorBadgeDiploma, taskTypeBadgeColor(TaskType.DIPLOMA))
    }

    @Test
    fun `N18 цвета бейджей различимы (мультицвет, а не один серый)`() {
        val colors = TaskType.entries.map { taskTypeBadgeColor(it) }
        assertEquals("все 6 типов дают разные цвета", 6, colors.distinct().size)
    }

    private fun lesson(
        num: Int,
        subject: String,
        date: String,
        dow: Int,
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = dow,
        lessonNumber = num,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = subject,
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
        dateString = date,
    )

    private fun task(
        subject: String,
        type: String,
        deadline: String,
    ) = StudentTaskEntity(
        groupName = "41О",
        course = 4,
        subjectName = subject,
        taskType = type,
        title = "t",
        deadlineDate = deadline,
    )

    @Test
    fun `N18 урок собирает все типы заданий дня (дедуплик)`() {
        val day = HomeworkMarkers.DayContext(dayIso = "2026-10-05", isToday = false, isPast = false)
        val lessons = listOf(lesson(1, "ТОЭ", "05.10.2026", 1))
        val tasks =
            listOf(
                task("ТОЭ", "Домашнее задание", "2026-10-05"),
                task("ТОЭ", "Лабораторная работа", "2026-10-05"),

                task("ТОЭ", "Домашнее задание", "2026-10-05"),

                task("Математика", "Домашнее задание", "2026-10-05"),
            )

        val byLesson =
            HomeworkMarkers.markerLessonTypes(
                dayLessons = lessons,
                day = day,
                nowMillis = 0L,
                weeklyLessonsByDow = emptyMap(),
                pendingTasks = tasks,
            )

        assertEquals(setOf(1), byLesson.keys)
        assertEquals(listOf(TaskType.HOMEWORK, TaskType.LAB_WORK), byLesson[1])
    }

    @Test
    fun `N18 прошедший день — ни номеров, ни типов`() {
        val past = HomeworkMarkers.DayContext(dayIso = "2026-10-01", isToday = false, isPast = true)
        val lessons = listOf(lesson(1, "ТОЭ", "01.10.2026", 4))
        val tasks = listOf(task("ТОЭ", "Домашнее задание", "2026-10-01"))
        assertTrue(
            HomeworkMarkers
                .markerLessonTypes(
                    dayLessons = lessons,
                    day = past,
                    nowMillis = 0L,
                    weeklyLessonsByDow = emptyMap(),
                    pendingTasks = tasks,
                ).isEmpty(),
        )
    }

    @Test
    fun `N4 точная дата приоритетнее шаблона дня недели`() {
        val rows =
            listOf(
                lesson(1, "ТОЭ", "05.10.2026", 1),
                lesson(2, "Химия", "", 1),
            )
        val exact = lessonsForDay(rows, activeDateString = "05.10.2026", activeDayOfWeek = 1)
        assertEquals(1, exact.size)
        assertEquals(1, exact[0].lessonNumber)
    }

    @Test
    fun `N4 нет точной даты — фолбэк на шаблон нужного дня недели`() {
        val rows =
            listOf(
                lesson(1, "ТОЭ", "", 1),
                lesson(2, "Химия", "", 2),
            )
        val tue = lessonsForDay(rows, activeDateString = "06.10.2026", activeDayOfWeek = 2)
        assertEquals(1, tue.size)
        assertEquals(2, tue[0].lessonNumber)
    }

    @Test
    fun `N4 пустой список — пусто`() {
        assertTrue(lessonsForDay(emptyList(), "05.10.2026", 1).isEmpty())
    }
}
