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

class TeacherInsightsTest {
    private fun teacher(fullName: String) = Teacher(name = fullName)

    private val base =
        listOf(
            teacher("Зыбин Олег Львович"),
            teacher("Купрейчик Наталья Андреевна"),
            teacher("Мешалкина Ирина Владимировна"),
            teacher("Дубатовка Екатерина Анатольевна"),
            teacher("Березовская Нелли Викторовна"),
            teacher("Савицкая Ольга Владимировна"),
            teacher("Савицкая Татьяна Владимировна"),
            teacher("Будай Инесса Николаевна"),
            teacher("Серединов Андрей Николаевич"),
            teacher("Кривошей Дмитрий Александрович"),
        )

    private fun insights(lessons: List<LessonEntity> = emptyList()) = TeacherInsights(base, lessons)

    private fun lesson(
        teacherFirst: String,
        teacherSecond: String = "",
        roomFirst: String = "",
        roomSecond: String = "",
        subject: String = "",
        date: String = "",
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = 2,
        lessonNumber = 1,
        timeStart = "08:15",
        timeEnd = "09:00",
        subjectRaw = subject,
        roomFirst = roomFirst,
        teacherFirst = teacherFirst,
        roomSecond = roomSecond,
        teacherSecond = teacherSecond,
        dateString = date,
    )

    @Test
    fun `точное совпадение по фамилии и инициалам`() {
        val ti = insights()
        assertEquals("Зыбин Олег Львович", ti.matchTeacher("Зыбин О.Л.")?.name)
        assertEquals("Мешалкина Ирина Владимировна", ti.matchTeacher("Мешалкина И.В.")?.name)
        assertEquals("Дубатовка Екатерина Анатольевна", ti.matchTeacher("Дубатовка Е.А.")?.name)
    }

    @Test
    fun `фамилия без инициалов сопоставляется однозначно`() {
        assertEquals("Кривошей Дмитрий Александрович", insights().matchTeacher("Кривошей Д")?.name)
        assertEquals("Кривошей Дмитрий Александрович", insights().matchTeacher("Кривошей")?.name)
    }

    @Test
    fun `обрезанная фамилия ищется по префиксу`() {
        assertEquals("Березовская Нелли Викторовна", insights().matchTeacher("Березовска")?.name)
        assertEquals("Березовская Нелли Викторовна", insights().matchTeacher("Березовск")?.name)
    }

    @Test
    fun `однофамильцы различаются по инициалам`() {
        val ti = insights()
        assertEquals("Савицкая Ольга Владимировна", ti.matchTeacher("Савицкая О.")?.name)
        assertEquals("Савицкая Татьяна Владимировна", ti.matchTeacher("Савицкая Т.В.")?.name)

        assertNull(ti.matchTeacher("Савицкая"))
    }

    @Test
    fun `неизвестная фамилия возвращает null`() {
        val ti = insights()
        assertNull(ti.matchTeacher("Несуществующий И.И."))
        assertNull(ti.matchTeacher(""))
        assertNull(ti.matchTeacher("Ы"))
    }

    @Test
    fun `разбор строки с двумя преподавателями через слэш`() {
        val names = TeacherInsights.splitTeacherNames("Зыбин О.Л. /Купрейчик Н.А.")
        assertEquals(2, names.size)
        assertEquals("Зыбин О.Л.", names[0])
        assertEquals("Купрейчик Н.А.", names[1])
    }

    @Test
    fun `строки из реального расписания парсятся полностью`() {
        val raw = "Серединов А/Будай И.Н."
        val names = TeacherInsights.splitTeacherNames(raw)
        assertEquals(2, names.size)
        val ti = insights()
        assertEquals("Серединов Андрей Николаевич", ti.matchTeacher(names[0])?.name)
        assertEquals("Будай Инесса Николаевна", ti.matchTeacher(names[1])?.name)
    }

    @Test
    fun `активные преподаватели определяются из уроков группы`() {
        val lessons =
            listOf(
                lesson(teacherFirst = "Зыбин О.Л. /Купрейчик Н.А.", roomFirst = "249", roomSecond = "112"),
                lesson(teacherFirst = "Дубатовка Е.А.", roomFirst = "242"),
                lesson(teacherFirst = "Посторонний П.П.", roomFirst = "999"),
            )
        val ti = insights(lessons)

        assertEquals(3, ti.activeNames.size)
        assertTrue(ti.isActive("Зыбин Олег Львович"))
        assertTrue(ti.isActive("Купрейчик Наталья Андреевна"))
        assertTrue(ti.isActive("Дубатовка Екатерина Анатольевна"))
        assertTrue("Преподаватель вне базы не попадает в активные", !ti.isActive("Посторонний"))
    }

