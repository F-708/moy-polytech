package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.ui.screens.ScheduleLessonSlot
import com.politex.ui.screens.canMergeLessons
import com.politex.ui.screens.canMergeSplitLessons
import com.politex.ui.screens.mergedCardContent
import com.politex.ui.screens.mergedLessonSlots
import com.politex.ui.screens.scheduleSlotIndexOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLessonMergeTest {
    private fun lesson(
        number: Int,
        subject: String = "Математика",
        teacher: String = "Ковалев В.П.",
        room: String = "204",
        group: String = "41О",
        dow: Int = 2,
        date: String = "",
        split: Boolean = false,
        room2: String = if (split) "118" else "",
        teacher2: String = if (split) "Смирнов А.В." else "",
        timeStart: String = "08:15",
        timeEnd: String = "09:55",
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = dow,
        lessonNumber = number,
        timeStart = timeStart,
        timeEnd = timeEnd,
        subjectRaw = subject,
        roomFirst = room,
        teacherFirst = teacher,
        roomSecond = room2,
        teacherSecond = teacher2,
        isSplit = split,
        dateString = date,
    )

    @Test
    fun `одинаковые подряд уроки объединяются`() {
        assertTrue(canMergeLessons(lesson(1), lesson(2), teacherMode = false))
    }

    @Test
    fun `разный кабинет не объединяется`() {
        assertFalse(canMergeLessons(lesson(1), lesson(2, room = "118"), teacherMode = false))
    }

    @Test
    fun `разный преподаватель не объединяется`() {
        assertFalse(canMergeLessons(lesson(1), lesson(2, teacher = "Смирнов А.В."), teacherMode = false))
    }

    @Test
    fun `разный предмет не объединяется`() {
        assertFalse(canMergeLessons(lesson(1), lesson(2, subject = "Физика"), teacherMode = false))
    }

    @Test
    fun `предмет сравнивается после normalize`() {
        assertTrue(canMergeLessons(lesson(1, subject = "Математика"), lesson(2, subject = "математика"), teacherMode = false))
    }

    @Test
    fun `пустой кабинет не объединяется`() {
        assertFalse(canMergeLessons(lesson(1, room = ""), lesson(2, room = ""), teacherMode = false))
    }

    @Test
    fun `пустой преподаватель не объединяется`() {
        assertFalse(canMergeLessons(lesson(1, teacher = ""), lesson(2, teacher = ""), teacherMode = false))
    }

    @Test
    fun `не подряд по номеру не объединяется`() {
        assertFalse(canMergeLessons(lesson(1), lesson(3), teacherMode = false))
        assertFalse(canMergeLessons(lesson(2), lesson(1), teacherMode = false))
    }

    @Test
    fun `сплит уроки не объединяются`() {
        assertFalse(canMergeLessons(lesson(1, split = true), lesson(2), teacherMode = false))
        assertFalse(canMergeLessons(lesson(1), lesson(2, split = true), teacherMode = false))
    }

    @Test
    fun `разные даты не объединяются`() {
        assertFalse(canMergeLessons(lesson(1, date = "20.10.2026"), lesson(2, date = ""), teacherMode = false))
        assertFalse(canMergeLessons(lesson(1, dow = 1), lesson(2, dow = 2), teacherMode = false))
    }

    @Test
    fun `teacher режим объединяет по группе а не по преподавателю`() {
        val a = lesson(1, teacher = "Иванов И.И.", group = "41О")
        val b = lesson(2, teacher = "Петров П.П.", group = "41О")
        assertFalse(canMergeLessons(a, b, teacherMode = false))
        assertTrue(canMergeLessons(a, b, teacherMode = true))
        assertFalse(canMergeLessons(a, lesson(2, teacher = "Иванов И.И.", group = "42О"), teacherMode = true))
        assertFalse(canMergeLessons(a, lesson(2, teacher = "Иванов И.И.", group = ""), teacherMode = true))
    }

    @Test
    fun `слоты - пустой список даёт пустой результат`() {
        assertTrue(mergedLessonSlots(emptyList(), teacherMode = false).isEmpty())
    }

    @Test
    fun `слоты - одиночный урок`() {
        val slots = mergedLessonSlots(listOf(lesson(1)), teacherMode = false)
        assertEquals(listOf(ScheduleLessonSlot.Single(lesson(1))), slots)
    }

    @Test
    fun `слоты - три одинаковых урока дают один merged run`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(2), lesson(3)), teacherMode = false)
        assertEquals(1, slots.size)
        assertTrue(slots[0] is ScheduleLessonSlot.Merged)
        assertEquals(1, slots[0].key)
        assertEquals(
            listOf(1, 2, 3),
            (slots[0] as ScheduleLessonSlot.Merged).lessons.map { it.lessonNumber },
        )
    }

    @Test
    fun `слоты - run из двух и затем другой урок`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(2), lesson(3, room = "118")), teacherMode = false)
        assertEquals(2, slots.size)
        assertTrue(slots[0] is ScheduleLessonSlot.Merged)
        assertEquals(listOf(1, 2), (slots[0] as ScheduleLessonSlot.Merged).lessons.map { it.lessonNumber })
        assertEquals(ScheduleLessonSlot.Single(lesson(3, room = "118")), slots[1])
    }

    @Test
    fun `слоты - несовместимый урок разрывает run`() {
        val slots =
            mergedLessonSlots(
                listOf(lesson(1), lesson(2, room = "118"), lesson(3)),
                teacherMode = false,
            )
        assertEquals(3, slots.size)
        assertTrue(slots.all { it is ScheduleLessonSlot.Single })
    }

    @Test
    fun `слоты - два run подряд`() {
        val slots =
            mergedLessonSlots(
                listOf(lesson(1), lesson(2), lesson(3, room = "118"), lesson(4, room = "118")),
                teacherMode = false,
            )
        assertEquals(2, slots.size)
        assertEquals(listOf(1, 2), (slots[0] as ScheduleLessonSlot.Merged).lessons.map { it.lessonNumber })
        assertEquals(listOf(3, 4), (slots[1] as ScheduleLessonSlot.Merged).lessons.map { it.lessonNumber })
    }

    @Test
    fun `слоты - неодинаковые соседи не объединяются`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(2, room = "118")), teacherMode = false)
        assertEquals(
            listOf(
                ScheduleLessonSlot.Single(lesson(1)),
                ScheduleLessonSlot.Single(lesson(2, room = "118")),
            ),
            slots,
        )
    }

    @Test
    fun `слоты - дырки сохраняются как Empty`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(3, room = "118")), teacherMode = false)
        assertEquals(
            listOf(
                ScheduleLessonSlot.Single(lesson(1)),
                ScheduleLessonSlot.Empty(2),
                ScheduleLessonSlot.Single(lesson(3, room = "118")),
            ),
            slots,
        )
    }

    @Test
    fun `слоты - слияние через отсутствующий номер невозможно`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(3)), teacherMode = false)
        assertEquals(3, slots.size)
        assertTrue(slots[0] is ScheduleLessonSlot.Single)
        assertTrue(slots[1] is ScheduleLessonSlot.Empty)
        assertTrue(slots[2] is ScheduleLessonSlot.Single)
    }

    @Test
    fun `слоты - пара в конце списка`() {
        val slots = mergedLessonSlots(listOf(lesson(1, room = "118"), lesson(2), lesson(3)), teacherMode = false)
        assertEquals(2, slots.size)
        assertTrue(slots[0] is ScheduleLessonSlot.Single)
        assertTrue(slots[1] is ScheduleLessonSlot.Merged)
        assertEquals(2, slots[1].key)
    }

    @Test
    fun `слоты - teacher режим объединяет одинаковые группы`() {
        val a = lesson(1, teacher = "Иванов И.И.")
        val b = lesson(2, teacher = "Петров П.П.")
        assertEquals(2, mergedLessonSlots(listOf(a, b), teacherMode = false).size)
        val merged = mergedLessonSlots(listOf(a, b), teacherMode = true)
        assertEquals(1, merged.size)
        assertTrue(merged[0] is ScheduleLessonSlot.Merged)
    }

    @Test
    fun `slotIndexOf - находит оба урока пары и границы`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(2), lesson(4, room = "118")), teacherMode = false)
        assertEquals(0, scheduleSlotIndexOf(slots, 1))
        assertEquals(0, scheduleSlotIndexOf(slots, 2))
        assertEquals(2, scheduleSlotIndexOf(slots, 4))
        assertNull(scheduleSlotIndexOf(slots, 3))
        assertNull(scheduleSlotIndexOf(slots, 9))
    }

    @Test
    fun `сплит пара объединяется отдельным предикатом`() {
        val first = lesson(1, split = true)
        val second = lesson(2, split = true)
        assertFalse(canMergeLessons(first, second, teacherMode = false))
        assertTrue(canMergeSplitLessons(first, second, teacherMode = false))
    }

    @Test
    fun `сплит пара с разным кабинетом второй подгруппы не объединяется`() {
        assertFalse(
            canMergeSplitLessons(
                lesson(1, split = true),
                lesson(2, split = true, room2 = "120"),
                teacherMode = false,
            ),
        )
    }

    @Test
    fun `сплит пара с разным преподавателем второй подгруппы не объединяется`() {
        assertFalse(
            canMergeSplitLessons(
                lesson(1, split = true),
                lesson(2, split = true, teacher2 = "Иванов И.И."),
                teacherMode = false,
            ),
        )
    }

    @Test
    fun `сплит пара с общей аудиторией и преподавателем объединяется`() {
        assertTrue(
            canMergeSplitLessons(
                lesson(1, split = true, room2 = "", teacher2 = ""),
                lesson(2, split = true, room2 = "", teacher2 = ""),
                teacherMode = false,
            ),
        )
    }

    @Test
    fun `сплит и обычный урок не объединяются`() {
        assertFalse(canMergeSplitLessons(lesson(1, split = true), lesson(2), teacherMode = false))
        assertFalse(canMergeSplitLessons(lesson(1), lesson(2, split = true), teacherMode = false))
    }

    @Test
    fun `сплит пара в teacher режиме объединяется по группе`() {
        val first = lesson(1, split = true, group = "41О", teacher = "Иванов И.И.", teacher2 = "Петров П.П.")
        val second = lesson(2, split = true, group = "41О", teacher = "Сидоров С.С.", teacher2 = "Смирнов А.В.")
        assertTrue(canMergeSplitLessons(first, second, teacherMode = true))
        assertFalse(canMergeSplitLessons(first, lesson(2, split = true, group = "42О"), teacherMode = true))
    }

    @Test
    fun `слоты - сплит пара даёт один merged слот`() {
        val slots = mergedLessonSlots(listOf(lesson(1, split = true), lesson(2, split = true)), teacherMode = false)
        assertEquals(1, slots.size)
        assertTrue(slots[0] is ScheduleLessonSlot.Merged)
        assertEquals(1, slots[0].key)
    }

    @Test
    fun `слоты - split run из трёх уроков даёт один merged`() {
        val slots =
            mergedLessonSlots(
                listOf(
                    lesson(1, split = true),
                    lesson(2, split = true),
                    lesson(3, split = true),
                ),
                teacherMode = false,
            )
        assertEquals(1, slots.size)
        assertEquals(
            listOf(1, 2, 3),
            (slots[0] as ScheduleLessonSlot.Merged).lessons.map { it.lessonNumber },
        )
    }

    @Test
    fun `slotIndexOf - находит любой урок run`() {
        val slots =
            mergedLessonSlots(
                listOf(lesson(1), lesson(2), lesson(3), lesson(5, room = "118")),
                teacherMode = false,
            )
        assertEquals(0, scheduleSlotIndexOf(slots, 1))
        assertEquals(0, scheduleSlotIndexOf(slots, 2))
        assertEquals(0, scheduleSlotIndexOf(slots, 3))
        assertEquals(2, scheduleSlotIndexOf(slots, 5))
        assertNull(scheduleSlotIndexOf(slots, 4))
    }

    @Test
    fun `layout - merged rows строятся по порядку номеров и времени`() {
        val slot =
            mergedLessonSlots(
                listOf(
                    lesson(1, timeStart = "08:15", timeEnd = "09:55"),
                    lesson(2, timeStart = "10:05", timeEnd = "11:45"),
                    lesson(3, timeStart = "12:15", timeEnd = "13:55"),
                ),
                teacherMode = false,
            ).single() as ScheduleLessonSlot.Merged
        val content = mergedCardContent(slot.lessons)!!
        assertEquals(listOf(1, 2, 3), content.headers.map { it.lessonNumber })
        assertEquals(listOf("08:15", "10:05", "12:15"), content.headers.map { it.timeStart })
        assertEquals(listOf("09:55", "11:45", "13:55"), content.headers.map { it.timeEnd })
    }

    @Test
    fun `layout - merged предмет выводится один раз`() {
        val content = mergedCardContent(listOf(lesson(1), lesson(2)))!!
        assertEquals("Математика", content.subjectRaw)
        assertEquals(listOf(1, 2), content.lessonNumbers)
        assertFalse(content.split)
        assertEquals("Ковалев В.П.", content.teacherFirst)
        assertEquals("204", content.roomFirst)
    }

    @Test
    fun `layout - merged split открывает панели подгрупп`() {
        val content = mergedCardContent(listOf(lesson(1, split = true), lesson(2, split = true)))!!
        assertTrue(content.split)
        assertEquals("Математика", content.subjectRaw)
        assertEquals("118", content.roomSecond)
        assertEquals("Смирнов А.В.", content.teacherSecond)
    }

    @Test
    fun `layout - compact numbers идут в ряд`() {
        val slot =
            mergedLessonSlots(
                listOf(lesson(1), lesson(2), lesson(3)),
                teacherMode = false,
            ).single() as ScheduleLessonSlot.Merged
        val content = mergedCardContent(slot.lessons)!!
        assertEquals(listOf(1, 2, 3), content.lessonNumbers)
        assertEquals(3, content.headers.size)
        assertEquals(listOf(1, 2, 3), content.headers.map { it.lessonNumber })
    }

    @Test
    fun `layout - пустой merged список без контента`() {
        assertNull(mergedCardContent(emptyList()))
    }
}
