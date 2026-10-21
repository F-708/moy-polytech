package com.politex

import com.politex.data.network.MpkScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealSiteDocParserTest {
    private fun realDocBytes(): ByteArray = javaClass.getResourceAsStream("/raspisanie-15.09.2026.doc")!!.readBytes()

    @Test
    fun `извлекает все 8 уроков группы 41О из реального документа сайта`() {
        val lessons = MpkScheduleParser.parseFile(realDocBytes(), targetGroup = "41О", targetDate = "15.09.2026")

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), lessons.map { it.lessonNumber })
        assertEquals("41О", lessons.first().groupName)
        assertEquals(2, lessons.first().dayOfWeek)
        assertEquals("15.09.2026", lessons.first().dateString)

        val first = lessons[0]
        assertTrue(first.isSplit)
        assertEquals("249", first.roomFirst)
        assertEquals("112", first.roomSecond)
        assertTrue(first.teacherFirst.startsWith("Зыбин"))
        assertTrue(first.teacherSecond.startsWith("Купрейчик"))

        assertEquals("08:15", first.timeStart)
        assertEquals("09:00", first.timeEnd)

        val third = lessons[2]
        assertTrue(third.isSplit)
        assertEquals("Экономика организации", third.subjectRaw)
        assertEquals("319", third.roomFirst)
        assertEquals("339", third.roomSecond)

        val fifth = lessons[4]
        assertTrue(!fifth.isSplit)
        assertEquals("242", fifth.roomFirst)
        assertTrue(fifth.teacherFirst.startsWith("Дубатовка"))

        assertEquals("Охрана окружающей среды и энергосбережение", lessons[6].subjectRaw)

        val eighth = lessons[7]
        assertTrue(eighth.isSplit)
        assertEquals("Физическая культура и здоровье", eighth.subjectRaw)
        assertEquals("155", eighth.roomFirst)
        assertEquals("150", eighth.roomSecond)
    }

    @Test
    fun `извлекает уроки 9 и 10 и их точное время (21М)`() {
        val lessons = MpkScheduleParser.parseFile(realDocBytes(), targetGroup = "21М", targetDate = "15.09.2026")

        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9, 10), lessons.map { it.lessonNumber })

        val lesson9 = lessons.first { it.lessonNumber == 9 }
        val lesson10 = lessons.first { it.lessonNumber == 10 }
        assertEquals("16:05", lesson9.timeStart)
        assertEquals("16:50", lesson9.timeEnd)
        assertEquals("17:00", lesson10.timeStart)
        assertEquals("17:45", lesson10.timeEnd)
        assertEquals("Информационные технологии", lesson9.subjectRaw)
        assertTrue(lesson9.isSplit)
        assertEquals("313", lesson9.roomFirst)
        assertEquals("315", lesson9.roomSecond)
    }

    @Test
    fun `для группы, которой нет в документе, результат пустой`() {
        val lessons = MpkScheduleParser.parseFile(realDocBytes(), targetGroup = "99Z", targetDate = "15.09.2026")
        assertTrue(lessons.isEmpty())
    }

    private fun wednesdayDocBytes(): ByteArray = javaClass.getResourceAsStream("/raspisanie-16.09.2026.doc")!!.readBytes()

    @Test
    fun `извлекает 8 уроков группы 41О из документа на среду 16_09`() {
        val lessons = MpkScheduleParser.parseFile(wednesdayDocBytes(), targetGroup = "41О", targetDate = "16.09.2026")

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), lessons.map { it.lessonNumber })
        assertEquals(3, lessons.first().dayOfWeek)
        assertEquals("16.09.2026", lessons.first().dateString)

        val third = lessons[2]
        assertTrue(third.isSplit)
        assertEquals("311", third.roomFirst)
        assertEquals("306", third.roomSecond)
        assertTrue(third.teacherFirst.startsWith("Тумилович"))

        val seventh = lessons[6]
        assertTrue(!seventh.isSplit)
        assertEquals("112", seventh.roomFirst)
        assertTrue(seventh.teacherFirst.startsWith("Купрейчик"))
        assertEquals("14:15", seventh.timeStart)
        assertEquals("15:00", seventh.timeEnd)
    }

    @Test
    fun `СТРАТЕГИЯ cells - уроки находятся даже если символы рамок таблицы ПОТЕРЯНЫ`() {
        val runs = MpkScheduleParser.extractUtf16LeRuns(realDocBytes())
        val textWithoutBorders =
            runs
                .joinToString("\n")
                .replace(Regex("[│─┬┴├┤┌┐└┘]"), "\n")

        val lessons = MpkScheduleParser.parsePlainTextSchedule(textWithoutBorders, "41О", "15.09.2026")

        assertEquals(
            "Должна сработать позиционная стратегия cells, статистика: ${MpkScheduleParser.lastParseStats}",
            "cells",
            MpkScheduleParser.lastParseStats.strategy,
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), lessons.map { it.lessonNumber })

        val first = lessons[0]
        assertTrue("first=$first stats=${MpkScheduleParser.lastParseStats}", first.isSplit)
        assertEquals("249", first.roomFirst)
        assertEquals("112", first.roomSecond)
        assertTrue("teacher=${first.teacherFirst}", first.teacherFirst.startsWith("Зыбин"))
        assertEquals("08:15", first.timeStart)

        val eighth = lessons[7]
        assertTrue("eighth=$eighth", eighth.isSplit)
        assertEquals("Физическая культура и здоровье", eighth.subjectRaw)
        assertEquals("155", eighth.roomFirst)
        assertEquals("150", eighth.roomSecond)
    }

    @Test
    fun `HTML-страница вместо документа распознается и не парсится как расписание`() {
        val html =
            "<!DOCTYPE html><html><head><title>302 Found</title></head><body>redirect</body></html>"
                .toByteArray(Charsets.UTF_8)
        val lessons = MpkScheduleParser.parseFile(html, targetGroup = "41О", targetDate = "15.09.2026")

        assertTrue(lessons.isEmpty())
        assertTrue(MpkScheduleParser.lastParseStats.wasHtml)
    }
}
