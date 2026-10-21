package com.politex

import com.politex.ui.components.firstRunSubgroupOptions
import com.politex.util.SubgroupPref
import com.politex.util.SubgroupSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunSubgroupStepTest {

    @Test
    fun `опции шага подгруппы - Первая Вторая Нейтрально`() {
        assertEquals(
            listOf(SubgroupPref.FIRST, SubgroupPref.SECOND, SubgroupPref.NEUTRAL),
            firstRunSubgroupOptions.map { it.first },
        )
        assertEquals(
            listOf("Первая", "Вторая", "Нейтрально"),
            firstRunSubgroupOptions.map { it.second },
        )
        assertEquals(3, firstRunSubgroupOptions.distinctBy { it.first }.size)
    }

    @Test
    fun `каждый SubgroupPref представлен в опциях первого запуска`() {
        SubgroupPref.entries.forEach { pref ->
            assertTrue(firstRunSubgroupOptions.any { it.first == pref })
        }
    }

    @Test
    fun `нейтральная подгруппа - дефолт при пустом или битом сохранённом значении`() {
        assertEquals(SubgroupPref.NEUTRAL, SubgroupPref.from(null))
        assertEquals(SubgroupPref.NEUTRAL, SubgroupPref.from(""))
        assertEquals(SubgroupPref.NEUTRAL, SubgroupPref.from("мусор"))
    }

    @Test
    fun `pref парсится из сохранённого id`() {
        assertEquals(SubgroupPref.FIRST, SubgroupPref.from("FIRST"))
        assertEquals(SubgroupPref.SECOND, SubgroupPref.from("SECOND"))
        assertEquals(SubgroupPref.NEUTRAL, SubgroupPref.from("NEUTRAL"))
    }

    @Test
    fun `индекс разреза списков для подгрупп`() {
        assertEquals(0, SubgroupSettings.splitIndex(SubgroupPref.FIRST))
        assertEquals(1, SubgroupSettings.splitIndex(SubgroupPref.SECOND))
        assertNull(SubgroupSettings.splitIndex(SubgroupPref.NEUTRAL))
    }
}
