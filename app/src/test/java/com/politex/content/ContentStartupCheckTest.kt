package com.politex.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.politex.util.WorkManagerTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = WorkManagerTestApplication::class)
class ContentStartupCheckTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("content_pack_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        WorkManager
            .getInstance(context)
            .cancelUniqueWork(ContentPackWorker.STARTUP_WORK_NAME)
            .result
            .get()
        prefs.edit().remove("last_startup_check_at").apply()
    }

    private fun startupWorks(): List<WorkInfo> = WorkManager
        .getInstance(context)
        .getWorkInfosForUniqueWork(ContentPackWorker.STARTUP_WORK_NAME)
        .get()

    private fun activeStartupCount(): Int = startupWorks().count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    private fun awaitActiveStartup(expected: Int) {
        repeat(100) {
            if (activeStartupCount() == expected) return
            Thread.sleep(50)
        }
        assertEquals(expected, activeStartupCount())
    }

    @Test
    fun `первый запуск планирует одну проверку и пишет отметку`() {
        ContentPackWorker.checkOnStartup(context)
        awaitActiveStartup(1)
        assertTrue(prefs.getLong("last_startup_check_at", 0L) > 0L)
    }

    @Test
    fun `запуск подряд не добавляет новую проверку`() {
        ContentPackWorker.checkOnStartup(context)
        awaitActiveStartup(1)
        val firstMark = prefs.getLong("last_startup_check_at", 0L)
        ContentPackWorker.checkOnStartup(context)
        ContentPackWorker.checkOnStartup(context)
        awaitActiveStartup(1)
        assertEquals(firstMark, prefs.getLong("last_startup_check_at", 0L))
    }

    @Test
    fun `после истечения дебаунса планируется новая проверка`() {
        ContentPackWorker.checkOnStartup(context)
        awaitActiveStartup(1)
        WorkManager
            .getInstance(context)
            .cancelUniqueWork(ContentPackWorker.STARTUP_WORK_NAME)
            .result
            .get()
        ContentPackWorker.rememberStartupCheck(prefs, System.currentTimeMillis() - 6 * 60_000L)
        ContentPackWorker.checkOnStartup(context)
        awaitActiveStartup(1)
        val mark = prefs.getLong("last_startup_check_at", 0L)
        assertTrue(System.currentTimeMillis() - mark < 60_000L)
    }

    @Test
    fun `свежая отметка периодической проверки подавляет startup-запуск`() {
        ContentPackWorker.rememberStartupCheck(prefs, System.currentTimeMillis())
        ContentPackWorker.checkOnStartup(context)
        Thread.sleep(300)
        assertEquals(0, activeStartupCount())
    }
}
