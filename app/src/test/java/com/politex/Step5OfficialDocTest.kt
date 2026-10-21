package com.politex

import com.politex.data.model.CollegeBellSchedule
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Calendar

class Step5OfficialDocTest {
    @Test
    fun testStandardBellSchedule() {
        val standard = CollegeBellSchedule.STANDARD_BELLS
        assertEquals(12, standard.size)

        assertEquals("08:15", standard[0].start)
        assertEquals("09:00", standard[0].end)
        assertEquals(10, standard[0].breakAfterMinutes)

        assertEquals("09:10", standard[1].start)
        assertEquals("09:55", standard[1].end)
        assertEquals(25, standard[1].breakAfterMinutes)
        assertTrue(standard[1].isBigBreak)

        assertEquals("10:20", standard[2].start)
        assertEquals("11:05", standard[2].end)

        assertEquals("11:15", standard[3].start)
        assertEquals("12:00", standard[3].end)
        assertEquals(25, standard[3].breakAfterMinutes)
        assertTrue(standard[3].isBigBreak)

        val last = standard.last()
        assertEquals(12, last.lessonNumber)
        assertEquals("18:50", last.start)
        assertEquals("19:35", last.end)
    }

    @Test
    fun testThursdayBellScheduleWithInfoHour() {
        val thursday = CollegeBellSchedule.THURSDAY_BELLS
        assertEquals(13, thursday.size)

        val infoHour = thursday.find { it.isInfoHour }
        assertNotNull(infoHour)
        assertEquals("14:15", infoHour?.start)
        assertEquals("14:35", infoHour?.end)
        assertEquals("Информационный час", infoHour?.title)

        val lesson7 = thursday.find { it.lessonNumber == 7 }
        assertNotNull(lesson7)
        assertEquals("14:45", lesson7?.start)
        assertEquals("15:30", lesson7?.end)

        val lesson12 = thursday.find { it.lessonNumber == 12 }
        assertEquals("19:20", lesson12?.start)
        assertEquals("20:05", lesson12?.end)
    }

    @Test
    fun testSaturdayBellSchedule() {
        val saturday = CollegeBellSchedule.SATURDAY_BELLS
        assertEquals(8, saturday.size)

        assertEquals("08:15", saturday.first().start)
        assertEquals("09:00", saturday.first().end)
        assertEquals("14:40", saturday.last().start)
        assertEquals("15:25", saturday.last().end)
    }

    @Test
    fun testGetTimeForLessonNumber() {

        val lesson1Mon = CollegeBellSchedule.getTimeForLessonNumber(1, dayOfWeek = 1)
        assertEquals("08:15", lesson1Mon.first)
        assertEquals("09:00", lesson1Mon.second)

        val lesson4Thu = CollegeBellSchedule.getTimeForLessonNumber(4, dayOfWeek = 4)
        assertEquals("11:15", lesson4Thu.first)
        assertEquals("12:00", lesson4Thu.second)
    }

