package com.politex.content

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContentAssetsResolverTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        ContentPackStore.attach(context)
    }

    @After
    fun tearDown() {
        ContentPackStore.reset(context)
    }

    private fun activatePack(
        version: Int,
        files: Map<String, String>,
    ) {
        val dir = ContentPackStore.packDir(context, version).apply { mkdirs() }
        files.forEach { (name, content) ->
            val file = File(dir, name)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        }
        ContentPackStore.activate(context, version, "2026-10-01T00:00:00Z", 0)
    }

    @Test
    fun `без активного пакета чтение идёт из assets`() {
        assertEquals(0, ContentPackStore.activeVersion)
        val text = ContentAssets.open(context, "teachers.csv").bufferedReader().use { it.readText() }
        assertTrue(text.isNotBlank())
        val groups = ContentAssets.open(context, "group_codes.csv").bufferedReader().use { it.readText() }
        assertTrue(groups.isNotBlank())
    }

    @Test
    fun `файл пакета имеет приоритет над assets`() {
        activatePack(7, mapOf("teachers.csv" to "PACK_TEACHERS"))
        val text = ContentAssets.open(context, "teachers.csv").bufferedReader().use { it.readText() }
        assertEquals("PACK_TEACHERS", text)
    }

    @Test
    fun `отсутствующий в пакете файл возвращается к assets`() {
        activatePack(7, mapOf("teachers.csv" to "PACK_TEACHERS"))
        val groups = ContentAssets.open(context, "group_codes.csv").bufferedReader().use { it.readText() }
        assertTrue(groups.isNotBlank())
        assertTrue(!groups.startsWith("PACK_TEACHERS"))
    }

    @Test
    fun `photoModel возвращает путь пакета с версией`() {
        activatePack(7, mapOf("teachers_photos/p1.jpg" to "x"))
        val model = ContentAssets.photoModel(context, "p1.jpg")
        assertTrue(model.startsWith("file://"))
        assertTrue(model.replace('\\', '/').contains("/content/7/teachers_photos/p1.jpg"))
    }

    @Test
    fun `photoModel без файла в пакете возвращается к android_asset`() {
        activatePack(7, mapOf("teachers.csv" to "PACK_TEACHERS"))
        assertEquals(
            "file:///android_asset/teachers_photos/missing.jpg",
            ContentAssets.photoModel(context, "missing.jpg"),
        )
    }

    @Test
    fun `photoModel без активного пакета указывает на android_asset`() {
        assertEquals(
            "file:///android_asset/teachers_photos/p1.jpg",
            ContentAssets.photoModel(context, "p1.jpg"),
        )
    }

    @Test
    fun `чистые резолверы отдают файл только когда он существует`() {
        val root = File(context.filesDir, "resolver-root").apply { mkdirs() }
        val existing = File(root, "a.csv").apply { writeText("1") }
        assertEquals(existing, ContentAssets.resolvePackFile(root, "a.csv"))
        assertNull(ContentAssets.resolvePackFile(root, "missing.csv"))
        assertNull(ContentAssets.resolvePackFile(null, "a.csv"))
        assertNull(ContentAssets.resolvePackFile(root, ""))
        assertEquals(
            "file://" + existing.absolutePath,
            ContentAssets.photoModelFor(existing, "a.csv"),
        )
        assertEquals(
            "file:///android_asset/teachers_photos/a.jpg",
            ContentAssets.photoModelFor(null, "a.jpg"),
        )
    }
}
