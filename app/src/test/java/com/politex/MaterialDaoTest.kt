package com.politex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaterialDaoTest {
    private lateinit var db: MpkDatabase
    private lateinit var dao: MaterialDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.materialDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newItem(
        subject: String,
        title: String,
        kind: MaterialKind = MaterialKind.FILE,
        ext: String = "pdf",
    ): Long = runBlocking {
        dao.insertItem(
            MaterialItemEntity(
                kind = kind.name,
                title = title,
                fileName = "$title.$ext",
                ext = ext,
                uriPath = "/data/files/materials/$title.$ext",
                subjectName = subject,
                sortOrder = dao.nextItemSortOrder(subject),
            ),
        )
    }

    private fun titlesIn(subject: String): List<String> = runBlocking {
        dao.getForSubject(subject, false).first().map { it.title }
    }

    @Test
    fun `вставка ставит материал в конец предмета`() = runBlocking {
        newItem("ТОЭ", "Лекция 1")
        newItem("ТОЭ", "Лекция 2")
        newItem("ТОЭ", "Лекция 3")
        assertEquals(listOf("Лекция 1", "Лекция 2", "Лекция 3"), titlesIn("ТОЭ"))
    }

    @Test
    fun `порядки предметов независимы`() = runBlocking {
        val a = newItem("ТОЭ", "А")
        val b = newItem("Математика", "Б")
        assertEquals("первый материал каждого предмета получает sortOrder 0", 0, dao.getItemById(a)?.sortOrder)
        assertEquals(0, dao.getItemById(b)?.sortOrder)
    }

    @Test
    fun `переименование меняет только название`() = runBlocking {
        val id = newItem("ТОЭ", "IMG_2049")

        dao.renameItem(id, "Лекция 3 — цепи")
        val item = dao.getItemById(id)

        assertEquals("Лекция 3 — цепи", item?.title)
        assertEquals("имя файла остаётся оригинальным — по нему ищется копия на диске", "IMG_2049.pdf", item?.fileName)
        assertEquals("pdf", item?.ext)
    }

    @Test
    fun `reorder стрелкой вниз меняет материал местами с соседом`() = runBlocking {
        val a = newItem("ТОЭ", "А")
        val b = newItem("ТОЭ", "Б")
        val c = newItem("ТОЭ", "В")

        dao.swapSortOrder(a, b)
        assertEquals(listOf("Б", "А", "В"), titlesIn("ТОЭ"))

        dao.swapSortOrder(a, c)
        assertEquals(listOf("Б", "В", "А"), titlesIn("ТОЭ"))
    }

    @Test
    fun `reorder соседних элементов обратим`() = runBlocking {
        val a = newItem("ТОЭ", "А")
        val b = newItem("ТОЭ", "Б")

        dao.swapSortOrder(a, b)
        dao.swapSortOrder(a, b)
        assertEquals("два нажатия стрелки туда-обратно возвращают исходный порядок", listOf("А", "Б"), titlesIn("ТОЭ"))
    }

    @Test
    fun `swap с неизвестным id не падает`() = runBlocking {
        val a = newItem("ТОЭ", "А")
        dao.swapSortOrder(a, 9999L)
        dao.swapSortOrder(9999L, a)
        assertEquals(listOf("А"), titlesIn("ТОЭ"))
    }

    @Test
    fun `архив скрывает материал из предмета и возвращает его в конец списка`() = runBlocking {
        val kept = newItem("ТОЭ", "Лекция 1")
        val moved = newItem("ТОЭ", "Лекция 2")
        val archived = newItem("ТОЭ", "Лекция 3")
        dao.setArchived(archived, true, dao.nextArchiveSortOrder())

        assertEquals(
            "архивированный материал исчезает из предмета",
            listOf("Лекция 1", "Лекция 2"),
            titlesIn("ТОЭ"),
        )
        assertTrue(dao.getItemById(archived)?.archived == true)

        val restoreOrder = dao.nextItemSortOrder("ТОЭ")
        dao.setArchived(moved, true, dao.nextArchiveSortOrder())
        dao.setArchived(kept, true, dao.nextArchiveSortOrder())
        dao.setArchived(moved, false, restoreOrder)
        assertEquals(listOf("Лекция 2"), titlesIn("ТОЭ"))
        assertFalse(dao.getItemById(moved)?.archived ?: true)
    }

    @Test
    fun `удаление материала уносит его закладки`() = runBlocking {
        val id = newItem("ТОЭ", "Учебник")
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 12, label = "Формулы"))
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 40, label = "Вывод"))
        assertEquals(2, dao.getBookmarksForItem(id).size)

        dao.deleteItemWithBookmarks(id)

        assertNull("материал удалён", dao.getItemById(id))
        assertTrue("закладки-сироты удалены вместе с ним", dao.getBookmarksForItem(id).isEmpty())
        assertTrue(titlesIn("ТОЭ").isEmpty())
    }

    @Test
    fun `закладки сортируются по странице`() = runBlocking {
        val id = newItem("ТОЭ", "Учебник")
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 40, label = "Поздняя"))
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 5, label = "Ранняя"))
        dao.insertBookmark(MaterialBookmarkEntity(itemId = id, page = 20, label = "Средняя"))

        assertEquals(listOf(5, 20, 40), dao.getBookmarksForItem(id).map { it.page })

        val middle = dao.getBookmarksForItem(id).first { it.page == 20 }
        dao.deleteBookmarkById(middle.id)
        assertEquals(listOf(5, 40), dao.getBookmarksForItem(id).map { it.page })
    }

    @Test
    fun `getAllItems отдаёт и архивные и не-архивные`() = runBlocking {
        val a = newItem("ТОЭ", "А")
        newItem("Математика", "Б")
        dao.setArchived(a, true, dao.nextArchiveSortOrder())

        assertEquals("архив НЕ выпадает из общего потока — экран Архива строится по нему", 2, dao.getAllItems().first().size)
    }

    @Test
    fun `состояние просмотрщика сохраняется в строке материала`() = runBlocking {
        val id = newItem("ТОЭ", "Методичка", kind = MaterialKind.IMAGES, ext = "jpg")

        dao.setLastPage(id, 7)
        assertEquals(7, dao.getItemById(id)?.lastPage)

        dao.setRotationDeg(id, 270)
        assertEquals(270, dao.getItemById(id)?.rotationDeg)

        val paths = listOf("/files/materials/$id-0.jpg", "/files/materials/$id-1.jpg")
        dao.setImagePaths(id, paths.joinToString("\n"))
        assertEquals(paths, dao.getItemById(id)?.imagePaths)

        dao.setImagePaths(id, paths.reversed().joinToString("\n"))
        assertEquals(paths.reversed(), dao.getItemById(id)?.imagePaths)
    }
}
