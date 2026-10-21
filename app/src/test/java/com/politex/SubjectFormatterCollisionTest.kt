package com.politex

import com.politex.data.network.MpkScheduleParser
import com.politex.util.MpkCurriculum
import com.politex.util.SubjectFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectFormatterCollisionTest {

    private val targetGroups =
        listOf(
            "41О" to Pair('О', 4),
            "41Г" to Pair('Г', 4),
            "21Д" to Pair('Д', 2),
            "11О" to Pair('О', 1),
        )

    private fun subjectsOf(code: Char, course: Int): List<String> = MpkCurriculum.getSubjects(code, course)

    private fun unionSubjects(): List<String> =
        targetGroups
            .flatMap { (_, sc) -> subjectsOf(sc.first, sc.second) }
            .distinct()

    @Test
    fun `короткие имена предметов 4 групп уникальны и непусты`() {
        val subjects = unionSubjects()
        assertTrue("ожидаются предметы куррикулума", subjects.isNotEmpty())

        val byShort = mutableMapOf<String, MutableList<String>>()
        for (s in subjects) {
            val short = SubjectFormatter.getShortName(s)
            assertTrue("пустое короткое имя для «$s»", short.isNotBlank())
            byShort.getOrPut(short) { mutableListOf() }.add(s)
        }
        val collisions = byShort.filterValues { it.size > 1 }
        assertTrue("коллизии коротких имён: $collisions", collisions.isEmpty())
    }

    @Test
    fun `короткое имя не содержит чужой акроним`() {
        val subjects = unionSubjects()
        val acronyms = subjects.associateWith { SubjectFormatter.getAcronym(it) }
        for (s in subjects) {
            val short = SubjectFormatter.getShortName(s)
            val own = acronyms[s]
            for (o in subjects) {
                if (o == s) continue
                val other = acronyms[o] ?: continue
                if (other.length >= 2 && other != own && short.contains(other)) {
                    throw AssertionError("«$s» → «$short» содержит чужой акроним «$other» (от «$o»)")
                }
            }
        }
    }

    @Test
    fun `короткое имя не равно полному имени другого предмета (4 группы)`() {
        val subjects = unionSubjects().toSet()
        for (s in subjects) {
            val short = SubjectFormatter.getShortName(s)
            if (short in subjects && short != s) {
                throw AssertionError("«$s» показывается как другой предмет «$short»")
            }
        }
    }

    @Test
    fun `ПЦУ — не Системы ЧПУ`() {
        for (raw in listOf("Программирование цифровых устройств", "Программируемые цифровые устройства", "Програм", "ПЦУ")) {
            val short = SubjectFormatter.getShortName(raw)
            assertFalse("«$raw» → «$short» содержит ЧПУ", short.contains("ЧПУ"))
            assertTrue(
                "«$raw» → «$short» не про ПЦУ",
                short.contains("ПЦУ") || short.contains("Программ") || short.contains("цифров", ignoreCase = true),
            )
        }

        assertEquals("ПЦУ", SubjectFormatter.getShortName("Программируемые цифровые устройства"))
        assertEquals("ПЦУ", SubjectFormatter.getAcronym("Программируемые цифровые устройства"))

        assertTrue(SubjectFormatter.getShortName("Программирование для станков с ЧПУ").contains("ЧПУ"))
    }

    @Test
    fun `весь курикулум — короткое имя не равно другому предмету и непустое`() {
        for (s in MpkCurriculum.ALL_SUBJECTS) {
            val short = SubjectFormatter.getShortName(s)
            assertTrue("пустое короткое имя для «$s»", short.isNotBlank())
            if (short in MpkCurriculum.ALL_SUBJECTS && short != s) {
                throw AssertionError("«$s» показывается как другой предмет «$short»")
            }
        }
    }

    @Test
    fun `фикстура 16_09 41О — Програм даёт ПЦУ без кабинета и без Системы ЧПУ`() {
        val bytes =
            runCatching { javaClass.getResourceAsStream("/raspisanie-16.09.2026.doc")!!.readBytes() }.getOrNull()
                ?: return
        val lessons = MpkScheduleParser.parseFile(bytes, "41О", "16.09.2026")
        if (lessons.isEmpty()) return

        val pcu = lessons.filter { it.subjectRaw.contains("цифров", ignoreCase = true) }
        assertTrue("нет строки ПЦУ у 41О", pcu.isNotEmpty())
        for (l in pcu) {
            assertEquals("Программируемые цифровые устройства", l.subjectRaw)
            assertEquals("ПЦУ", SubjectFormatter.getShortName(l.subjectRaw))
            assertTrue("в предмет вклеился кабинет: «${l.subjectRaw}»", l.subjectRaw.none { it.isDigit() })
        }
        assertTrue(
            "у 41О появился предмет «Системы ЧПУ»",
            lessons.none { it.subjectRaw.contains("ЧПУ") || SubjectFormatter.getShortName(it.subjectRaw) == "Системы ЧПУ" },
        )
    }
}
