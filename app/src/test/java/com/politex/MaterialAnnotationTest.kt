package com.politex

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialAnnotationEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.screens.materials.Stroke
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaterialAnnotationTest {
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

    private fun newItem(): Long = runBlocking {
        dao.insertItem(
            MaterialItemEntity(kind = "FILE", title = "Методичка", ext = "pdf", uriPath = "/data/files/materials/m.pdf", subjectName = "ТОЭ"),
        )
    }

    private val s1 = Stroke(0xFF000000.toInt(), 4f, listOf(Offset(0.1f, 0.2f), Offset(0.5f, 0.75f), Offset(1f, 0f)))
    private val s2 = Stroke(0xFFE53935.toInt(), 2f, listOf(Offset(0.25f, 0.5f)))

    @Test
    fun `strokesJson round-trip сохраняет штрихи без потерь`() {
        val strokes = listOf(s1, s2)
        val json = Stroke.serialize(strokes)
        assertEquals(strokes, Stroke.parse(json))
    }

    @Test
    fun `parse битого и пустого JSON возвращает пустой список`() {
        assertEquals(emptyList<Stroke>(), Stroke.parse(""))
        assertEquals(emptyList<Stroke>(), Stroke.parse("не json{"))
        assertEquals(emptyList<Stroke>(), Stroke.parse("{}"))
        assertEquals(emptyList<Stroke>(), Stroke.parse("{\"v\":1,\"strokes\":[]}"))
    }

    @Test
    fun `upsert делает REPLACE по паре itemId-pageIndex`() = runBlocking {
        val id = newItem()

        dao.upsertAnnotation(MaterialAnnotationEntity(itemId = id, pageIndex = 2, strokesJson = Stroke.serialize(listOf(s1))))
        dao.upsertAnnotation(MaterialAnnotationEntity(itemId = id, pageIndex = 2, strokesJson = Stroke.serialize(listOf(s1, s2))))

        val rows = dao.getAnnotationsFlow(id).first()
        assertEquals("одна страница — одна строка, повторный upsert заменяет, а не дублирует", 1, rows.size)
        assertEquals(listOf(s1, s2), Stroke.parse(rows[0].strokesJson))

        dao.upsertAnnotation(MaterialAnnotationEntity(itemId = id, pageIndex = 3, strokesJson = Stroke.serialize(listOf(s2))))
        val all = dao.getAnnotationsFlow(id).first()
        assertEquals(2, all.size)
        assertEquals(listOf(s1, s2), Stroke.parse(all.first { it.pageIndex == 2 }.strokesJson))
        assertEquals(listOf(s2), Stroke.parse(all.first { it.pageIndex == 3 }.strokesJson))
    }

    @Test
    fun `очистка страницы и удаление материала чистят аннотации`() = runBlocking {
        val id = newItem()
        dao.upsertAnnotation(MaterialAnnotationEntity(itemId = id, pageIndex = 0, strokesJson = Stroke.serialize(listOf(s1))))
        dao.upsertAnnotation(MaterialAnnotationEntity(itemId = id, pageIndex = 1, strokesJson = Stroke.serialize(listOf(s2))))

        dao.deletePageAnnotations(id, 0)
        assertEquals(1, dao.getAnnotationsFlow(id).first().size)

        dao.deleteItemWithBookmarks(id)
        assertTrue("аннотации уходят вместе с материалом", dao.getAnnotationsFlow(id).first().isEmpty())
    }

    @Test
    fun `updateTextContent пишет текст и ставит textEdited`() = runBlocking {
        val id = newItem()
        dao.updateTextContent(id, "**жирный** конспект")
        val item = dao.getItemById(id)
        assertEquals("**жирный** конспект", item?.textContent)
        assertTrue("V10: loadParagraphs по флагу textEdited читает БД вместо файла", item?.textEdited == true)
    }
}
