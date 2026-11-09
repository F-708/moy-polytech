package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.TaskType
import com.politex.util.HomeworkMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class HomeworkMarkersWindowTest {
    private val monday: Calendar by lazy {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(2026, Calendar.NOVEMBER, 2, 0, 0, 0)
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal
    }

    private fun calAt(
        dayOffset: Int,
        hour: Int,
    ): Calendar =
        (monday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
        }

    private fun millis(
        dayOffset: Int,
        hour: Int,
        minute: Int = 0,
    ): Long =
        calAt(dayOffset, hour)
            .apply { set(Calendar.MINUTE, minute) }
            .timeInMillis

    private fun iso(dayOffset: Int): String = HomeworkMarkers.dayIsoOf(calAt(dayOffset, 8))

    private fun dot(dayOffset: Int): String {
        val cal = calAt(dayOffset, 0)
        return String.format(
            Locale.ROOT,
            "%02d.%02d.%04d",
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR),
        )
    }

    private fun day(
        dayOffset: Int,
        isPast: Boolean = dayOffset < 0,
    ) = HomeworkMarkers.DayContext(iso(dayOffset), isToday = dayOffset == 0, isPast = isPast)

    private fun lesson(
        number: Int,
        dow: Int,
        subject: String,
        timeEnd: String = "09:55",
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = dow,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = timeEnd,
        subjectRaw = subject,
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
    )

    private val mondayLessons = listOf(lesson(2, 1, "Математика"))
    private val wednesdayLessons = listOf(lesson(3, 3, "Математика"))

    private val weekly: Map<Int, List<LessonEntity>> =
        mapOf(
            1 to mondayLessons,
            3 to wednesdayLessons,
        )

    private fun task(
        deadline: String = "",
        createdAt: Long = millis(0, 8),
        subject: String = "Математика",
        type: TaskType = TaskType.HOMEWORK,
        isCompleted: Boolean = false,
    ) = StudentTaskEntity(
        groupName = "41О",
        course = 4,
        subjectName = subject,
        taskType = type.displayName,
        title = "t",
        isCompleted = isCompleted,
        deadlineDate = deadline,
        createdAt = createdAt,
    )

    @Test
    fun `без дедлайна бейдж горит на обоих вхождениях предмета`() {
        val t = task()
        val now = millis(0, 12)
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(0), weekly, now))
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, now))
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(7), weekly, now))
    }

    @Test
    fun `markerLessonTypes ставит бейдж и в понедельник, и в среду`() {
        val t = task()
        val now = millis(0, 12)
        val mon = HomeworkMarkers.markerLessonTypes(mondayLessons, day(0), now, weekly, listOf(t))
        val wed = HomeworkMarkers.markerLessonTypes(wednesdayLessons, day(2), now, weekly, listOf(t))
        assertEquals(listOf(TaskType.HOMEWORK), mon[2])
        assertEquals(listOf(TaskType.HOMEWORK), wed[3])
    }

    @Test
    fun `создано после пары — сегодня не горит, следующее вхождение горит`() {
        val t = task(createdAt = millis(0, 12))
        val now = millis(0, 12)
        assertEquals(iso(2), HomeworkMarkers.firstActiveDayIso(t.createdAt, t.subjectName, weekly))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(0), weekly, now))
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, now))
    }

    @Test
    fun `создано в 23 50 — к сегодняшнему уроку не липнет`() {
        val t = task(createdAt = millis(0, 23, 50))
        val now = millis(0, 23, 55)
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(0), weekly, now))
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, now))
    }

    @Test
    fun `выполненное задание не горит нигде`() {
        val t = task(isCompleted = true)
        val now = millis(0, 12)
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(0), weekly, now))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, now))
        assertTrue(HomeworkMarkers.markerLessonTypes(mondayLessons, day(0), now, weekly, listOf(t)).isEmpty())
    }

    @Test
    fun `пустое расписание — бейджей нет`() {
        val t = task(deadline = iso(2))
        val now = millis(0, 12)
        assertNull(HomeworkMarkers.firstActiveDayIso(t.createdAt, t.subjectName, emptyMap()))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(0), emptyMap(), now))
        assertTrue(HomeworkMarkers.markerLessonTypes(mondayLessons, day(0), now, emptyMap(), listOf(t)).isEmpty())
    }

    @Test
    fun `прошедшие дни гасятся календарно, а не по флагу`() {
        val t = task()
        val now = millis(2, 12)
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, now))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, HomeworkMarkers.DayContext(iso(1), false, false), weekly, now))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(1), weekly, now))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(-2), weekly, now))
    }

    @Test
    fun `прошедший день на уровне маркеров — пусто`() {
        val t = task()
        val res =
            HomeworkMarkers.markerLessonTypes(
                wednesdayLessons,
                HomeworkMarkers.DayContext(iso(0), isToday = false, isPast = true),
                millis(2, 12),
                weekly,
                listOf(t),
            )
        assertTrue(res.isEmpty())
    }

    @Test
    fun `дедлайн в будущем закрывает окно после срока`() {
        val t = task(deadline = iso(2))
        val now = millis(0, 12)
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(0), weekly, now))
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, now))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(7), weekly, now))
    }

    @Test
    fun `просроченный дедлайн — невыполненное продолжает гореть`() {
        val t = task(deadline = iso(0))
        val overdueNow = millis(2, 12)
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, overdueNow))
        assertEquals(listOf(TaskType.HOMEWORK), HomeworkMarkers.markerLessonTypes(wednesdayLessons, day(2), overdueNow, weekly, listOf(t))[3])
    }

    @Test
    fun `просроченное но выполненное — не горит`() {
        val t = task(deadline = iso(0), isCompleted = true)
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, millis(2, 12)))
    }

    @Test
    fun `дедлайн в формате дд точка мм точка гггг равноправен ISO`() {
        val isoTask = task(deadline = iso(2))
        val dotTask = task(deadline = dot(2))
        val now = millis(0, 12)
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(dotTask, day(2), weekly, now))
        assertEquals(
            HomeworkMarkers.isHomeworkActiveOn(isoTask, day(7), weekly, now),
            HomeworkMarkers.isHomeworkActiveOn(dotTask, day(7), weekly, now),
        )
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(dotTask, day(7), weekly, now))
    }

    @Test
    fun `день раньше окна активности не горит`() {
        val t = task(createdAt = millis(2, 8))
        val now = millis(2, 12)
        assertEquals(iso(2), HomeworkMarkers.firstActiveDayIso(t.createdAt, t.subjectName, weekly))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(0), weekly, now))
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, now))
    }

    @Test
    fun `два задания разных типов — два бейджа, дубли схлопываются`() {
        val now = millis(0, 12)
        val twoTypes = listOf(task(type = TaskType.HOMEWORK), task(type = TaskType.LAB_WORK))
        assertEquals(
            listOf(TaskType.HOMEWORK, TaskType.LAB_WORK),
            HomeworkMarkers.markerLessonTypes(mondayLessons, day(0), now, weekly, twoTypes)[2],
        )
        val duplicates = listOf(task(), task())
        assertEquals(
            listOf(TaskType.HOMEWORK),
            HomeworkMarkers.markerLessonTypes(mondayLessons, day(0), now, weekly, duplicates)[2],
        )
    }

    @Test
    fun `битое время пары не отменяет окно`() {
        val broken = mapOf(1 to listOf(lesson(2, 1, "Математика", timeEnd = "—")))
        val t = task()
        val now = millis(0, 12)
        assertNotNull(HomeworkMarkers.firstActiveDayIso(t.createdAt, t.subjectName, broken))
        assertEquals(iso(0), HomeworkMarkers.firstActiveDayIso(t.createdAt, t.subjectName, broken))
        assertEquals(
            listOf(TaskType.HOMEWORK),
            HomeworkMarkers.markerLessonTypes(broken.getValue(1), day(0), now, broken, listOf(t))[2],
        )
    }

    @Test
    fun `после окончания пары бейдж остаётся`() {
        val t = task()
        val evening = millis(0, 18)
        assertEquals(
            listOf(TaskType.HOMEWORK),
            HomeworkMarkers.markerLessonTypes(mondayLessons, day(0), evening, weekly, listOf(t))[2],
        )
    }

    @Test
    fun `бейдж только у уроков своего предмета`() {
        val t = task()
        val mixed = listOf(lesson(2, 1, "Математика"), lesson(4, 1, "Информатика"))
        val res = HomeworkMarkers.markerLessonTypes(mixed, day(0), millis(0, 12), weekly, listOf(t))
        assertEquals(setOf(2), res.keys)
    }

    @Test
    fun `предмета нет в расписании — бейджа нет ни в один день`() {
        val t = task(subject = "Проектная деятельность", deadline = iso(0))
        val now = millis(0, 12)
        assertNull(HomeworkMarkers.firstActiveDayIso(t.createdAt, t.subjectName, weekly))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(0), weekly, now))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(t, day(2), weekly, now))
    }

    @Test
    fun `кодек дат принимает и дд мм гггг и ISO, битый дедлайн не скрывает ДЗ`() {
        val now = millis(0, 12)
        val isoTask = task(deadline = iso(2))
        val dotTask = task(deadline = dot(2))
        val brokenDeadline = task(deadline = "не дата")
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(isoTask, day(2), weekly, now))
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(dotTask, day(2), weekly, now))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(isoTask, day(7), weekly, now))
        assertFalse(HomeworkMarkers.isHomeworkActiveOn(dotTask, day(7), weekly, now))
        assertTrue(HomeworkMarkers.isHomeworkActiveOn(brokenDeadline, day(7), weekly, now))
        assertFalse(HomeworkMarkers.isPastDay(iso(0), 0L))
        assertTrue(HomeworkMarkers.isPastDay(iso(0), millis(2, 12)))
    }
}