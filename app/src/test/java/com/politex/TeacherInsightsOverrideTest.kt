package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherInsightsOverrideTest {
    private val now = 1_800_000_000_000L
    private val name = "Зыбин Олег Львович"
    private val base = listOf(Teacher(name = name))

    private fun lesson(date: String) = LessonEntity(
        groupName = "41О",
        dayOfWeek = 2,
        lessonNumber = 1,
        timeStart = "08:15",
        timeEnd = "09:00",
        subjectRaw = "",
        roomFirst = "249",
        teacherFirst = "Зыбин О.Л.",
        roomSecond = "",
        teacherSecond = "",
        dateString = date,
    )

    @Test
    fun `без override прежнее поведение W28 — свежий урок ведёт`() {
        val ti = TeacherInsights(base, listOf(lesson("10.01.2027")), nowMillis = now)
        assertTrue(ti.isActive(name))
        assertEquals("без отметок isLeadsNow == isActive", ti.isActive(name), ti.isLeadsNow(name))
    }

    @Test
    fun `без override прежнее поведение W28 — старый урок не ведёт`() {
        val ti = TeacherInsights(base, listOf(lesson("01.09.2026")), nowMillis = now)
        assertFalse(ti.isActive(name))
        assertFalse(ti.isLeadsNow(name))
    }

    @Test
    fun `снятая отметка гасит шильдик пока урок старше снятия`() {
        val ti =
            TeacherInsights(
                base,
                listOf(lesson("10.01.2027")),
                nowMillis = now,
                leadsOffTs = { now },
            )
        assertTrue("авто-факт не меняется", ti.isActive(name))
        assertFalse("отметка снята, нового урока нет — скрыт", ti.isLeadsNow(name))
    }

    @Test
    fun `новый урок после снятия возвращает шильдик`() {
        val last =
            TeacherInsights(base, listOf(lesson("10.01.2027")), nowMillis = now)
                .lastLessonMillis(name)
        assertNotNull(last)
        val ti =
            TeacherInsights(
                base,
                listOf(lesson("10.01.2027")),
                nowMillis = now,
                leadsOffTs = { last!! - 1 },
            )
        assertTrue("урок датирован позже снятия — авто-факт вернул шильдик", ti.isLeadsNow(name))
    }

    @Test
    fun `граница равна timestamp снятия — шильдик скрыт`() {
        val last =
            TeacherInsights(base, listOf(lesson("10.01.2027")), nowMillis = now)
                .lastLessonMillis(name)!!
        val ti = TeacherInsights(base, listOf(lesson("10.01.2027")), nowMillis = now, leadsOffTs = { last })
        assertFalse("нужен урок СТРОГО позже снятия", ti.isLeadsNow(name))
    }

    @Test
    fun `принудительная отметка показывает шильдик даже без уроков`() {
        val ti = TeacherInsights(base, emptyList(), nowMillis = now, leadsOn = { true })
        assertFalse(ti.isActive(name))
        assertTrue("forced on побеждает авто-логику", ti.isLeadsNow(name))
    }

    @Test
    fun `снятие при недельном шаблоне без дат держит шильдик снятым`() {
        val ti =
            TeacherInsights(
                base,
                listOf(lesson("")),
                nowMillis = now,
                leadsOffTs = { now - 86_400_000L },
            )
        assertTrue(ti.isActive(name))
        assertFalse(ti.isLeadsNow(name))
        assertNull("датированных уроков нет", ti.lastLessonMillis(name))
    }

    @Test
    fun `override других преподавателей не задевает`() {
        val teachers = base + Teacher(name = "Купрейчик Наталья Андреевна")
        val ti =
            TeacherInsights(
                teachers,
                listOf(lesson("10.01.2027"), lesson("").copy(teacherFirst = "Купрейчик Н.А.")),
                nowMillis = now,
                leadsOffTs = { if (it == name) now else null },
                leadsOn = { it != name && it.contains("Купрейчик") },
            )
        assertFalse("снятая отметка — скрыт", ti.isLeadsNow(name))
        assertTrue("принудительная — показан", ti.isLeadsNow("Купрейчик Наталья Андреевна"))
    }

    @Test
    fun `lastLessonMillis — epoch последней датированной пары`() {
        val ti =
            TeacherInsights(
                base,
                listOf(lesson("01.09.2026"), lesson("10.01.2027")),
                nowMillis = now,
            )
        val last = ti.lastLessonMillis(name)!!
        assertTrue("побеждает максимальная дата", last > now - 30L * 24 * 60 * 60 * 1000)
    }
}
