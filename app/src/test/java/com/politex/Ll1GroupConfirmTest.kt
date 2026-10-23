package com.politex

import com.politex.ui.screens.buildGroupCode
import com.politex.ui.screens.groupPickerLetters
import com.politex.ui.screens.groupPickerMaxNumber
import com.politex.ui.screens.validateAgainstCatalog
import com.politex.util.GroupCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Ll1GroupConfirmTest {

    private val catalog: List<String> =
        sequenceOf(
            File("src/main/assets/group_codes.csv"),
            File("app/src/main/assets/group_codes.csv"),
        ).first { it.exists() }
            .readLines()
            .let { GroupCatalog.parse(it).all() }

    @Test
    fun `41О есть в каталоге всеми восемью кодами`() {
        val codes = catalog.filter { it.startsWith("41") }
        assertEquals(8, codes.size)
        assertTrue("41О" in codes)
    }

    @Test
    fun `41О проходит валидацию кнопки подтверждения`() {
        assertEquals("41О", buildGroupCode(4, 1, 'О'))
        assertTrue(validateAgainstCatalog("41О", catalog))
        assertEquals(3, groupPickerMaxNumber(catalog, 4))
        assertTrue('О' in groupPickerLetters(catalog, 4, 1))
    }

    @Test
    fun `42О и 43О тоже проходят валидацию`() {
        assertTrue(validateAgainstCatalog("42О", catalog))
        assertTrue(validateAgainstCatalog("43О", catalog))
    }

    @Test
    fun `пробелы регистр и латиница в каталоге не блокируют кнопку`() {
        assertTrue(validateAgainstCatalog("41О", listOf(" 41о ", "41O", "42Э\r")))
        assertTrue(validateAgainstCatalog("41О", listOf("4 1 О", "42Э")))
    }
}
