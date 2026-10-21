package com.politex

import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.widget.TeacherWidgetData
import org.junit.Assert.assertEquals
import org.junit.Test

class TeacherWidgetSubjectTest {
    private fun row(subject: String): TeacherLessonEntity =
        TeacherLessonEntity(
            teacherName = "Деревянко Т.Ф.",
            surnameKey = "деревянко",
            dateString = "05.10.2026",
            dayOfWeek = 1,
            lessonNumber = 1,
            groups = "32Н",
            subject = subject,
            room = "150",
        )

    @Test
    fun `виджет-данные раскрывают сокращения преподавательского документа`() {
        assertEquals("Физкультура", TeacherWidgetData.toLesson(row("ФизК"), 1).subject)
        assertEquals("Инф. технол.", TeacherWidgetData.toLesson(row("ИнфТ"), 1).subject)
        assertEquals("Экон. организ.", TeacherWidgetData.toLesson(row("ЭкОр"), 1).subject)
        assertEquals("Осн. менеджмента", TeacherWidgetData.toLesson(row("ОМен"), 1).subject)
        assertEquals(
            "Технология и оборудование термических вакуумно-элионных и плазмохимических процессов",
            TeacherWidgetData.toLesson(row("ТехО"), 1).subject,
        )
    }

    @Test
    fun `полные и короткие имена проходят без изменений`() {
        assertEquals("Физкультура", TeacherWidgetData.toLesson(row("Физическая культура и здоровье"), 1).subject)
        assertEquals("Математика", TeacherWidgetData.toLesson(row("Математика"), 1).subject)
        assertEquals("ТОЭ", TeacherWidgetData.toLesson(row("ТОЭ"), 1).subject)
    }

    @Test
    fun `пустой предмет и легас-маркер Вые дают пустую строку`() {
        assertEquals("", TeacherWidgetData.toLesson(row(""), 1).subject)
        assertEquals("", TeacherWidgetData.toLesson(row("Вые"), 1).subject)
    }

    @Test
    fun `остальные поля строки не затрагиваются`() {
        val lesson = TeacherWidgetData.toLesson(row("ФизК"), 1)
        assertEquals(1, lesson.lessonNumber)
        assertEquals(listOf("32Н"), lesson.groups)
        assertEquals("150", lesson.room)
        assertEquals("ЫыЫы", TeacherWidgetData.toLesson(row("ЫыЫы"), 1).subject)
    }
}
