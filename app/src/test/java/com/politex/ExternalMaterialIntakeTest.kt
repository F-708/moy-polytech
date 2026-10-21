package com.politex

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.LessonsSection
import com.politex.ui.screens.materials.ExternalMaterialIntake
import com.politex.ui.screens.targetHubSection
import com.politex.util.MaterialImporter
import com.politex.util.PendingMaterial
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ExternalMaterialIntakeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun contentUri(name: String): Uri = Uri.parse("content://com.telegram.provider/media/$name")

    @Before
    fun resetIntake() {
        drain()
    }

    @After
    fun clearIntake() {
        drain()
    }

    private fun drain() {
        while (ExternalMaterialIntake.hasWork) {
            ExternalMaterialIntake.claim()
            val current = ExternalMaterialIntake.active ?: break
            ExternalMaterialIntake.finish(current)
        }
    }

    @Test
    fun `ACTION_VIEW с документом даёт одиночный файл`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri("лекция.pdf"), "application/pdf")
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
        assertEquals(contentUri("лекция.pdf"), (pending as PendingMaterial.SingleFile).uri)
    }

    @Test
    fun `ACTION_VIEW с картинкой становится набором из одного изображения`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri("доска.jpg"), "image/jpeg")
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.Images)
        assertEquals(1, (pending as PendingMaterial.Images).uris.size)
    }

    @Test
    fun `ACTION_VIEW с type star-star и EXTRA_MIME_TYPES берёт файл из EXTRA_STREAM`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, contentUri("лекция.pdf"))
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf"))
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
        assertEquals(contentUri("лекция.pdf"), (pending as PendingMaterial.SingleFile).uri)
    }

    @Test
    fun `ACTION_VIEW с type star-star и картинкой в EXTRA_MIME_TYPES даёт набор`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, contentUri("доска.jpg"))
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg"))
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.Images)
        assertEquals(contentUri("доска.jpg"), (pending as PendingMaterial.Images).uris.single())
    }

    @Test
    fun `ACTION_VIEW с type star-star без EXTRA_MIME_TYPES спрашивает resolver`() {
        Robolectric.buildContentProvider(ImageTypeProvider::class.java).create("mpk.test")
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://mpk.test/photo"), "*/*")
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.Images)
        assertEquals(Uri.parse("content://mpk.test/photo"), (pending as PendingMaterial.Images).uris.single())
    }

    @Test
    fun `ACTION_VIEW с type star-star и data без EXTRA_STREAM даёт одиночный файл`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri("конспект"), "*/*")
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
    }

    @Test
    fun `ACTION_SEND кладёт файл из EXTRA_STREAM`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri("конспект.pdf"))
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
    }

    @Test
    fun `ACTION_SEND с type star-star берёт файл из EXTRA_STREAM`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, contentUri("конспект.pdf"))
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf"))
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
    }

    @Test
    fun `ACTION_SEND без type берёт файл из EXTRA_STREAM`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, contentUri("конспект.pdf"))
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
    }

    @Test
    fun `ACTION_SEND_MULTIPLE с изображениями даёт набор`() {
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(contentUri("1.jpg"), contentUri("2.jpg"), contentUri("3.jpg")),
                )
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.Images)
        assertEquals(3, (pending as PendingMaterial.Images).uris.size)
    }

    @Test
    fun `ACTION_SEND_MULTIPLE с type star-star и EXTRA_MIME_TYPES image даёт набор`() {
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*"))
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(contentUri("1.jpg"), contentUri("2.jpg")),
                )
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.Images)
        assertEquals(2, (pending as PendingMaterial.Images).uris.size)
    }

    @Test
    fun `ACTION_SEND_MULTIPLE с документами не превращается в набор картинок`() {
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/pdf"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(contentUri("1.pdf"), contentUri("2.pdf")),
                )
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.SingleFile)
        assertEquals(contentUri("1.pdf"), (pending as PendingMaterial.SingleFile).uri)
    }

    @Test
    fun `file схема отклоняется и для VIEW и для SEND`() {
        val file = Uri.parse("file:///data/data/com.politex/databases/mpk_schedule.db")
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, Intent(Intent.ACTION_VIEW, file)))
        assertNull(
            ExternalMaterialIntake.fromIntent(
                context.contentResolver,
                Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, file)
                },
            ),
        )
        assertNull(
            ExternalMaterialIntake.fromIntent(
                context.contentResolver,
                Intent(Intent.ACTION_VIEW).apply { setDataAndType(file, "application/octet-stream") },
            ),
        )
    }

    @Test
    fun `APK по MIME и по расширению отклоняется`() {
        val byMime =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri("update"), "application/vnd.android.package-archive")
            }
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, byMime))

        val byName =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri("update.apk"), "application/octet-stream")
            }
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, byName))

        val multiple =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/vnd.android.package-archive"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(contentUri("update.apk")))
            }
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, multiple))
    }

    @Test
    fun `чужие типы в EXTRA_STREAM не роняют разбор`() {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, "not-a-uri")
            }
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, send))

        val sendObject =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, Intent(Intent.ACTION_MAIN))
            }
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, sendObject))

        val multiple =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putStringArrayListExtra(Intent.EXTRA_STREAM, arrayListOf("a", "b"))
            }
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, multiple))
    }

    @Test
    fun `SEND_MULTIPLE больше лимита обрезается и предупреждает`() {
        val uris = (1..25).map { contentUri("$it.jpg") }
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        val pending = ExternalMaterialIntake.fromIntent(context.contentResolver, intent)
        assertTrue(pending is PendingMaterial.Images)
        assertEquals(ExternalMaterialIntake.MAX_FILES_PER_SHARE, (pending as PendingMaterial.Images).uris.size)
        assertNotNull(ExternalMaterialIntake.consumeWarning())
    }

    @Test
    fun `copyToStorage обрывает поток больше лимита и убирает частичный файл`() {
        val uri = Uri.parse("content://mpk.test/big")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArray(4096) { 1 }.inputStream())

        val error =
            runCatching {
                runBlocking { MaterialImporter.copyToStorage(context, uri, "cap-test.bin", maxBytes = 1024) }
            }.exceptionOrNull()

        assertTrue(error is IOException)
        assertFalse(File(MaterialImporter.storageDir(context), "cap-test.bin").exists())
    }

    @Test
    fun `claim удерживает материал при отмене и finish освобождает`() {
        val material = PendingMaterial.Text("Конспект")
        ExternalMaterialIntake.offer(material)
        ExternalMaterialIntake.claim()

        assertNull(ExternalMaterialIntake.pending)
        assertSame(material, ExternalMaterialIntake.active)
        assertTrue(ExternalMaterialIntake.hasWork)

        ExternalMaterialIntake.finish(material)
        assertFalse(ExternalMaterialIntake.hasWork)
    }

    @Test
    fun `finish чужого материала не сбрасывает active`() {
        val material = PendingMaterial.Text("Свой")
        ExternalMaterialIntake.offer(material)
        ExternalMaterialIntake.claim()

        ExternalMaterialIntake.finish(PendingMaterial.Text("Чужой"))

        assertSame(material, ExternalMaterialIntake.active)
        ExternalMaterialIntake.finish(material)
        assertFalse(ExternalMaterialIntake.hasWork)
    }

    @Test
    fun `второй материал ждёт пока активный не завершён`() {
        val first = PendingMaterial.Text("Первый")
        val second = PendingMaterial.Text("Второй")
        ExternalMaterialIntake.offer(first)
        ExternalMaterialIntake.claim()
        ExternalMaterialIntake.offer(second)

        assertSame(first, ExternalMaterialIntake.active)
        assertSame(second, ExternalMaterialIntake.pending)

        ExternalMaterialIntake.finish(first)
        ExternalMaterialIntake.claim()
        assertSame(second, ExternalMaterialIntake.active)
        assertNull(ExternalMaterialIntake.pending)
    }

    @Test
    fun `пока intake не завершён секция остаётся материалами`() {
        val material = PendingMaterial.Text("Лекция")
        ExternalMaterialIntake.offer(material)
        ExternalMaterialIntake.claim()

        assertEquals(
            LessonsSection.MATERIALS,
            targetHubSection(LessonsSection.TASKS, ExternalMaterialIntake.hasWork),
        )

        ExternalMaterialIntake.finish(material)
        assertEquals(
            LessonsSection.TASKS,
            targetHubSection(LessonsSection.TASKS, ExternalMaterialIntake.hasWork),
        )
    }

    @Test
    fun `пустые и посторонние intent игнорируются`() {
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, Intent(Intent.ACTION_MAIN)))
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, Intent(Intent.ACTION_VIEW)))
        assertNull(
            ExternalMaterialIntake.fromIntent(
                context.contentResolver,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/page")),
            ),
        )
        assertNull(ExternalMaterialIntake.fromIntent(context.contentResolver, Intent(Intent.ACTION_SEND_MULTIPLE)))
    }

    class ImageTypeProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String = "image/jpeg"

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}