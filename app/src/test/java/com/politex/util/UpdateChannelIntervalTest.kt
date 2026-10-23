package com.politex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelIntervalTest {
    @Test
    fun `превью-канал проверяется каждый час`() {
        assertEquals(1L, updateCheckIntervalHours(UpdateChannel.PREVIEW))
    }

    @Test
    fun `стабильный канал проверяется каждые 12 часов`() {
        assertEquals(12L, updateCheckIntervalHours(UpdateChannel.STABLE))
    }

    @Test
    fun `смена канала меняет период проверок приложения`() {
        assertNotEquals(
            updateCheckIntervalHours(UpdateChannel.STABLE),
            updateCheckIntervalHours(UpdateChannel.PREVIEW),
        )
    }

    @Test
    fun `контент-пакет проверяется каждый час на стабильном канале`() {
        assertEquals(1L, contentCheckIntervalHours(UpdateChannel.STABLE))
    }

    @Test
    fun `контент-пакет проверяется каждый час на превью-канале`() {
        assertEquals(1L, contentCheckIntervalHours(UpdateChannel.PREVIEW))
    }

    @Test
    fun `период проверки контента не зависит от канала`() {
        assertEquals(
            contentCheckIntervalHours(UpdateChannel.STABLE),
            contentCheckIntervalHours(UpdateChannel.PREVIEW),
        )
    }

    @Test
    fun `первая постановка в расписание считается перепланировкой`() {
        assertTrue(periodicCheckNeedsReschedule(null, UpdateChannel.STABLE))
        assertTrue(periodicCheckNeedsReschedule(null, UpdateChannel.PREVIEW))
    }

    @Test
    fun `тот же канал не требует перепланировки`() {
        assertFalse(periodicCheckNeedsReschedule(UpdateChannel.STABLE.wire, UpdateChannel.STABLE))
        assertFalse(periodicCheckNeedsReschedule(UpdateChannel.PREVIEW.wire, UpdateChannel.PREVIEW))
    }

    @Test
    fun `смена канала требует перепланировки`() {
        assertTrue(periodicCheckNeedsReschedule(UpdateChannel.STABLE.wire, UpdateChannel.PREVIEW))
        assertTrue(periodicCheckNeedsReschedule(UpdateChannel.PREVIEW.wire, UpdateChannel.STABLE))
    }

    @Test
    fun `первая постановка контент-расписания считается перепланировкой`() {
        assertTrue(contentScheduleNeedsReschedule(null, contentCheckIntervalHours(UpdateChannel.STABLE)))
    }

    @Test
    fun `тот же период контента не требует перепланировки`() {
        assertFalse(contentScheduleNeedsReschedule(1L, contentCheckIntervalHours(UpdateChannel.STABLE)))
        assertFalse(contentScheduleNeedsReschedule(1L, contentCheckIntervalHours(UpdateChannel.PREVIEW)))
    }

    @Test
    fun `изменение периода контента требует перепланировки`() {
        assertTrue(contentScheduleNeedsReschedule(12L, contentCheckIntervalHours(UpdateChannel.STABLE)))
    }
}
