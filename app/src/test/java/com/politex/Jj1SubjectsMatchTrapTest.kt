package com.politex

import com.politex.util.HomeworkMarkers
import com.politex.util.MpkCurriculum
import com.politex.util.SubjectFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Jj1SubjectsMatchTrapTest {
    @Test
    fun `сырые аббревиатуры сайта нормализуются в официальные имена`() {
        assertEquals("Системы технической организации сервиса", SubjectFormatter.normalize("СистТехнОргСер"))
        assertEquals("Системы технической организации сервиса", SubjectFormatter.normalize("СистТехОрг"))
        assertEquals("Статический анализ и оптимизация технологических процессов", SubjectFormatter.normalize("СтатАнализОТП"))
        assertEquals("Электроснабжение городского электрического транспорта", SubjectFormatter.normalize("ЭлСнабГЭТ"))
        assertEquals("Программируемые цифровые устройства", SubjectFormatter.normalize("ПрограмЦУ"))
        assertEquals("Белорусский язык (профессиональная лексика)", SubjectFormatter.normalize("БеПрофЛекс"))
    }

    @Test
    fun `предмет из расписания 41О есть в пикере куррикулума`() {
        assertTrue(MpkCurriculum.getSubjects('О', 4).contains("Системы технической организации сервиса"))
    }

    @Test
    fun `расписание и домашка сходятся — аббревиатура против полного имени`() {
        assertTrue(HomeworkMarkers.subjectsMatch("СистТехнОргСер", "Системы технической организации сервиса"))
        assertTrue(HomeworkMarkers.subjectsMatch("Системы технической организации сервиса", "СистТехнОргСер"))
        assertTrue(HomeworkMarkers.subjectsMatch("СистТехнОргСер", "СистТехОрг"))
    }

    @Test
    fun `опечатка в написании одного предмета всё ещё матчится`() {
        assertTrue(HomeworkMarkers.subjectsMatch("СисиТехОрг", "СистТехОрг"))
        assertTrue(HomeworkMarkers.subjectsMatch("СистТехОрг", "СисиТехОрг"))
    }

    @Test
    fun `ё и е не ломают совпадение`() {
        assertTrue(HomeworkMarkers.subjectsMatch("БухУчёт", "Бухгалтерский учёт"))
    }

    @Test
    fun `разные предметы не склеиваются фаззи-порогом`() {
        assertFalse(HomeworkMarkers.subjectsMatch("Электрические машины", "Электрические аппараты"))
        assertFalse(HomeworkMarkers.subjectsMatch("Электротехника", "Электроника"))
        assertFalse(HomeworkMarkers.subjectsMatch("Математика", "Информатика"))
        assertFalse(HomeworkMarkers.subjectsMatch("Основы права", "Основы менеджмента"))
    }
}
