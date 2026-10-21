package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.ScheduleDisplayPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleDisplayPrefsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `по умолчанию компактный режим выключен`() {
        assertFalse(ScheduleDisplayPrefs.isCompact(context))
    }

    @Test
    fun `set-get roundtrip - true и false сохраняются`() {
        ScheduleDisplayPrefs.setCompact(context, true)
        assertTrue(ScheduleDisplayPrefs.isCompact(context))

        ScheduleDisplayPrefs.setCompact(context, false)
        assertFalse(ScheduleDisplayPrefs.isCompact(context))
    }

    @Test
    fun `revision растёт при каждой записи - compose перечитает значение`() {
        val before = ScheduleDisplayPrefs.revision.intValue

        ScheduleDisplayPrefs.setCompact(context, true)
        val afterFirst = ScheduleDisplayPrefs.revision.intValue
        assertTrue(afterFirst > before)

        ScheduleDisplayPrefs.setCompact(context, false)
        assertTrue(ScheduleDisplayPrefs.revision.intValue > afterFirst)
    }
}
