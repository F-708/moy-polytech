package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step3NetworkArchiveTest {
    @Test
    fun testExtractDocumentUrlsFromHtml() {
        val sampleHtml =
            """
            <html>
                <body>
                    <div class="entry-content">
                        <h2>Расписание учебных занятий</h2>
                        <p><a href="https://guo-mpk.by/wp-content/uploads/2025/03/raspisanie_03_03_2025.docx">Расписание с 03.03 по 08.03</a></p>
                        <p><a href="/wp-content/uploads/2025/03/izmeneniya_04_03.doc">Изменения на 04.03</a></p>
                        <iframe src="https://view.officeapps.live.com/op/view.aspx?src=https%3A%2F%2Fguo-mpk.by%2Fwp-content%2Fuploads%2F2025%2F03%2Fsubbota.docx"></iframe>
                    </div>
                </body>
            </html>
            """.trimIndent()

        val urls = MpkScheduleParser.extractDocumentUrls(sampleHtml, "https://guo-mpk.by/")
        assertTrue("Должны быть извлечены ссылки на docx/doc", urls.isNotEmpty())
        assertTrue("Должна быть прямая ссылка на docx", urls.any { it.contains("raspisanie_03_03_2025.docx") })
        assertTrue(
            "Относительная ссылка должна быть преобразована в абсолютную",
            urls.any {
                it.startsWith("https://guo-mpk.by/wp-content/uploads/2025/03/izmeneniya_04_03.doc")
            },
        )
        assertTrue("Ссылка из iframe office viewer должна быть извлечена", urls.any { it.contains("subbota.docx") })
    }

    @Test
    fun testParseLessonEntitySingleGroup() {
        val rawContent = "Электронные системы механических транспортных средств ауд. 203 Иванов И.И."
        val lesson =
            MpkScheduleParser.createLessonEntity(
                groupName = "41О",
                dayOfWeek = 1,
                lessonNumber = 1,
                rawContent = rawContent,
            )

        assertNotNull(lesson)
        assertEquals("41О", lesson!!.groupName)
        assertEquals(1, lesson.dayOfWeek)
        assertEquals(1, lesson.lessonNumber)

        assertEquals("08:15", lesson.timeStart)
        assertEquals("09:00", lesson.timeEnd)
        assertEquals("203", lesson.roomFirst)
        assertEquals("Иванов И.И.", lesson.teacherFirst)
        assertFalse(lesson.isSplit)
        assertEquals("Электрон. сист. ТС", lesson.shortSubjectName)
        assertEquals("ЭСТС", lesson.subjectAcronym)
    }

    @Test
    fun testParseLessonEntitySubgroupSplit() {
        val rawContent = "1 п/г: Информационные технологии ауд. 305 Петров А.В. / 2 п/г: Охрана труда ауд. 102 Сидоров К.С."
        val lesson =
            MpkScheduleParser.createLessonEntity(
                groupName = "41О",
                dayOfWeek = 2,
                lessonNumber = 3,
                rawContent = rawContent,
            )

        assertNotNull(lesson)
        assertTrue(lesson!!.isSplit)
        assertEquals("305", lesson.roomFirst)
        assertEquals("Петров А.В.", lesson.teacherFirst)
        assertEquals("102", lesson.roomSecond)
        assertEquals("Сидоров К.С.", lesson.teacherSecond)

        assertEquals("10:20", lesson.timeStart)
        assertEquals("11:05", lesson.timeEnd)
    }

    @Test
    fun testParsePlainTextSchedule() {
        val plainText =
            """
            Понедельник
            1 пара: 41О Охрана труда каб. 204 Ковалев В.П.
            2 пара: 41О ТОЭ ауд. 301 Смирнов Д.А.
            3 пара: 11Э Математика ауд. 105
            """.trimIndent()

        val lessons = MpkScheduleParser.parsePlainTextSchedule(plainText, "41О")
        assertEquals(2, lessons.size)
        assertEquals(1, lessons[0].lessonNumber)
        assertEquals("204", lessons[0].roomFirst)
        assertEquals("Ковалев В.П.", lessons[0].teacherFirst)
        assertEquals(2, lessons[1].lessonNumber)
        assertEquals("301", lessons[1].roomFirst)
    }

    @Test
    fun testArchiveLessonFiltering() {
        val lessons =
            listOf(
                LessonEntity(
                    id = 1,
                    groupName = "41О",
                    dayOfWeek = 1,
                    lessonNumber = 1,
                    timeStart = "08:30",
                    timeEnd = "10:05",
                    subjectRaw = "Охрана труда ауд 204",
                    roomFirst = "204",
                    teacherFirst = "Ковалев В.П.",
                    dateString = "2025-03-03",
                ),
                LessonEntity(
                    id = 2,
                    groupName = "41О",
                    dayOfWeek = 1,
                    lessonNumber = 2,
                    timeStart = "10:15",
                    timeEnd = "11:50",
                    subjectRaw = "Теоретические основы электротехники ауд 301",
                    roomFirst = "301",
                    teacherFirst = "Смирнов Д.А.",
                    dateString = "2025-03-03",
                ),
                LessonEntity(
                    id = 3,
                    groupName = "41О",
                    dayOfWeek = 2,
                    lessonNumber = 1,
                    timeStart = "08:30",
                    timeEnd = "10:05",
                    subjectRaw = "Математика ауд 105",
                    roomFirst = "105",
                    teacherFirst = "Петрова Е.Н.",
                    dateString = "2025-03-04",
                ),
            )

        val filteredByDate = lessons.filter { it.dateString == "2025-03-03" }
        assertEquals(2, filteredByDate.size)

        val filteredByQuery =
            lessons.filter {
                it.subjectRaw.contains("ТОЭ", ignoreCase = true) ||
                    it.teacherFirst.contains("Смирнов", ignoreCase = true) ||
                    it.roomFirst.contains("301", ignoreCase = true)
            }
        assertEquals(1, filteredByQuery.size)
        assertEquals(2, filteredByQuery[0].lessonNumber)
    }
}
