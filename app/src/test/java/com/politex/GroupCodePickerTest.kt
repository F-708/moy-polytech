package com.politex

import com.politex.ui.screens.buildGroupCode
import com.politex.ui.screens.groupPickerLetters
import com.politex.ui.screens.groupPickerMaxNumber
import com.politex.ui.screens.validateAgainstCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GroupCodePickerTest {
    private val catalog = listOf("11О", "11Д", "21Г", "41О", "41Д", "42Э")

    @Test
    fun `сборка кода группы из курса, номера и буквы`() {
        assertEquals("41О", buildGroupCode(4, 1, 'О'))
        assertEquals("11Д", buildGroupCode(1, 1, 'Д'))
    }

    @Test
    fun `латиница и lowercase нормализуются в кириллицу`() {
        assertEquals("41О", buildGroupCode(4, 1, 'o'))
        assertEquals("41О", buildGroupCode(4, 1, 'O'))
    }

    @Test
    fun `неполный ввод - null`() {
        assertNull(buildGroupCode(null, 1, 'О'))
        assertNull(buildGroupCode(4, null, 'О'))
        assertNull(buildGroupCode(4, 1, null))
    }

    @Test
    fun `выход за диапазон - null`() {
        assertNull(buildGroupCode(5, 1, 'О'))
        assertNull(buildGroupCode(0, 1, 'О'))
        assertNull(buildGroupCode(4, 0, 'О'))
        assertNull(buildGroupCode(4, 10, 'О'))
        assertNull(buildGroupCode(4, 1, '5'))
    }

    @Test
    fun `валидация по каталогу`() {
        assertTrue(validateAgainstCatalog("41О", catalog))
        assertFalse(validateAgainstCatalog("43О", catalog))
        assertFalse(validateAgainstCatalog("41", catalog))
        assertFalse(validateAgainstCatalog("", catalog))

        assertTrue(validateAgainstCatalog("4-1 О", catalog))
        assertFalse(validateAgainstCatalog("41Ы", catalog))
    }

    @Test
    fun `буквы берутся из каталога для курса и номера`() {
        assertEquals(listOf('Д', 'О'), groupPickerLetters(catalog, 4, 1))
        assertEquals(listOf('Э'), groupPickerLetters(catalog, 4, 2))
        assertTrue(groupPickerLetters(catalog, 1, null).containsAll(listOf('Д', 'О')))
    }

    @Test
    fun `пустой каталог - фолбэк на буквы специальностей`() {
        val letters = groupPickerLetters(emptyList(), 4, 1)
        assertTrue(letters.isNotEmpty())
        assertTrue('О' in letters)
    }

    @Test
    fun `максимальный номер группы для курса из каталога`() {
        assertEquals(1, groupPickerMaxNumber(catalog, 1))
        assertEquals(1, groupPickerMaxNumber(catalog, 2))
        assertEquals(2, groupPickerMaxNumber(catalog, 4))

        assertEquals(9, groupPickerMaxNumber(catalog, 3))
        assertEquals(9, groupPickerMaxNumber(catalog, null))
        assertEquals(9, groupPickerMaxNumber(emptyList(), 1))
    }

    @Test
    fun `курс и номер без групп в каталоге - буквы пустые`() {
        assertTrue(groupPickerLetters(catalog, 4, 3).isEmpty())
        assertTrue(groupPickerLetters(catalog, 3, 1).isEmpty())
    }

    @Test
    fun `ссылка всё равно открыть удалена`() {
        val src =
            sequenceOf(
                File("src/main/java/com/politex/ui/screens/GroupScheduleScreen.kt"),
                File("app/src/main/java/com/politex/ui/screens/GroupScheduleScreen.kt"),
            ).first { it.exists() }
        val text = src.readText()
        assertFalse(text.contains("group_picker_open_anyway"))
        assertFalse(text.contains("Всё равно открыть"))

        assertTrue(text.contains("нет в расписании"))
        assertTrue(text.contains("group_picker_no_groups"))
    }
}
