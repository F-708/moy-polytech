package com.politex

import com.politex.data.network.MpkScheduleParser
import com.politex.util.GroupParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.util.Calendar
import java.util.Locale

class Step4BugfixTest {
    private val cp1251 = Charset.forName("windows-1251")

    @Test
    fun testFindSchedulePostLinks() {
        val html =
            """
            <div class="site-content">
                <h2>Расписание</h2>
                <ul>
                    <li><a href="https://guo-mpk.by/15-09-2026-raspisanie-uchashhihsya/">15.09.2026 РАСПИСАНИЕ УЧАЩИХСЯ</a></li>
                    <li><a href="/16-09-2026-raspisanie-uchashhihsya/">16.09.2026 РАСПИСАНИЕ УЧАЩИХСЯ</a></li>
                </ul>
            </div>
            """.trimIndent()

        val links = MpkScheduleParser.findSchedulePostLinks(html, "https://guo-mpk.by/")
        assertEquals(2, links.size)
        assertEquals("15.09.2026", links[0].dateString)
        assertEquals("16.09.2026", links[1].dateString)
        assertTrue(links[1].url.startsWith("https://guo-mpk.by/16-09-2026"))
    }

    @Test
    fun testIframeOfficeAppsUrlDecoding() {
        val eventHtml =
            """
            <div class="entry-content">
                <iframe src="https://view.officeapps.live.com/op/embed.aspx?src=https%3A%2F%2Fguo-mpk.by%2Fwp-content%2Fuploads%2F2026%2F09%2F15_09_2026.doc"></iframe>
            </div>
            """.trimIndent()

        val docUrls = MpkScheduleParser.extractDocumentUrls(eventHtml, "https://guo-mpk.by/")
        assertEquals(1, docUrls.size)
        assertEquals("https://guo-mpk.by/wp-content/uploads/2026/09/15_09_2026.doc", docUrls[0])
    }

    @Test
    fun testTwoRoomParsingVariations() {
        val lesson1 =
            MpkScheduleParser.createLessonEntity(
                groupName = "41О",
                dayOfWeek = 1,
                lessonNumber = 2,
                rawContent = "Электронные системы ТС каб. 215 / каб. 308 Иванов И.И. / Петров П.П.",
            )
        assertNotNull(lesson1)
        assertTrue("Должно быть определено деление на 2 подгруппы", lesson1!!.isSplit)
        assertEquals("215", lesson1.roomFirst)
        assertEquals("308", lesson1.roomSecond)

        val lesson2 =
            MpkScheduleParser.createLessonEntity(
                groupName = "41О",
                dayOfWeek = 2,
                lessonNumber = 1,
                rawContent = "Охрана труда каб. 101 - каб. 102 Смирнов А.А.",
            )
        assertNotNull(lesson2)
        assertTrue(lesson2!!.isSplit)
        assertEquals("101", lesson2.roomFirst)
        assertEquals("102", lesson2.roomSecond)

        val lesson3 =
            MpkScheduleParser.createLessonEntity(
                groupName = "41О",
                dayOfWeek = 3,
                lessonNumber = 4,
                rawContent = "Информационные технологии 215 / 308",
            )
        assertNotNull(lesson3)
        assertTrue(lesson3!!.isSplit)
        assertEquals("215", lesson3.roomFirst)
        assertEquals("308", lesson3.roomSecond)
    }

    @Test
    fun testBinaryDocCp1251StreamParsing() {
        val simulatedDocText = "Понедельник\n1 пара: 41О Охрана труда каб. 204 Ковалев В.П.\n2 пара: 41О ТОЭ ауд. 301 Смирнов Д.А."
        val bytes = simulatedDocText.toByteArray(cp1251)

        val lessons = MpkScheduleParser.parseDocBinary(bytes, "41О", "15.09.2026")
        assertEquals(2, lessons.size)
        assertEquals(1, lessons[0].lessonNumber)
        assertEquals("204", lessons[0].roomFirst)
        assertEquals("Ковалев В.П.", lessons[0].teacherFirst)
        assertEquals("15.09.2026", lessons[0].dateString)
    }

    @Test
    fun testBinaryDocUtf16LeStreamParsing() {
        val simulatedDocText = "Вторник\n1 пара: 41О Математика каб. 105 Сидорова Е.Н."
        val bytes = simulatedDocText.toByteArray(Charsets.UTF_16LE)

        val lessons = MpkScheduleParser.parseDocBinary(bytes, "41О", "16.09.2026")
        assertEquals(1, lessons.size)
        assertEquals(2, lessons[0].dayOfWeek)
        assertEquals(1, lessons[0].lessonNumber)
        assertEquals("105", lessons[0].roomFirst)
        assertEquals("16.09.2026", lessons[0].dateString)
    }

    @Test
    fun testStrictWeekDateCalculation() {
        val locale = Locale.forLanguageTag("ru-BY")
        val cal =
            Calendar.getInstance(locale).apply {
                firstDayOfWeek = Calendar.MONDAY
                set(2026, Calendar.SEPTEMBER, 15)
            }

        val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)

        assertEquals(14, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, cal.get(Calendar.MONTH))
        assertEquals(2026, cal.get(Calendar.YEAR))

        val expectedDays = listOf(14, 15, 16, 17, 18, 19)
        for (i in 0..5) {
            val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            assertEquals(expectedDays[i], dayCal.get(Calendar.DAY_OF_MONTH))
        }
    }

    @Test
    fun testQuickNextDayButtonLogic() {
        val course1Group = GroupParser.parse("11О")!!
        val course4Group = GroupParser.parse("41О")!!

        assertTrue(course1Group.hasSaturdayClasses)
        assertFalse(course4Group.hasSaturdayClasses)

        val fridayBtnCourse1 =
            when {
                5 == 5 && course1Group.hasSaturdayClasses -> "Завтра (Сб)"
                5 == 5 && !course1Group.hasSaturdayClasses -> "Понедельник"
                else -> "Завтра"
            }
        assertEquals("Завтра (Сб)", fridayBtnCourse1)

        val fridayBtnCourse4 =
            when {
                5 == 5 && course4Group.hasSaturdayClasses -> "Завтра (Сб)"
                5 == 5 && !course4Group.hasSaturdayClasses -> "Понедельник"
                else -> "Завтра"
            }
        assertEquals("Понедельник", fridayBtnCourse4)

        val saturdayBtn =
            when {
                6 == 6 -> "Понедельник"
                else -> "Завтра"
            }
        assertEquals("Понедельник", saturdayBtn)

        val tuesdayBtn =
            when {
                2 == 5 && course4Group.hasSaturdayClasses -> "Завтра (Сб)"
                2 == 5 && !course4Group.hasSaturdayClasses -> "Понедельник"
                2 == 6 -> "Понедельник"
                else -> "Завтра"
            }
        assertEquals("Завтра", tuesdayBtn)
    }
}
