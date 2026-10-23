package com.politex

import com.politex.util.BellCountdownNotifier
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.WidgetUpdateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BellSecondsTest {

    @Test
    fun `59 секунд - MСС без ведущего нуля у минут`() {
        assertEquals("0:59", BellCountdownWidgetProvider.formatSeconds(59))
    }

    @Test
    fun `272 секунды - четыре минуты тридцать две секунды`() {
        assertEquals("4:32", BellCountdownWidgetProvider.formatSeconds(272))
    }

    @Test
    fun `3753 секунды - час с двухзначными минутами и секундами`() {
        assertEquals("1:02:33", BellCountdownWidgetProvider.formatSeconds(3753))
    }

    @Test
    fun `ровные минуты и часы`() {
        assertEquals("5:00", BellCountdownWidgetProvider.formatSeconds(300))
        assertEquals("2:00:00", BellCountdownWidgetProvider.formatSeconds(7200))
    }

    @Test
    fun `граница звонка - ноль и отрицательный остаток не ломают формат`() {
        assertEquals("0:00", BellCountdownWidgetProvider.formatSeconds(0))
        assertEquals("0:00", BellCountdownWidgetProvider.formatSeconds(-45))
    }

    @Test
    fun `минуты досчитываются от секунды календаря`() {
        assertEquals("34:15", BellCountdownNotifier.formatMinutesSeconds(35, 45))

        assertEquals("35:00", BellCountdownNotifier.formatMinutesSeconds(35, 0))
        assertEquals("0:59", BellCountdownNotifier.formatMinutesSeconds(1, 1))
    }

    @Test
    fun `нулевые минуты с секундой внутри - не уходим в минус`() {
        assertEquals("0:00", BellCountdownNotifier.formatMinutesSeconds(0, 30))
    }

    @Test
    fun `секунды в виджете включают посекундный тик при любом стиле`() {
        assertTrue(WidgetUpdateHelper.needSecondTick(true, BellCountdownNotifier.Style.COMPACT))
        assertTrue(WidgetUpdateHelper.needSecondTick(true, BellCountdownNotifier.Style.WITH_SECONDS))
        assertTrue(WidgetUpdateHelper.needSecondTick(true, BellCountdownNotifier.Style.WITH_PROGRESS))
    }

    @Test
    fun `стиль WITH_SECONDS включает посекундный тик без секунд в виджетах`() {
        assertTrue(WidgetUpdateHelper.needSecondTick(false, BellCountdownNotifier.Style.WITH_SECONDS))
    }

    @Test
    fun `минутный режим - ни секунд в виджетах, ни стиля с секундами`() {
        assertFalse(WidgetUpdateHelper.needSecondTick(false, BellCountdownNotifier.Style.COMPACT))
        assertFalse(WidgetUpdateHelper.needSecondTick(false, BellCountdownNotifier.Style.WITH_LESSON))
        assertFalse(WidgetUpdateHelper.needSecondTick(false, BellCountdownNotifier.Style.WITH_PROGRESS))
        assertFalse(WidgetUpdateHelper.needSecondTick(false, BellCountdownNotifier.Style.WITH_APP_NAME))
    }
}