    @Test
    fun `кабинеты преподавателя собираются из расписания по частоте`() {
        val lessons =
            listOf(
                lesson(teacherFirst = "Зыбин О.Л.", roomFirst = "249"),
                lesson(teacherFirst = "Зыбин О.Л.", roomFirst = "249"),
                lesson(teacherFirst = "Зыбин О.Л.", roomFirst = "112"),
            )
        val rooms = insights(lessons).rooms("Зыбин Олег Львович")
        assertEquals(listOf("249", "112"), rooms)
    }

    @Test
    fun `дисциплины преподавателя определяются из его уроков`() {
        val lessons =
            listOf(
                lesson(teacherFirst = "Дубатовка Е.А.", subject = "Системы технологий"),
                lesson(teacherFirst = "Дубатовка Е.А.", subject = "Охрана труда"),
                lesson(teacherFirst = "Дубатовка Е.А.", subject = "Системы технологий"),
            )
        val subjects = insights(lessons).subjects("Дубатовка Екатерина Анатольевна")
        assertTrue(subjects.contains("Системы технологий"))
        assertTrue(subjects.contains("Охрана труда"))
        assertEquals("Дубли убраны", 2, subjects.size)
    }

    @Test
    fun `преподаватели с историей расписания подсвечиваются как активные`() {
        val lessons =
            listOf(
                lesson(teacherFirst = "Мешалкина И.В.", roomFirst = "339", date = "2026-09-15"),
            )
        val ti = TeacherInsights(base, lessons, nowMillis = 1_789_862_400_000L)
        assertTrue(ti.isActive("Мешалкина Ирина Владимировна"))
        assertEquals(1, ti.lessonCount("Мешалкина Ирина Владимировна"))

        assertEquals("15.09.2026", ti.lastSeenDate("Мешалкина Ирина Владимировна"))
    }

    @Test
    fun `QA15 lastDate выбирается по epoch а не лексикографически на стыке месяцев`() {
        val lessons =
            listOf(
                lesson(teacherFirst = "Зыбин О.Л.", date = "30.09.2026"),
                lesson(teacherFirst = "Зыбин О.Л.", date = "01.10.2026"),
            )

        assertEquals("01.10.2026", insights(lessons).lastSeenDate("Зыбин Олег Львович"))
    }

    @Test
    fun `сопоставление через второй преподаватель в подгруппе`() {
        val lessons =
            listOf(
                lesson(
                    teacherFirst = "Зыбин О.Л.",
                    teacherSecond = "Купрейчик Н.А.",
                    roomFirst = "249",
                    roomSecond = "112",
                ),
            )
        val ti = insights(lessons)

        assertNotNull(ti.rooms("Зыбин Олег Львович"))
        assertNotNull(ti.rooms("Купрейчик Наталья Андреевна"))
    }

    @Test
    fun `W28 недельный шаблон держит шильдик без дат`() {
        val ti = TeacherInsights(base, listOf(lesson(teacherFirst = "Зыбин О.Л.", date = "")), nowMillis = 1_800_000_000_000L)
        assertTrue(ti.isActive("Зыбин Олег Львович"))
    }

    @Test
    fun `W28 датированный урок в пределах порога держит шильдик`() {
        val now = 1_800_000_000_000L
        val ti = TeacherInsights(base, listOf(lesson(teacherFirst = "Зыбин О.Л.", date = "10.01.2027")), nowMillis = now)
        assertTrue("дата 5 дней назад — активен", ti.isActive("Зыбин Олег Львович"))
    }

    @Test
    fun `W28 старый датированный урок гасит шильдик`() {
        val now = 1_800_000_000_000L
        val ti = TeacherInsights(base, listOf(lesson(teacherFirst = "Зыбин О.Л.", date = "01.09.2026")), nowMillis = now)
        assertFalse("дата старше 30 дней без шаблона — погашен", ti.isActive("Зыбин Олег Львович"))
    }

    @Test
    fun `W28 смешанный случай дат побеждает свежая`() {
        val now = 1_800_000_000_000L
        val ti =
            TeacherInsights(
                base,
                listOf(
                    lesson(teacherFirst = "Зыбин О.Л.", date = "01.09.2026"),
                    lesson(teacherFirst = "Зыбин О.Л.", date = "12.01.2027"),
                ),
                nowMillis = now,
            )
        assertTrue("есть свежая дата — активен", ti.isActive("Зыбин Олег Львович"))
    }
}
