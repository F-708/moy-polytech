package com.politex

import com.politex.util.GroupCatalog
import com.politex.util.GroupParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GroupCatalogTest {
    private val catalog = GroupCatalog.load(RuntimeEnvironment.getApplication())

    @Test
    fun `ассет group_codes_csv попадает в standard и содержит все группы колледжа`() {
        val all = catalog.all()

        assertEquals(59, all.size)
        assertEquals(all.distinct(), all)
        assertTrue(all.all { it.length == 3 })
    }

    @Test
    fun `новые группы 42Н 42Т 43О 43Э присутствуют`() {
        listOf("42Н", "42Т", "43О", "43Э").forEach { g ->
            assertEquals(g, catalog.find(g))
        }
    }

    @Test
    fun `выбывшие группы 12Т 13Д 13М 13О 13Э 33М 41Д 41М 42Д 42М отсутствуют`() {
        listOf("12Т", "13Д", "13М", "13О", "13Э", "33М", "41Д", "41М", "42Д", "42М").forEach { g ->
            assertNull(catalog.find(g))
        }
    }

    @Test
    fun `каталог не содержит персональных данных — только коды групп`() {
        catalog.all().forEach { code ->
            assertTrue("код группы не должен содержать пробелов: $code", !code.contains(' '))
            assertTrue("код группы не должен содержать ';': $code", !code.contains(';'))

            assertTrue(GroupParser.parse(code)?.canonicalName == code)
        }
    }

    @Test
    fun `поиск понимает регистр и латиницу`() {

        assertEquals("41С", catalog.find("41С"))
        assertEquals("41С", catalog.find("41C"))
        assertEquals("41С", catalog.find("41c"))
        assertEquals("41О", catalog.find("41о"))
        assertEquals("41О", catalog.find("41O"))
        assertEquals("41О", catalog.find("41o"))
        assertEquals("11Э", catalog.find(" 11Э "))
    }

    @Test
    fun `несуществующая группа возвращает null`() {
        assertNull(catalog.find("99Х"))
        assertNull(catalog.find(""))
        assertNull(catalog.find("41"))
        assertNull(catalog.find("абракадабра"))
    }

    @Test
    fun `normalize принимает дефисы и подчёркивания`() {
        assertEquals("41О", GroupCatalog.normalize("41-О"))
        assertEquals("41О", GroupCatalog.normalize("41_о"))
        assertEquals("41О", GroupCatalog.normalize("4 1 О"))
        assertNull(GroupCatalog.normalize("51О"))
    }
}
