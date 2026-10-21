package com.politex

import com.politex.util.BellTimerService
import com.politex.widget.WidgetAlarm
import com.politex.worker.ScheduleCheckWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R3WidgetFixesTest {

    private fun nowNextChip(id: Int, slot: Int, roomIdx: Int = 0) = 1_000_000 + id * 100 + slot * 10 + roomIdx
    private fun todayChip(id: Int, lesson: Int, slot: Int = 0) = 2_000_000 + id * 100 + lesson * 2 + slot
    private fun reminder(id: Long) = (3_000_000 + id % 1_000_000).toInt()
    private fun openApp(id: Int) = 4_000_000 + id

    @Test
    fun `CRIT2 cancelIfNoWork - отменяем только когда виджетов не осталось`() {
        assertFalse("при живых виджетах отменять нельзя", WidgetAlarm.shouldCancelAlarm(hasWork = true))
        assertTrue("когда виджетов нет - отменяем", WidgetAlarm.shouldCancelAlarm(hasWork = false))
    }

    @Test
    fun `SRED5 минутная граница срабатывает один раз на смену минуты`() {
        assertTrue(BellTimerService.isMinuteBoundary(nowMs = 60_000L, lastMinute = 0L))
        assertFalse(BellTimerService.isMinuteBoundary(nowMs = 60_500L, lastMinute = 1L))
        assertFalse(BellTimerService.isMinuteBoundary(nowMs = 119_999L, lastMinute = 1L))
        assertTrue(BellTimerService.isMinuteBoundary(nowMs = 120_000L, lastMinute = 1L))
    }

    @Test
    fun `SRED5 неинициализированный lastMinute даёт границу на первом тике`() {
        assertTrue(BellTimerService.isMinuteBoundary(nowMs = 0L, lastMinute = -1L))
    }

    @Test
    fun `SRED10 staleMarkKeys удаляет notified_-checked_ ключи старше окна`() {
        val now = 1_700_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ROOT)
        val oldDate = fmt.format(java.util.Date(now - 10 * day))
        val freshDate = fmt.format(java.util.Date(now - 2 * day))
        val keys =
            setOf(
                "notified_41О_$oldDate",
                "checked_41О_$oldDate",
                "notified_41О_$freshDate",
                "bell_notification_enabled",
                "widget_style_5",
            )
        val stale = ScheduleCheckWorker.staleMarkKeys(keys, now)
        assertEquals(
            "ровно два старых mark-ключа",
            setOf("notified_41О_$oldDate", "checked_41О_$oldDate"),
            stale.toSet(),
        )
        assertFalse("свежий ключ не трогаем", stale.contains("notified_41О_$freshDate"))
    }

    @Test
    fun `SRED10 staleMarkKeys на границе окна - 6 дней живо, 8 дней удаляется`() {
        val now = 1_700_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ROOT)
        val sixDaysAgo = fmt.format(java.util.Date(now - 6 * day))
        val eightDaysAgo = fmt.format(java.util.Date(now - 8 * day))
        val keys = setOf("notified_41О_$sixDaysAgo", "checked_41О_$eightDaysAgo")
        val stale = ScheduleCheckWorker.staleMarkKeys(keys, now)
        assertEquals(setOf("checked_41О_$eightDaysAgo"), stale.toSet())
    }

    @Test
    fun `SRED11 неймспейсы занимают непересекающиеся диапазоны`() {
        assertTrue(nowNextChip(0, 1) >= 1_000_000)
        assertTrue(nowNextChip(9_999, 2, 1) < 2_000_000)
        assertTrue(todayChip(0, 0) >= 2_000_000)
        assertTrue(todayChip(9_999, 12, 1) < 3_000_000)
        assertTrue(reminder(0L) >= 3_000_000)
        assertTrue(reminder(Long.MAX_VALUE) < 4_000_000)
        assertTrue(openApp(0) >= 4_000_000)
    }

    @Test
    fun `SRED11 таблица - нет коллизий между категориями на реалистичных id`() {
        val seen = HashMap<Int, String>()
        fun put(code: Int, tag: String) {
            val prev = seen.put(code, tag)
            assertTrue("коллизия requestCode=$code между '$prev' и '$tag'", prev == null)
        }
        for (id in 1..50) {
            put(openApp(id), "openApp#$id")
            for (slot in 1..2) {
                for (roomIdx in 0..1) put(nowNextChip(id, slot, roomIdx), "nowNext#$id.$slot.$roomIdx")
            }
            for (lesson in 1..12) {
                for (slot in 0..1) put(todayChip(id, lesson, slot), "today#$id.$lesson.$slot")
            }
            put(reminder(id.toLong()), "reminder#$id")
        }
    }
}
