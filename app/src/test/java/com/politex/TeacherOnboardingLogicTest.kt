package com.politex

import com.politex.data.repository.Teacher
import com.politex.ui.teacher.TeacherOnboardingLogic
import com.politex.ui.teacher.TeacherProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherOnboardingLogicTest {

    private val teachers =
        listOf(

            Teacher("Еременко Ольга Васильевна", "Преподаватель", "Электрические измерения"),
            Teacher("Еременко Владимир Викторович", "Преподаватель", "Электрические машины, Электробезопасность"),
            Teacher("Савицкая Ольга Владимировна", "Преподаватель", "Иностранный язык"),
            Teacher("Савицкая Татьяна Владимировна", "Преподаватель", "Логистика, Международный маркетинг"),
            Teacher("Смирнова Татьяна Анатольевна", "Преподаватель", "Математика, Информатика"),
            Teacher("Кайдова Любовь Сергеевна", "Преподаватель", "Математика"),
            Teacher("Мельник Артём Сергеевич", "Заместитель директора", "Коммуникационный дизайн"),
            Teacher("Кривенькая Татьяна Николаевна", "Заведующий отделением", "Специальные дисциплины"),

            Teacher("Иванова Мария Петровна", "Преподаватель", "Физика"),
            Teacher("Иванова Мария Сергеевна", "Заведующий отделением", "Химия"),
        )

    private fun search(q: String) = TeacherOnboardingLogic.teacherSearch(teachers, q)

    @Test
    fun `empty query returns all alphabetically`() {
        val res = search("")
        assertEquals(teachers.size, res.size)
        assertEquals(res.map { it.name.lowercase() }, res.map { it.name.lowercase() }.sorted())
    }

    @Test
    fun `blank query returns all`() {
        assertEquals(teachers.size, search("   ").size)
    }

    @Test
    fun `typo ivanoa finds ivanova`() {
        val res = search("иваноа")
        assertEquals(2, res.count { it.name.startsWith("Иванова") })
    }

    @Test
    fun `distance-2 typo finds eremenko`() {
        assertTrue(search("еремонка").any { it.name.startsWith("Еременко") })
    }

    @Test
    fun `distance-3 typo finds eremenko`() {
        assertTrue(search("ириминко").any { it.name.startsWith("Еременко") })
    }

    @Test
    fun `yo normalization ereMenko with yo matches csv ye`() {
        val res = search("ерёменко")
        assertTrue(res.any { it.name == "Еременко Ольга Васильевна" })
        assertTrue(res.any { it.name == "Еременко Владимир Викторович" })
    }

    @Test
    fun `transposition finds melnik`() {
        assertTrue(search("мелньик").any { it.name.startsWith("Мельник") })
    }

    @Test
    fun `two words match in any order across fields`() {
        val res = search("владимир еременко")
        assertTrue(res.any { it.name == "Еременко Владимир Викторович" })
    }

    @Test
    fun `subject match returns all mathematicians`() {
        val res = search("математика")
        assertTrue(res.any { it.name == "Смирнова Татьяна Анатольевна" })
        assertTrue(res.any { it.name == "Кайдова Любовь Сергеевна" })

        assertFalse(res.any { it.name.startsWith("Еременко") })
    }

    @Test
    fun `surname with wrong firstname still shows candidates`() {
        val res = search("савицкая наталья")
        assertTrue(res.isNotEmpty())
        assertTrue(res.all { it.name.startsWith("Савицкая") })
    }

    @Test
    fun `surname substring finds both`() {
        val res = search("савиц")
        assertEquals(2, res.count { it.name.startsWith("Савицкая") })
    }

    @Test
    fun `search is case insensitive`() {
        assertTrue(search("САВИЦКАЯ").any { it.name.startsWith("Савицкая") })
    }

    @Test
    fun `no match returns empty without crash`() {
        assertTrue(search("zzzqqq").isEmpty())
        assertTrue(search("!! ??").isEmpty())
    }

    @Test
    fun `profile mapping uses surnameKey`() {
        val p = TeacherOnboardingLogic.profileOf(teachers[0])
        assertEquals(TeacherProfile("Еременко Ольга Васильевна", "еременко"), p)
    }

    @Test
    fun `surnameKey normalizes yo to ye`() {
        val t = Teacher("Ерёменко Ольга Викторовна")
        assertEquals("еременко", TeacherOnboardingLogic.profileOf(t).surnameKey)
    }

    @Test
    fun `onboarding shown only while profile null and not skipped`() {
        assertTrue(TeacherOnboardingLogic.showOnboarding(profile = null, skipped = false))
        assertFalse(TeacherOnboardingLogic.showOnboarding(profile = null, skipped = true))
        assertFalse(TeacherOnboardingLogic.showOnboarding(TeacherProfile("а", "а"), skipped = false))
    }
}