    @Test
    fun testRealCollegeScheduleGridParser() {
        val rawCollegeText =
            """
            1 НЕДЕЛЯ РАСПИСАНИЕ на   15.09.2026 г.  ВТОРНИК
            ┌─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┐
            │          11А            │          11Г            │          11Д            │          11М            │          11Н            │          11О            │          11П            │
            ├─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┤
            │ 1 Биология           205│ 1 БеЯзык             139│ 1 ОбщВедение         334│ 1 ДопрПод/МедПодг331/228│ 1 Физика             252│ 1 География          209│ 1 Математика         402│
            │   Семечко В.А.          │   Самсоник Н.Н.         │   Кривошей Д.А.         │   Серединов А/Будай И.Н.│   Верич А.В.            │   Коваль Л.А.           │   Кайдова Л.С.          │
            │ 2 Биология           205│ 2 БеЯзык             139│ 2 Математика         312│ 2 ДопрПод/МедПодг331/228│ 2 ОбщВедение         343│ 2 География          209│ 2 История            244│
            │   Семечко В.А.          │   Самсоник Н.Н.         │   Халимон А.Ю.          │   Серединов А/Будай И.Н.│   Ламкина И.Н.          │   Коваль Л.А.           │   Побегайло В.В.        │
            │ 3 Химия              205│ 3 История            323│ 3 МедПодг/ДопрПод228/331│ 3 ОбщВедение         343│ 3 Математика         312│ 3 БелЛит             139│ 3 Химия              347│
            │   Семечко В.А.          │   Маркова А.И.          │   Будай И.Н. /Серединов │   Ламкина И.Н.          │   Смирнова Т.А.         │   Самсоник Н.Н.         │   Крейдич Н.В.          │
            │ 4 БелЛит             139│ 4 Биология           205│ 4 МедПодг/ДопрПод228/331│ 4 География          209│ 4 Математика         312│ 4 ИноЯзык/ИноЯзык154/314│ 4 Химия              347│
            │   Самсоник Н.Н.         │   Семечко В.А.          │   Будай И.Н. /Серединов │   Коваль Л.А.           │   Смирнова Т.А.         │   Соколова Т./Снитко Е.И│   Крейдич Н.В.          │
            │ 5 БеЯзык             139│ 5 Биология           205│ 5 Химия              228│ 5 Физика             219│ 5 История            312│ 5 Математика         402│ 5 Информа/Информа305/303│
            │   Самсоник Н.Н.         │   Семечко В.А.          │   Будай И.Н.            │   Коховец Ж.А.          │   Маркова А.И.          │   Кайдова Л.С.          │   Смирнова Т./Халимон А.│
            │ 6 БеЯзык             139│ 6 Математика         403│ 6 БеЯзык             237│ 6 Физика             219│ 6 История            312│ 6 Математика         402│ 6 Биология           205│
            │   Самсоник Н.Н.         │   Смольская Л.А.        │   Загоровская И.Н.      │   Коховец Ж.А.          │   Маркова А.И.          │   Кайдова Л.С.          │   Семечко В.А.          │
            ┌─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┐
            │          11С            │          11Т            │          11Э            │          12Э            │          21Г            │          21Д            │          22Д            │
            ├─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┤
            │ 1 Информа/Информа305/303│ 1 Физика             227│ 1 ДопрПод/МедПодг331/228│ 1 Математика         403│ 1                       │ 1                       │ 1 РусПрофЛекс        135│
            │   Смирнова Т./Халимон А.│   Попко Ю.А.            │   Серединов А/Будай И.Н.│   Смольская Л.А.        │                         │                         │   Романенко И.И.        │
            │ 2 Информа/Информа305/303│ 2 Физика             227│ 2 ДопрПод/МедПодг331/228│ 2 Математика         403│ 2                       │ 2 СтРЯ   /-------135/   │ 2 РусПрофЛекс        135│
            │   Смирнова Т./Халимон А.│   Попко Ю.А.            │   Серединов А/Будай И.Н.│   Смольская Л.А.        │                         │   Романенко И/          │   Романенко И.И.        │
            │ 3 Химия              228│ 3 История            323│ 3 МедПодг/ДопрПод228/331│ 3 Физика             252│ 3 ТОЭ                 125│ 3 Делопроизвод       309│ 3 Математика         403│
            │   Будай И.Н.            │   Маркова А.И.          │   Будай И.Н. /Серединов │   Верич А.В.            │   Попко Ю.А.            │   Загоровская И.Н.      │   Смольская Л.А.        │
            │ 4 Химия              228│ 4 ДопрПод            331│ 4 МедПодг/ДопрПод228/331│ 4 Физика             252│ 4 ФизКулЗ/СМГрупп142/142│ 4 Делопроизвод       309│ 4 СекрДело           332│
            │   Будай И.Н.            │   Серединов А.А.        │   Будай И.Н. /Серединов │   Верич А.В.            │   Мурашко Е.О/Матюк Е.В.│   Загоровская И.Н.      │   Свирид Е.В.           │
            │ 5 История            209│ 5 ДопрПод            331│ 5 Математика         403│ 5 География          252│ 5 ЭлТехМат           125│ 5                       │ 5 СекрДело           332│
            │   Побегайло В.В.        │   Серединов А.А.        │   Смольская Л.А.        │   Коваль Л.А.           │   Цепелев Д.В.          │                         │   Свирид Е.В.           │
            │ 6 История            209│ 6 География          209│ 6 Математика         403│ 6 История            244│ 6 ЭлТехМат           125│ 6                       │ 6                       │
            │   Побегайло В.В.        │   Коваль Л.А.           │   Смольская Л.А.        │   Побегайло В.В.        │   Цепелев Д.В.          │                         │                         │
            │ 7                       │ 7                       │ 7                       │ 7                       │ 7 ОИнжГра/ОИнжГра245/245│ 7 ИнЯзПро/ИнЯзПро247/337│ 7                       │
            │                         │                         │                         │                         │   Белый Е.В. /Демидова Е│   Савицкая О./Павлова А.│                         │
            """.trimIndent()

        val lessons11O = MpkScheduleParser.parsePlainTextSchedule(rawCollegeText, targetGroup = "11О")
        assertEquals(6, lessons11O.size)
        assertEquals("15.09.2026", lessons11O[0].dateString)
        assertEquals(2, lessons11O[0].dayOfWeek)

        assertEquals(1, lessons11O[0].lessonNumber)
        assertEquals("География", lessons11O[0].subjectRaw)
        assertEquals("209", lessons11O[0].roomFirst)
        assertEquals("Коваль Л.А.", lessons11O[0].teacherFirst)
        assertFalse(lessons11O[0].isSplit)

        val lesson4 = lessons11O[3]
        assertEquals(4, lesson4.lessonNumber)
        assertTrue(lesson4.isSplit)
        assertEquals("154", lesson4.roomFirst)
        assertEquals("314", lesson4.roomSecond)
        assertEquals("Соколова Т.", lesson4.teacherFirst)
        assertEquals("Снитко Е.И.", lesson4.teacherSecond)

        val lessons11M = MpkScheduleParser.parsePlainTextSchedule(rawCollegeText, targetGroup = "11М")
        assertEquals(6, lessons11M.size)
        val lesson1M = lessons11M[0]
        assertEquals(1, lesson1M.lessonNumber)
        assertTrue(lesson1M.isSplit)
        assertEquals("331", lesson1M.roomFirst)
        assertEquals("228", lesson1M.roomSecond)
        assertEquals("Серединов А.", lesson1M.teacherFirst)
        assertEquals("Будай И.Н.", lesson1M.teacherSecond)

        val lessons21D = MpkScheduleParser.parsePlainTextSchedule(rawCollegeText, targetGroup = "21Д")

        assertEquals(4, lessons21D.size)
        val lesson21DPair2 = lessons21D.find { it.lessonNumber == 2 }
        assertNotNull(lesson21DPair2)
        assertTrue(lesson21DPair2!!.isSplit)
        assertEquals("135", lesson21DPair2.roomFirst)
        assertEquals("Романенко И.", lesson21DPair2.teacherFirst)
    }

