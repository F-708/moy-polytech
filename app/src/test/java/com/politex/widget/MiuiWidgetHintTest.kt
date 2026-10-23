package com.politex.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiuiWidgetHintTest {

    @Test
    fun `xiaomi-бренды распознаются без учёта регистра и пробелов`() {
        assertTrue(MiuiWidgetHint.isXiaomi("Xiaomi"))
        assertTrue(MiuiWidgetHint.isXiaomi("xiaomi"))
        assertTrue(MiuiWidgetHint.isXiaomi(" XIAOMI "))
        assertTrue(MiuiWidgetHint.isXiaomi("Redmi"))
        assertTrue(MiuiWidgetHint.isXiaomi("POCO"))
        assertTrue(MiuiWidgetHint.isXiaomi("BlackShark"))
    }

    @Test
    fun `чужие производители и null не xiaomi`() {
        assertFalse(MiuiWidgetHint.isXiaomi("samsung"))
        assertFalse(MiuiWidgetHint.isXiaomi("google"))
        assertFalse(MiuiWidgetHint.isXiaomi(""))
        assertFalse(MiuiWidgetHint.isXiaomi(null))
    }

    @Test
    fun `подсказка показывается на xiaomi без разрешений и только один раз`() {
        assertTrue(MiuiWidgetHint.shouldShow("Xiaomi", canExactAlarms = false, ignoringBatteryOptimizations = true, alreadyShown = false))
        assertTrue(MiuiWidgetHint.shouldShow("Xiaomi", canExactAlarms = true, ignoringBatteryOptimizations = false, alreadyShown = false))
        assertFalse(
            "уже показанную подсказку не повторяем",
            MiuiWidgetHint.shouldShow("Xiaomi", canExactAlarms = false, ignoringBatteryOptimizations = false, alreadyShown = true),
        )
    }

    @Test
    fun `подсказка не показывается когда разрешения даны или устройство не xiaomi`() {
        assertFalse(MiuiWidgetHint.shouldShow("Xiaomi", canExactAlarms = true, ignoringBatteryOptimizations = true, alreadyShown = false))
        assertFalse(MiuiWidgetHint.shouldShow("samsung", canExactAlarms = false, ignoringBatteryOptimizations = false, alreadyShown = false))
        assertFalse(MiuiWidgetHint.shouldShow(null, canExactAlarms = false, ignoringBatteryOptimizations = false, alreadyShown = false))
    }
}
