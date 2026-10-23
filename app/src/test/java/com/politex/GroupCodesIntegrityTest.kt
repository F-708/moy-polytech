package com.politex

import com.politex.ui.screens.buildGroupCode
import com.politex.ui.screens.groupPickerLetters
import com.politex.ui.screens.groupPickerMaxNumber
import com.politex.ui.screens.validateAgainstCatalog
import com.politex.util.GroupCatalog
import com.politex.util.GroupParser
import com.politex.util.MpkCurriculum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GroupCodesIntegrityTest {

    private val packaged = GroupCatalog.load(RuntimeEnvironment.getApplication()).all()

    private val diskLines: List<String>
        get() =
            sequenceOf(
                File("app/src/main/assets/group_codes.csv"),
                File("src/main/assets/group_codes.csv"),
            ).first { it.exists() }.readLines(Charsets.UTF_8)

    @Test
    fun `упакованный ассет совпадает с файлом group_codes_csv`() {
        val disk = diskLines.map { it.trim() }.filter { it.isNotBlank() }
        assertEquals(disk, packaged)
    }

    @Test
    fun `все коды групп валидны и каноничны`() {
        packaged.forEach { code ->
            assertEquals(3, code.length)
            assertEquals(code, GroupCatalog.normalize(code))
            assertEquals(code, GroupParser.parse(code)?.canonicalName)
        }
    }

    @Test
    fun `коды уникальны и отсортированы`() {
        assertEquals(packaged.distinct(), packaged)
        assertEquals(packaged.sorted(), packaged)
    }

    @Test
    fun `каталог содержит 59 групп - 43О и 43Э присутствуют`() {
        assertEquals(59, packaged.size)
        assertTrue("43О" in packaged)
        assertTrue("43Э" in packaged)
        val catalog = GroupCatalog.load(RuntimeEnvironment.getApplication())
        assertEquals("43О", catalog.find("43О"))
        assertEquals("43Э", catalog.find("43Э"))
    }

    @Test
    fun `43Э - 4 курс специальности Э, у которой 4 курса существует`() {
        val info = GroupParser.parse("43Э")
        assertEquals(4, info?.course)
        assertEquals(3, info?.groupNumber)
        assertEquals('Э', info?.specialtyCode)
        assertEquals("43Э", info?.canonicalName)

        val maxCourse = MpkCurriculum.getSpecialty('Э')?.subjectsByCourse?.keys?.maxOrNull()
        assertEquals(4, maxCourse)
    }

    @Test
    fun `каскадный пикер на 4 курсе номере 3 предлагает 43О и 43Э`() {
        assertEquals(3, groupPickerMaxNumber(packaged, 4))
        assertEquals(listOf('О', 'Э'), groupPickerLetters(packaged, 4, 3))

        assertEquals("43О", buildGroupCode(4, 3, 'О'))
        assertEquals("43Э", buildGroupCode(4, 3, 'Э'))
        assertTrue(validateAgainstCatalog("43О", packaged))
        assertTrue(validateAgainstCatalog("43Э", packaged))
    }

    @Test
    fun `группы 23Д 23М 23О присутствуют и доступны в пикере на 2 курсе номере 3`() {
        listOf("23Д", "23М", "23О").forEach { g ->
            assertTrue("$g должен быть в каталоге", g in packaged)
        }

        assertEquals(3, groupPickerMaxNumber(packaged, 2))
        assertEquals(listOf('Д', 'М', 'О', 'Э'), groupPickerLetters(packaged, 2, 3))

        assertEquals("23Д", buildGroupCode(2, 3, 'Д'))
        assertEquals("23М", buildGroupCode(2, 3, 'М'))
        assertEquals("23О", buildGroupCode(2, 3, 'О'))
        assertTrue(validateAgainstCatalog("23Д", packaged))
        assertTrue(validateAgainstCatalog("23М", packaged))
        assertTrue(validateAgainstCatalog("23О", packaged))
    }

    @Test
    fun `специальности Д М О известны куррикулуму на 2 курсе`() {
        listOf('Д', 'М', 'О').forEach { spec ->
            val subjects = MpkCurriculum.getSubjects(spec, 2)
            assertTrue("для специальности $spec курс 2 не должен быть пустым", subjects.isNotEmpty())
        }
    }

    @Test
    fun `групп 13Д 13М 13О 13Э больше нет - 1 курс доходит только до номера 2`() {
        assertTrue(packaged.none { it.startsWith("13") })
        assertEquals(2, groupPickerMaxNumber(packaged, 1))
        assertTrue(groupPickerLetters(packaged, 1, 3).isEmpty())
        assertFalse(validateAgainstCatalog("13Д", packaged))
        assertFalse(validateAgainstCatalog("13Э", packaged))
    }
}
