package com.politex.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollegeMapTest {
    @Test
    fun `первая цифра номера задаёт этаж`() {
        assertEquals(CollegeMap.Floor.FIRST, CollegeMap.floorForRoom("101"))
        assertEquals(CollegeMap.Floor.FIRST, CollegeMap.floorForRoom("155"))
        assertEquals(CollegeMap.Floor.SECOND, CollegeMap.floorForRoom("214"))
        assertEquals(CollegeMap.Floor.SECOND, CollegeMap.floorForRoom("230"))
        assertEquals(CollegeMap.Floor.THIRD_FOURTH, CollegeMap.floorForRoom("312"))
        assertEquals(CollegeMap.Floor.THIRD_FOURTH, CollegeMap.floorForRoom("405"))
    }

    @Test
    fun `пробелы и мусор вокруг номера не мешают`() {
        assertEquals(CollegeMap.Floor.SECOND, CollegeMap.floorForRoom(" 214 "))
        assertEquals(CollegeMap.Floor.SECOND, CollegeMap.floorForRoom("214а"))
    }

    @Test
    fun `нестандартные кабинеты не привязываются к этажу наугад`() {
        assertNull(CollegeMap.floorForRoom(""))
        assertNull(CollegeMap.floorForRoom("спортзал"))
        assertNull(CollegeMap.floorForRoom("актовый зал"))
        assertNull(CollegeMap.floorForRoom("914"))
    }
}