    @Test
    fun testBinaryDocParserWithCellDelimiters() {
        val baos = ByteArrayOutputStream()

        baos.write("Группа 41О\u0007Понедельник\u0007\r\n".toByteArray(charset("windows-1251")))

        baos.write("1 пара\u0007Информатика\u0007каб. 215 / каб. 308\u0007Иванов / Петров\u0007\r\n".toByteArray(charset("windows-1251")))

        baos.write("2 пара\u0007Математика\u0007каб. 104\u0007Сидоров\u0007\r\n".toByteArray(charset("windows-1251")))

        val bytes = baos.toByteArray()
        val lessons = MpkScheduleParser.parseDocBinary(bytes, targetGroup = "41О", targetDate = "15.09.2026")

        assertFalse(lessons.isEmpty())
        assertEquals(2, lessons.size)

        val lesson1 = lessons[0]
        assertEquals("41О", lesson1.groupName)
        assertEquals(1, lesson1.lessonNumber)
        assertTrue(lesson1.isSplit)
        assertEquals("215", lesson1.roomFirst)
        assertEquals("308", lesson1.roomSecond)

        val lesson2 = lessons[1]
        assertEquals(2, lesson2.lessonNumber)
        assertFalse(lesson2.isSplit)
        assertEquals("104", lesson2.roomFirst)
    }

    @Test
    fun testNetworkCandidateUrls() {
        val client = MpkNetworkClient()
        val cal =
            Calendar.getInstance().apply {
                set(2026, Calendar.SEPTEMBER, 15)
            }

        val directUrls = client.generateCandidateDirectUrls(cal)
        assertTrue(directUrls.isNotEmpty())
        assertTrue(directUrls.any { it.first.contains("15.09.2026-raspisanie-uchashhihsya.doc") })

        val eventUrls = client.generateCandidateEventUrls(cal)
        assertTrue(eventUrls.isNotEmpty())
        assertTrue(eventUrls.any { it.first.contains("15-09-2026-raspisanie-uchashhihsya") })
    }
}
