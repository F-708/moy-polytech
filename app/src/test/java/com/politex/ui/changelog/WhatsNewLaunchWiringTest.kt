package com.politex.ui.changelog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhatsNewLaunchWiringTest {

    private fun src(relative: String): String = sequenceOf(
        File("src/main/java/$relative"),
        File("app/src/main/java/$relative"),
    ).first { it.exists() }.readText()

    private val mainActivity = src("com/politex/MainActivity.kt")

    @Test
    fun `XX - точка показа подключена в непросветительской ветке MainActivity`() {
        assertTrue(mainActivity.contains("whatsNewEntries = com.politex.ui.changelog.WhatsNewStore.onLaunch(this, BuildConfig.VERSION_CODE)"))
        assertTrue(mainActivity.contains("com.politex.ui.changelog.WhatsNewDialog("))
        assertTrue(mainActivity.contains("com.politex.ui.changelog.WhatsNewStore.recordSeen(this@MainActivity, BuildConfig.VERSION_CODE)"))
        val onLaunchIndex = mainActivity.indexOf("WhatsNewStore.onLaunch")
        assertTrue(onLaunchIndex > mainActivity.indexOf("setupPeriodicScheduleCheck"))
        assertTrue(onLaunchIndex < mainActivity.indexOf("setContent {"))
    }

    @Test
    fun `XX - диалог показывается только когда записи есть и скрывается при закрытии`() {
        assertTrue(mainActivity.contains("var showWhatsNew by remember { mutableStateOf(whatsNewEntries.isNotEmpty()) }"))
        assertTrue(mainActivity.contains("if (showWhatsNew && whatsNewEntries.isNotEmpty())"))
        assertTrue(mainActivity.contains("showWhatsNew = false"))
        assertTrue(mainActivity.contains("whatsNewEntries = emptyList()"))
    }

    @Test
    fun `XX - контент 2_0_1 краткий и по делу`() {
        val entry = WhatsNewEntries.single { it.versionCode == 20001999 }
        assertEquals("2.0.1", entry.versionName)
        assertTrue(entry.items.size in 4..6)
        entry.items.forEach { item ->
            assertTrue(item.text.isNotBlank())
            assertTrue(item.text.length <= 90)
            assertTrue(!item.text.contains('\n'))
        }
        val texts = entry.items.map { it.text }
        assertTrue(texts.any { it.contains("секундомеры") && it.contains("«до звонка»") })
        assertTrue(texts.any { it.contains("«Установить»") })
        assertTrue(texts.any { it.contains("2–3 раза быстрее") })
        assertTrue(texts.any { it.contains("автоматически и незаметно") })
    }

    @Test
    fun `XX - предикат выбора записей берёт только диапазон между lastSeen и текущей версией`() {
        val all = listOf(
            WhatsNewEntry(100, "0.0.1", listOf(WhatsNewItem(WhatsNewIcon.SPEED, "a"))),
            WhatsNewEntry(200, "0.0.2", listOf(WhatsNewItem(WhatsNewIcon.SPEED, "b"))),
            WhatsNewEntry(300, "0.0.3", listOf(WhatsNewItem(WhatsNewIcon.SPEED, "c"))),
        )
        assertEquals(emptyList<WhatsNewEntry>(), entriesFor(300, 300, all))
        assertEquals(emptyList<WhatsNewEntry>(), entriesFor(400, 300, all))
        assertEquals(emptyList<WhatsNewEntry>(), entriesFor(300, 200, all))
        assertEquals(listOf(200, 300), entriesFor(100, 300, all).map { it.versionCode })
        assertEquals(listOf(300), entriesFor(250, 350, all).map { it.versionCode })
    }
}
