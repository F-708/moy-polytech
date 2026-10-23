package com.politex

import com.politex.util.GradeAggregator
import com.politex.util.GradeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeAggregatorTest {
    private val delta = 1e-9

    @Test
    fun `дефолтная формула без экзамена`() {
        val avg =
            GradeAggregator.average(
                mapOf(
                    "REGULAR" to listOf(8, 8),
                    "PRACTICAL" to listOf(7),
                    "CONTROL" to listOf(9),
                ),
            )
        assertEquals(8.25, avg!!, delta)
    }

    @Test
    fun `дефолтная формула с экзаменом`() {
        val avg =
            GradeAggregator.average(
                mapOf(
                    "REGULAR" to listOf(8),
                    "PRACTICAL" to listOf(7),
                    "CONTROL" to listOf(9),
                    "EXAM" to listOf(10),
                ),
            )
        assertEquals(9.125, avg!!, delta)
        assertEquals(9.1, GradeAggregator.round1(avg), delta)
    }

    @Test
    fun `пустая контрольная пропускается без сдвига acc`() {
        val avg =
            GradeAggregator.average(
                mapOf(
                    "REGULAR" to listOf(8),
                    "PRACTICAL" to listOf(6),
                    "CONTROL" to emptyList(),
                ),
            )
        assertEquals(7.0, avg!!, delta)

        assertEquals(
            7.0,
            GradeAggregator.average(mapOf("REGULAR" to listOf(8), "PRACTICAL" to listOf(6)))!!,
            delta,
        )
    }

    @Test
    fun `только практ — средний равен среднему категории`() {
        val avg = GradeAggregator.average(mapOf("PRACTICAL" to listOf(7, 9)))
        assertEquals(8.0, avg!!, delta)
    }

    @Test
    fun `нет оценок вовсе — null, а не ноль`() {
        assertNull(GradeAggregator.average(emptyMap()))
        assertNull(GradeAggregator.average(mapOf("REGULAR" to emptyList(), "EXAM" to emptyList())))
    }

    @Test
    fun `свой priority меняет результат сворачивания`() {
        val values = mapOf("REGULAR" to listOf(6), "CONTROL" to listOf(8), "EXAM" to listOf(10))

        assertEquals(8.5, GradeAggregator.average(values)!!, delta)

        assertEquals(
            7.5,
            GradeAggregator.average(values, listOf("EXAM", "CONTROL", "REGULAR"))!!,
            delta,
        )
    }

    @Test
    fun `LAB не входит в агрегацию даже если попал в priority`() {
        val values = mapOf("REGULAR" to listOf(8), "LAB" to listOf(0, 1))
        assertEquals(8.0, GradeAggregator.average(values)!!, delta)

        assertEquals(
            8.0,
            GradeAggregator.average(values, listOf("REGULAR", "LAB"))!!,
            delta,
        )

        assertNull(GradeAggregator.average(mapOf("LAB" to listOf(1)), listOf("LAB")))
    }

    @Test
    fun `округление и формат показа — 1 знак`() {
        assertEquals(8.3, GradeAggregator.round1(8.25), delta)
        assertEquals(9.1, GradeAggregator.round1(9.125), delta)
        assertEquals("8.3", GradeAggregator.format(8.25))
        assertEquals("7", GradeAggregator.format(7.0))
        assertEquals("—", GradeAggregator.format(null))
    }

    @Test
    fun `дефолтный priority из спеки — без LAB, порядок REGULAR PRACTICAL CONTROL EXAM`() {
        assertEquals(
            listOf(
                GradeCategory.REGULAR.name,
                GradeCategory.PRACTICAL.name,
                GradeCategory.CONTROL.name,
                GradeCategory.EXAM.name,
            ),
            GradeAggregator.DEFAULT_PRIORITY,
        )
    }

    @Test
    fun `buildTreeLevels — лестница склеивает первые два узла каждого уровня`() {
        assertEquals(
            listOf(
                listOf("REGULAR", "PRACTICAL", "CONTROL", "EXAM"),
                listOf("REGULAR+PRACTICAL", "CONTROL", "EXAM"),
                listOf("REGULAR+PRACTICAL+CONTROL", "EXAM"),
                listOf("REGULAR+PRACTICAL+CONTROL+EXAM"),
            ),
            GradeAggregator.buildTreeLevels(GradeAggregator.DEFAULT_PRIORITY),
        )
    }

    @Test
    fun `buildTreeLevels — пустые категории и LAB выпадают из дерева`() {
        assertEquals(
            listOf(listOf("REGULAR", "EXAM"), listOf("REGULAR+EXAM")),
            GradeAggregator.buildTreeLevels(
                listOf("REGULAR", "LAB", "CONTROL", "EXAM"),
                setOf("REGULAR", "EXAM", "LAB"),
            ),
        )
    }

    @Test
    fun `buildTreeLevels — один лист и совсем пусто`() {
        assertEquals(listOf(listOf("PRACTICAL")), GradeAggregator.buildTreeLevels(listOf("PRACTICAL")))
        assertEquals(emptyList<String>(), GradeAggregator.buildTreeLevels(listOf("REGULAR"), emptySet()))
        assertEquals(emptyList<String>(), GradeAggregator.buildTreeLevels(emptyList()))
    }

    @Test
    fun `buildTreeLevels — порядок узлов зеркалит порядок сворачивания average`() {
        val priority = listOf("EXAM", "CONTROL", "REGULAR")
        assertEquals(
            listOf(
                listOf("EXAM", "CONTROL", "REGULAR"),
                listOf("EXAM+CONTROL", "REGULAR"),
                listOf("EXAM+CONTROL+REGULAR"),
            ),
            GradeAggregator.buildTreeLevels(priority),
        )

        assertEquals(
            GradeAggregator.average(mapOf("EXAM" to listOf(10), "CONTROL" to listOf(8), "REGULAR" to listOf(6)), priority)!!,
            GradeAggregator.average(
                mapOf("EXAM" to listOf(10), "CONTROL" to listOf(8), "REGULAR" to listOf(6)),
                GradeAggregator.buildTreeLevels(priority).last().single().split("+"),
            )!!,
            delta,
        )
    }

    @Test
    fun `weightedAverage — равные веса это обычное среднее`() {
        val avg =
            GradeAggregator.weightedAverage(
                mapOf("REGULAR" to listOf(8), "EXAM" to listOf(10)),
                mapOf("REGULAR" to 50, "EXAM" to 50),
            )
        assertEquals(9.0, avg!!, delta)
    }

    @Test
    fun `weightedAverage — неравные веса тянут балл`() {
        val avg =
            GradeAggregator.weightedAverage(
                mapOf("REGULAR" to listOf(8), "EXAM" to listOf(6)),
                mapOf("REGULAR" to 70, "EXAM" to 30),
            )
        assertEquals(7.4, avg!!, delta)
    }

    @Test
    fun `weightedAverage — пустые категории и нулевые веса не входят в знаменатель`() {
        val avg =
            GradeAggregator.weightedAverage(
                mapOf(
                    "REGULAR" to listOf(8),
                    "PRACTICAL" to listOf(2),
                    "CONTROL" to emptyList(),
                    "EXAM" to listOf(10),
                ),
                mapOf("REGULAR" to 50, "PRACTICAL" to 0, "CONTROL" to 40, "EXAM" to 50),
            )
        assertEquals(9.0, avg!!, delta)

        assertEquals(
            8.0,
            GradeAggregator.weightedAverage(mapOf("REGULAR" to listOf(8), "EXAM" to listOf(10)), mapOf("REGULAR" to 1))!!,
            delta,
        )
    }

    @Test
    fun `weightedAverage — все веса нулевые или нет оценок = null`() {
        assertNull(GradeAggregator.weightedAverage(mapOf("REGULAR" to listOf(8)), mapOf("REGULAR" to 0)))
        assertNull(GradeAggregator.weightedAverage(mapOf("REGULAR" to listOf(8)), emptyMap()))
        assertNull(GradeAggregator.weightedAverage(emptyMap(), mapOf("REGULAR" to 50)))
    }

    @Test
    fun `weightedAverage — LAB не участвует никогда`() {
        assertNull(GradeAggregator.weightedAverage(mapOf("LAB" to listOf(1)), mapOf("LAB" to 100)))
        assertEquals(
            8.0,
            GradeAggregator.weightedAverage(
                mapOf("REGULAR" to listOf(8), "LAB" to listOf(0)),
                mapOf("REGULAR" to 50, "LAB" to 50),
            )!!,
            delta,
        )
    }

    @Test
    fun `roundAvg — _0 стандартное школьное HALF_UP`() {
        assertEquals(8.3, GradeAggregator.roundAvg(8.25, ".0"), delta)
        assertEquals(8.2, GradeAggregator.roundAvg(8.24, ".0"), delta)
        assertEquals(7.0, GradeAggregator.roundAvg(7.0, ".0"), delta)
        assertEquals(8.0, GradeAggregator.roundAvg(8.04, ".0"), delta)
        assertEquals(8.1, GradeAggregator.roundAvg(8.05, ".0"), delta)
    }

    @Test
    fun `roundAvg — _00 два знака и целое`() {
        assertEquals(8.26, GradeAggregator.roundAvg(8.255, ".00"), delta)
        assertEquals(8.25, GradeAggregator.roundAvg(8.254, ".00"), delta)
        assertEquals(8.0, GradeAggregator.roundAvg(8.4, "целое"), delta)
        assertEquals(9.0, GradeAggregator.roundAvg(8.5, "целое"), delta)
    }

    @Test
    fun `roundAvg — неизвестный и legacy mode = 1 знак`() {
        assertEquals(8.3, GradeAggregator.roundAvg(8.25, "garbage"), delta)
        assertEquals(8.3, GradeAggregator.roundAvg(8.25, "0.1"), delta)
    }

    @Test
    fun `plainAverage — все оценки всех категорий одним средним`() {
        assertEquals(
            8.25,
            GradeAggregator.plainAverage(mapOf("REGULAR" to listOf(8, 6), "EXAM" to listOf(9, 10)))!!,
            delta,
        )
    }

    @Test
    fun `plainAverage — LAB не участвует, нет оценок = null`() {
        assertEquals(8.0, GradeAggregator.plainAverage(mapOf("REGULAR" to listOf(8), "LAB" to listOf(0, 1)))!!, delta)
        assertNull(GradeAggregator.plainAverage(emptyMap()))
        assertNull(GradeAggregator.plainAverage(mapOf("REGULAR" to emptyList())))
    }

    @Test
    fun `X9 — дефолт весов Обычн 25 Практ 25 Контр 50 Экз 100`() {
        assertEquals(listOf(25, 25, 50, 100), GradeAggregator.DEFAULT_WEIGHTS)

        val avg =
            GradeAggregator.weightedAverage(
                mapOf("REGULAR" to listOf(8), "PRACTICAL" to listOf(7), "CONTROL" to listOf(9), "EXAM" to listOf(10)),
                GradeAggregator.DEFAULT_PRIORITY.zip(GradeAggregator.DEFAULT_WEIGHTS).toMap(),
            )
        assertEquals(9.125, avg!!, delta)
    }

    @Test
    fun `X9 — snapWeight шаг 25, пять позиций, края clamp`() {
        assertEquals(0, GradeAggregator.snapWeight(0f))
        assertEquals(0, GradeAggregator.snapWeight(0.1f))
        assertEquals(25, GradeAggregator.snapWeight(0.15f))
        assertEquals(50, GradeAggregator.snapWeight(0.5f))
        assertEquals(100, GradeAggregator.snapWeight(1f))
        assertEquals(100, GradeAggregator.snapWeight(3f))
        assertEquals(0, GradeAggregator.snapWeight(-1f))
    }

    @Test
    fun `X8 — format учитывает режим округления`() {
        assertEquals("8.25", GradeAggregator.format(8.25, ".00"))
        assertEquals("7.00", GradeAggregator.format(7.0, ".00"))
        assertEquals("9", GradeAggregator.format(8.6, "целое"))
        assertEquals("8.3", GradeAggregator.format(8.25, ".0"))
        assertEquals("8.3", GradeAggregator.format(8.25))
        assertEquals("—", GradeAggregator.format(null, ".00"))
    }
}
