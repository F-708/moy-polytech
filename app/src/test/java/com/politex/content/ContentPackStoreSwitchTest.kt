package com.politex.content

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContentPackStoreSwitchTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        ContentPackStore.attach(context)
    }

    @After
    fun tearDown() {
        ContentPackStore.reset(context)
    }

    private fun makePackDir(version: Int): File = ContentPackStore
        .packDir(context, version)
        .apply {
            mkdirs()
            ContentPackValidator.REQUIRED_FILES.forEach { File(this, it).writeText("x") }
        }

    @Test
    fun `activate пишет prefs поднимает ревизию и держит активный пакет`() {
        makePackDir(3)
        val revisionBefore = ContentPackStore.revision.intValue
        ContentPackStore.activate(context, 3, "2026-10-20T10:00:00Z", 2)
        assertEquals(3, ContentPackStore.activeVersion)
        assertEquals("2026-10-20T10:00:00Z", ContentPackStore.generatedAt)
        assertTrue(ContentPackStore.revision.intValue > revisionBefore)
        val prefs = context.getSharedPreferences("content_pack_prefs", android.content.Context.MODE_PRIVATE)
        assertEquals(3, prefs.getInt("active_version", 0))

        ContentPackStore.attach(context)
        assertEquals(3, ContentPackStore.activeVersion)
    }

    @Test
    fun `attach сбрасывает версию когда папка пакета пропала`() {
        val dir = makePackDir(4)
        ContentPackStore.activate(context, 4, "", 0)
        dir.deleteRecursively()
        ContentPackStore.attach(context)
        assertEquals(0, ContentPackStore.activeVersion)
        val prefs = context.getSharedPreferences("content_pack_prefs", android.content.Context.MODE_PRIVATE)
        assertEquals(0, prefs.getInt("active_version", 0))
    }

    @Test
    fun `attach принимает только папку с обязательным составом`() {
        val dir = ContentPackStore.packDir(context, 6).apply { mkdirs() }
        File(dir, "teachers.csv").writeText("x")
        assertFalse(ContentPackStore.isUsablePackDir(dir))
        ContentPackValidator.REQUIRED_FILES.forEach { File(dir, it).writeText("x") }
        assertTrue(ContentPackStore.isUsablePackDir(dir))
    }

    @Test
    fun `prune держит активный и предыдущий пакеты и убирает мусор`() {
        makePackDir(1)
        makePackDir(2)
        makePackDir(3)
        val staging = File(ContentPackStore.contentDir(context), "staging-9").apply { mkdirs() }
        ContentPackStore.activate(context, 3, "", 2)
        assertFalse(ContentPackStore.packDir(context, 1).exists())
        assertTrue(ContentPackStore.packDir(context, 2).exists())
        assertTrue(ContentPackStore.packDir(context, 3).exists())
        assertFalse(staging.exists())
    }

    @Test
    fun `статус текста настроек`() {
        assertEquals("Встроенные данные", contentPackStatusText(0, ""))
        assertEquals("Пакет v7 от 2026-10-20", contentPackStatusText(7, "2026-10-20T12:00:00Z"))
        assertEquals("Пакет v7", contentPackStatusText(7, ""))
        assertEquals("Пакет v7", contentPackStatusText(7, "T12:00"))
    }
}
