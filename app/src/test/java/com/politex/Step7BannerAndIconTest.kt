package com.politex

import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import com.politex.util.GroupParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class Step7BannerAndIconTest {
    @Test
    fun testDirectUrlGenerationForSpecificDate() {
        val networkClient = MpkNetworkClient()

        val cal =
            Calendar.getInstance(TimeZone.getTimeZone("GMT+3")).apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.SEPTEMBER)
                set(Calendar.DAY_OF_MONTH, 15)
                set(Calendar.HOUR_OF_DAY, 10)
            }

        val directUrls = networkClient.generateCandidateDirectUrls(cal)
        assertTrue(directUrls.isNotEmpty())

        val urlsOnly = directUrls.map { it.first }

        val expectedDirectUrl = "https://guo-mpk.by/wp-content/uploads/2026/09/15.09.2026-raspisanie-uchashhihsya.doc"
        assertTrue(
            "Список URL должен содержать $expectedDirectUrl, получено: $urlsOnly",
            urlsOnly.contains(expectedDirectUrl),
        )
    }

    @Test
    fun testEventUrlGenerationForSpecificDate() {
        val networkClient = MpkNetworkClient()

        val cal =
            Calendar.getInstance(TimeZone.getTimeZone("GMT+3")).apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.SEPTEMBER)
                set(Calendar.DAY_OF_MONTH, 15)
            }

        val eventUrls = networkClient.generateCandidateEventUrls(cal)
        assertTrue(eventUrls.isNotEmpty())

        val urlsOnly = eventUrls.map { it.first }
        val expectedEventUrl = "https://guo-mpk.by/raspisanie/15-09-2026-raspisanie-uchashhihsya/?occurrence=2026-09-15"
        assertTrue(
            "Список URL событий должен содержать $expectedEventUrl, получено: $urlsOnly",
            urlsOnly.contains(expectedEventUrl),
        )
    }

    @Test
    fun testUserAgentHeader() {
        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            MpkNetworkClient.BROWSER_USER_AGENT,
        )
    }

    @Test
    fun testGroup41OParsing() {
        val group = GroupParser.parse("41О")
        assertNotNull(group)
        assertEquals(4, group?.course)
        assertEquals(1, group?.groupNumber)
        assertEquals('О', group?.specialtyCode)
        assertEquals("41О", group?.canonicalName)

        val rawSchedule =
            """
            Расписание на вторник 15.09.2026
            Группа 41О
            1 пара Математика каб. 314
            2 пара 1 п/г Иностранный язык 215 / 2 п/г Информационные технологии 308
            3 пара Физическая культура и здоровье
            """.trimIndent()

        val lessons = MpkScheduleParser.parsePlainTextSchedule(rawSchedule, "41О", "15.09.2026")
        assertEquals(3, lessons.size)

        val lesson1 = lessons[0]
        assertEquals(1, lesson1.lessonNumber)
        assertEquals("Математика", lesson1.subjectRaw)
        assertEquals("314", lesson1.roomFirst)
        assertEquals(false, lesson1.isSplit)

        val lesson2 = lessons[1]
        assertEquals(2, lesson2.lessonNumber)
        assertEquals(true, lesson2.isSplit)
        assertEquals("215", lesson2.roomFirst)
        assertEquals("308", lesson2.roomSecond)
    }
}
