package com.politex

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.MaterialDao
import com.politex.util.MaterialImporter
import com.politex.util.PendingMaterial
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MaterialContentHashTest {
    private lateinit var context: Context
    private lateinit var db: MpkDatabase
    private lateinit var dao: MaterialDao
    private lateinit var inbox: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.materialDao()
        inbox = File(context.cacheDir, "inbox").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        inbox.deleteRecursively()
        runCatching { MaterialImporter.storageDir(context).deleteRecursively() }
    }

    private fun fileWith(
        name: String,
        content: ByteArray,
    ): Uri {
        val f = File(inbox, name)
        f.writeBytes(content)
        return Uri.fromFile(f)
    }

    @Test
    fun `sha256 пустого потока совпадает с эталоном`() = runBlocking {
        val uri = fileWith("empty.bin", ByteArray(0))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            MaterialImporter.contentUriSha256(context, uri),
        )
    }

    @Test
    fun `одинаковое содержимое под разными именами даёт один hash`() = runBlocking {
        val bytes = "%PDF-1.4 фиктивный контент лекции".toByteArray()
        val a = MaterialImporter.contentUriSha256(context, fileWith("лекция-1.pdf", bytes))
        val b = MaterialImporter.contentUriSha256(context, fileWith("scan_2026_10_19.pdf", bytes))
        assertNotNull(a)
        assertEquals("имя файла не влияет на hash содержимого", a, b)
    }

    @Test
    fun `разное содержимое даёт разный hash`() = runBlocking {
        val a = MaterialImporter.contentUriSha256(context, fileWith("a.pdf", "первый".toByteArray()))
        val b = MaterialImporter.contentUriSha256(context, fileWith("b.pdf", "второй".toByteArray()))
        assertNotEquals(a, b)
    }

    @Test
    fun `несуществующий источник даёт null, а не падение`() = runBlocking {
        val missing = Uri.fromFile(File(inbox, "нет-такого.pdf"))
        assertNull(MaterialImporter.contentUriSha256(context, missing))
    }

    @Test
    fun `импорт записывает hash, повтор того же содержимого находит существующий`() = runBlocking {
        val bytes = "лекция по ТОЭ, один и тот же файл".toByteArray()
        val first = fileWith("методичка.pdf", bytes)
        val id = MaterialImporter.importMaterial(context, dao, "ТОЭ", PendingMaterial.SingleFile(first))

        val stored = dao.getItemById(id)
        assertNotNull(stored)
        assertTrue("hash записан при импорте", stored!!.contentHash.isNotBlank())

        val again = fileWith("переименованная-копия.pdf", bytes)
        val hit = MaterialImporter.findExistingByContent(context, dao, PendingMaterial.SingleFile(again))
        assertEquals("то же содержимое под другим именем — существующий материал", id, hit?.id)
    }

    @Test
    fun `другое содержимое не матчится — будет новый материал`() = runBlocking {
        val first = fileWith("a.pdf", "содержимое А".toByteArray())
        MaterialImporter.importMaterial(context, dao, "ТОЭ", PendingMaterial.SingleFile(first))

        val other = fileWith("b.pdf", "содержимое Б".toByteArray())
        assertNull(MaterialImporter.findExistingByContent(context, dao, PendingMaterial.SingleFile(other)))
    }

    @Test
    fun `удалённый материал выпадает из реестра — повторный запуск снова спросит куда положить`() = runBlocking {
        val bytes = "удаляемая лекция".toByteArray()
        val id = MaterialImporter.importMaterial(context, dao, "ТОЭ", PendingMaterial.SingleFile(fileWith("del.pdf", bytes)))
        MaterialImporter.deleteStoredFiles(dao.getItemById(id)!!)
        dao.deleteItemWithBookmarks(id)

        val again = fileWith("del-copy.pdf", bytes)
        assertNull(
            "после удаления материала hash больше не находит строку",
            MaterialImporter.findExistingByContent(context, dao, PendingMaterial.SingleFile(again)),
        )
    }

    @Test
    fun `материал со стёртым с диска файлом не считается живым совпадением`() = runBlocking {
        val bytes = "файл сотрут из хранилища".toByteArray()
        val id = MaterialImporter.importMaterial(context, dao, "ТОЭ", PendingMaterial.SingleFile(fileWith("stale.pdf", bytes)))
        val item = dao.getItemById(id)!!
        File(item.uriPath).delete()

        val again = fileWith("stale-copy.pdf", bytes)
        assertNull(
            "строка есть, но файл стёрт — импортируем заново",
            MaterialImporter.findExistingByContent(context, dao, PendingMaterial.SingleFile(again)),
        )
    }

    @Test
    fun `текстовые материалы не участвуют в реестре hash`() = runBlocking {
        MaterialImporter.importMaterial(context, dao, "ТОЭ", PendingMaterial.Text("Заметка про закон Ома"))
        val blank = dao.getItemsByContentHash("")
        assertTrue("пустой hash не выдаёт строки", blank.isEmpty())
    }

    @Test
    fun `набор изображений запоминается по содержимому всех файлов`() = runBlocking {
        val u1 = fileWith("p1.jpg", "кадр-1".toByteArray())
        val u2 = fileWith("p2.jpg", "кадр-2".toByteArray())
        val id = MaterialImporter.importMaterial(context, dao, "Фотография", PendingMaterial.Images(listOf(u1, u2)))
        assertTrue(dao.getItemById(id)!!.contentHash.isNotBlank())

        val c1 = fileWith("копия-1.jpg", "кадр-1".toByteArray())
        val c2 = fileWith("копия-2.jpg", "кадр-2".toByteArray())
        val hit = MaterialImporter.findExistingByContent(context, dao, PendingMaterial.Images(listOf(c1, c2)))
        assertEquals(id, hit?.id)

        val swapped = MaterialImporter.findExistingByContent(context, dao, PendingMaterial.Images(listOf(c2, c1)))
        assertNull("другой порядок — другой набор", swapped)
    }

    @Test
    fun `большой файл хэшируется потоково без OutOfMemory`() = runBlocking {
        val big = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val f = File(inbox, "big.pdf")
        f.outputStream().use { out ->
            repeat(24) { out.write(big) }
        }
        val hash = MaterialImporter.contentUriSha256(context, Uri.fromFile(f))
        assertNotNull(hash)
        assertEquals(64, hash!!.length)
        assertEquals(hash, MaterialImporter.contentUriSha256(context, Uri.fromFile(f)))
    }
}
