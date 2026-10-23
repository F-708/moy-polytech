package com.politex.widget

import com.politex.util.ExactAlarmGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetConfigSecondsGatePredicateTest {
    private val configSource: String =
        sequenceOf(
            File("src/main/java/com/politex/widget/WidgetConfigActivity.kt"),
            File("app/src/main/java/com/politex/widget/WidgetConfigActivity.kt"),
        ).first { it.exists() }.readText()

    @Test
    fun `предикат - секунды без права на точные будильники требуют подсказки`() {
        assertTrue(
            ExactAlarmGate.secondsHintNeeded(
                sdkInt = 35,
                canScheduleExact = false,
                secondsSelected = true,
            ),
        )
        assertTrue(
            ExactAlarmGate.secondsHintNeeded(
                sdkInt = android.os.Build.VERSION_CODES.S,
                canScheduleExact = false,
                secondsSelected = true,
            ),
        )
    }

    @Test
    fun `предикат - право дано, секунды выключены или старый sdk - подсказка не нужна`() {
        assertFalse(ExactAlarmGate.secondsHintNeeded(35, true, true))
        assertFalse(ExactAlarmGate.secondsHintNeeded(35, false, false))
        assertFalse(ExactAlarmGate.secondsHintNeeded(30, false, true))
        assertFalse(ExactAlarmGate.secondsHintNeeded(26, false, false))
    }

    @Test
    fun `wiring - секундная секция конфига связана с gate`() {
        assertTrue(
            "секция секунд обязана рендериться через SecondsOptionSection",
            configSource.contains("SecondsOptionSection("),
        )
        assertTrue(
            "видимость подсказки обязана считаться через ExactAlarmGate.secondsHintNeeded",
            configSource.contains("ExactAlarmGate.secondsHintNeeded("),
        )
        assertTrue(
            "кнопка РАЗРЕШИТЬ ведёт в ExactAlarmGate.openSettings",
            configSource.contains("ExactAlarmGate.openSettings(this@WidgetConfigActivity)"),
        )
    }

    @Test
    fun `wiring - запрос права не блокирует сохранение виджета`() {
        val requestIdx = configSource.indexOf("requestAlarmAccessOnce()")
        val setResultIdx = configSource.indexOf("setResult(")
        assertTrue("запрос права отсутствует", requestIdx >= 0)
        assertTrue("setResult отсутствует", setResultIdx >= 0)
        assertTrue(
            "gate-запрос идёт до setResult/finish и не блокирует сохранение",
            requestIdx < setResultIdx,
        )
    }
}
