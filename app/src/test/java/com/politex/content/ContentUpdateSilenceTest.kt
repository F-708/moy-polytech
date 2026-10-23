package com.politex.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentUpdateSilenceTest {

    private fun src(relative: String): String = sequenceOf(
        File("src/main/java/$relative"),
        File("app/src/main/java/$relative"),
    ).first { it.exists() }.readText()

    private val worker = src("com/politex/content/ContentPackWorker.kt")
    private val store = src("com/politex/content/ContentPackStore.kt")
    private val assets = src("com/politex/content/ContentAssets.kt")
    private val validator = src("com/politex/content/ContentPackValidator.kt")
    private val mainActivity = src("com/politex/MainActivity.kt")
    private val settings = src("com/politex/ui/screens/SettingsScreen.kt")
    private val updateWorker = src("com/politex/update/AppUpdateWorker.kt")
    private val mainScreen = src("com/politex/ui/screens/MainScreen.kt")

    @Test
    fun `XX - в путях контент-пакета нет żadnych пользовательских сообщений`() {
        listOf(worker, store, assets, validator).forEach { source ->
            assertFalse(source.contains("Notification"))
            assertFalse(source.contains("Toast"))
            assertFalse(source.contains("Snackbar"))
            assertFalse(source.contains("AlertDialog"))
            assertFalse(source.contains("setForeground"))
            assertFalse(source.contains("Log."))
        }
    }

    @Test
    fun `XX - точки входа контент-пакета остались и работают молча`() {
        assertTrue(mainActivity.contains("com.politex.content.ContentPackWorker.schedule(this)"))
        assertTrue(mainActivity.contains("com.politex.content.ContentPackWorker.checkOnStartup(this)"))
        assertTrue(worker.contains("Result.retry()"))
        assertTrue(worker.contains("startupCheckNeeded"))
    }

    @Test
    fun `XX - ручного обновления пакета и его строки статуса в настройках нет`() {
        assertFalse(settings.contains("content_pack_update_btn"))
        assertFalse(settings.contains("content_pack_status_row"))
        assertFalse(settings.contains("contentPackStatusText"))
        assertFalse(settings.contains("ContentPackWorker.checkOnce"))
    }

    @Test
    fun `XX - уведомления обновления APK остались на месте`() {
        assertTrue(updateWorker.contains("NotificationCompat"))
        assertTrue(updateWorker.contains("Доступно обновление"))
        assertTrue(updateWorker.contains("Обновление отклонено"))
        assertTrue(mainScreen.contains("pulseHalo(tab == AppTab.COLLEGE && updatePending != null)"))
        assertTrue(mainScreen.contains("UpdateInstallController.foundUpdate.collectAsState()"))
    }
}
