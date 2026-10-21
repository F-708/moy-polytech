package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.repository.ScheduleRepository
import com.politex.util.HomeworkMarkers
import com.politex.util.SubjectFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocaleRootDateTest {
    private fun withArabicLocale(body: () -> Unit) {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-u-nu-arab"))
            body()
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun `repository db date stays ascii under arabic default locale`() = withArabicLocale {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ScheduleRepository(MpkDatabase.getInstance(context).lessonDao())
        val cal = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 5) }
        val formatted = repo.formatDate(cal)
        assertEquals("05.10.2026", formatted)
        assertEquals("только ASCII-цифры и точка", formatted, formatted.filter { it.isAsciiDigitOrDot() })
    }

    @Test
    fun `homework dayIso stays ascii under arabic default locale`() = withArabicLocale {
        val cal = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 5) }
        assertEquals("2026-10-05", HomeworkMarkers.dayIsoOf(cal))
    }

    @Test
    fun `normalize cache returns same result on repeated calls`() {
        val once = SubjectFormatter.normalize("ОхрТруда ауд. 305")
        val twice = SubjectFormatter.normalize("ОхрТруда ауд. 305")
        assertEquals(once, twice)
        assertEquals("Охрана труда", once)

        assertEquals("Информатика", SubjectFormatter.normalize("Информа"))
        assertEquals("Информатика", SubjectFormatter.normalize("Информа"))
        val unknown = "Ыыыыы zzz"
        assertEquals(unknown, SubjectFormatter.normalize(unknown))
        assertEquals(unknown, SubjectFormatter.normalize(unknown))
    }

    private fun Char.isAsciiDigitOrDot(): Boolean = this == '.' || this in '0'..'9'
}
