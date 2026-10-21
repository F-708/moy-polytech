package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.SubjectFormatter
import com.politex.util.SubjectRenameStore
import com.politex.widget.WidgetUpdateHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubjectRenameReactiveTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SubjectRenameStore.clearAll(context)
    }

    @After
    fun tearDown() {
        SubjectRenameStore.clearAll(context)
    }

    @Test
    fun `bump revision меняет выдачу getShortName - именно её перечитывает UI`() {
        val raw = "Математика"
        val before = SubjectFormatter.getShortName(raw)
        val revBefore = SubjectRenameStore.revision.intValue

        SubjectRenameStore.setCustomName(context, SubjectFormatter.normalize(raw), "Мат.")

        assertTrue(SubjectRenameStore.revision.intValue > revBefore)
        assertEquals("*Мат.", SubjectFormatter.getShortName(raw))
        assertNotEquals(before, SubjectFormatter.getShortName(raw))

        SubjectRenameStore.reset(context, SubjectFormatter.normalize(raw))
        assertEquals(before, SubjectFormatter.getShortName(raw))
    }

    @Test
    fun `переименование триггерит обновление расписательных виджетов`() {
        val before = WidgetUpdateHelper.scheduleRefreshRequestCount
        SubjectRenameStore.setCustomName(context, "Математика", "Мат.")
        assertEquals(before + 1, WidgetUpdateHelper.scheduleRefreshRequestCount)
    }

    @Test
    fun `повтор того же имени не дёргает виджеты`() {
        SubjectRenameStore.setCustomName(context, "Математика", "Мат.")
        val before = WidgetUpdateHelper.scheduleRefreshRequestCount
        SubjectRenameStore.setCustomName(context, "Математика", "Мат.")
        assertEquals(before, WidgetUpdateHelper.scheduleRefreshRequestCount)
    }

    @Test
    fun `сброс имени триггерит обновление виджетов`() {
        SubjectRenameStore.setCustomName(context, "Математика", "Мат.")
        val before = WidgetUpdateHelper.scheduleRefreshRequestCount
        SubjectRenameStore.reset(context, "Математика")
        assertEquals(before + 1, WidgetUpdateHelper.scheduleRefreshRequestCount)
    }

    @Test
    fun `сброс без переименования не дёргает виджеты`() {
        val before = WidgetUpdateHelper.scheduleRefreshRequestCount
        SubjectRenameStore.reset(context, "Математика")
        assertEquals(before, WidgetUpdateHelper.scheduleRefreshRequestCount)
    }

    @Test
    fun `context-free чтение из receiver-процесса - in-memory map после записи`() {
        SubjectRenameStore.setCustomName(context, "Информатика", "Компы")
        assertEquals("*Компы", SubjectFormatter.getShortName("Информатика"))
        assertEquals("Компы", SubjectRenameStore.customNameCached("Информатика"))
    }
}
