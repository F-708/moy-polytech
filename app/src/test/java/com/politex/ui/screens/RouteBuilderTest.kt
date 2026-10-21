package com.politex.ui.screens

import com.politex.data.model.CollegeMapData
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteBuilderTest {

    @Test
    fun `X36 laneOffsetDp раскладывает полосы 0 6 -6 12 -12 18`() {
        assertEquals(0f, laneOffsetDp(0), 1e-6f)
        assertEquals(6f, laneOffsetDp(1), 1e-6f)
        assertEquals(-6f, laneOffsetDp(2), 1e-6f)
        assertEquals(12f, laneOffsetDp(3), 1e-6f)
        assertEquals(-12f, laneOffsetDp(4), 1e-6f)
        assertEquals(18f, laneOffsetDp(5), 1e-6f)
    }

    @Test
    fun `Y8 две точки дают один перегон A-B`() {
        val mapData = CollegeMapData(emptyMap())
        val legs = buildLegs(listOf(Waypoint("A", "каб. A"), Waypoint("B", "каб. B")), mapData, emptyMap())
        assertEquals("пара точек — один перегон", 1, legs.size)
        assertEquals("A", legs[0].from.key)
        assertEquals("B", legs[0].to.key)
    }

    @Test
    fun `Y8 одна точка B дополняется главным входом`() {
        val mapData = CollegeMapData(emptyMap())
        val legs = buildLegs(listOf(Waypoint("B", "каб. B")), mapData, emptyMap())
        assertEquals(1, legs.size)
        assertEquals(MAIN_ENTRANCE_KEY, legs[0].from.key)
        assertEquals("B", legs[0].to.key)
    }

    @Test
    fun `X19 buildLegs схлопывает последовательный дубль`() {
        val mapData = CollegeMapData(emptyMap())
        val legs = buildLegs(listOf(Waypoint("A", "A"), Waypoint("A", "A"), Waypoint("B", "B")), mapData, emptyMap())
        assertEquals(1, legs.size)
        assertEquals("A", legs[0].from.key)
        assertEquals("B", legs[0].to.key)
    }

    @Test
    fun `X19 buildLegs A B A не даёт петли - глобальный дедуп`() {
        val mapData = CollegeMapData(emptyMap())
        val legs = buildLegs(listOf(Waypoint("A", "A"), Waypoint("B", "B"), Waypoint("A", "A")), mapData, emptyMap())
        assertEquals("A встречается один раз → один перегон, без возврата", 1, legs.size)
        assertEquals("A", legs[0].from.key)
        assertEquals("B", legs[0].to.key)
    }
}
