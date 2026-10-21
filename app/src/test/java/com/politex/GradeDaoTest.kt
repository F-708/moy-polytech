package com.politex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.GradeDao
import com.politex.data.local.entity.GradeConfigEntity
import com.politex.data.local.entity.GradeEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GradeDaoTest {
    private lateinit var db: MpkDatabase
    private lateinit var dao: GradeDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.gradeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(
        subject: String = "ТОЭ",
        category: String = "REGULAR",
        value: Int = 8,
        label: String = "",
        dateIso: String = "2026-10-01",
    ) = GradeEntryEntity(
        subjectName = subject,
        category = category,
        value = value,
        label = label,
        dateIso = dateIso,
    )

    @Test
    fun `записи предмета отдаются потоком в порядке дат`() = runBlocking {
        dao.insert(entry(value = 7, dateIso = "2026-10-05", label = "Практ. №2"))
        dao.insert(entry(value = 9, dateIso = "2026-09-30", label = "Практ. №1"))
        dao.insert(entry(subject = "Математика", value = 4))

        val subjectEntries = dao.entriesForSubject("ТОЭ").first()
        assertEquals(listOf("2026-09-30", "2026-10-05"), subjectEntries.map { it.dateIso })
        assertEquals("Практ. №1", subjectEntries.first().label)

        val all = dao.allEntries().first()
        assertEquals("allEntries видит все предметы", 3, all.size)
    }

    @Test
    fun `upsertExam оставляет ровно одну запись экзамена`() = runBlocking {
        dao.upsertExam(entry(category = "EXAM", value = 5, dateIso = "2026-06-20"))
        dao.upsertExam(entry(category = "EXAM", value = 9, dateIso = "2026-06-25"))
        dao.insert(entry(category = "REGULAR", value = 8))

        val exams = dao.entriesForSubject("ТОЭ").first().filter { it.category == "EXAM" }
        assertEquals(1, exams.size)
        assertEquals("новая оценка заменила прежнюю", 9, exams.single().value)
        assertEquals("2026-06-25", exams.single().dateIso)
    }

    @Test
    fun `стирание экзамена и обычной оценки — deleteById`() = runBlocking {
        val examId = dao.insert(entry(category = "EXAM", value = 9))
        val regId = dao.insert(entry(value = 8))

        dao.deleteById(examId)
        dao.deleteById(regId)

        assertEquals(0, dao.entriesForSubject("ТОЭ").first().size)
    }

    @Test
    fun `LAB хранит 0 незачёт и 1 зачёт`() = runBlocking {
        dao.insert(entry(category = "LAB", value = 1, label = "Лаб. №1"))
        dao.insert(entry(category = "LAB", value = 0, label = "Лаб. №2"))

        val labs = dao.entriesForSubject("ТОЭ").first()
        assertEquals(listOf(1, 0), labs.map { it.value })
    }

    @Test
    fun `конфиг приоритета — REPLACE по предмету, без записи — null`() = runBlocking {
        assertNull(dao.getConfig("ТОЭ"))

        dao.upsertConfig(GradeConfigEntity("ТОЭ", """["EXAM","CONTROL","PRACTICAL","REGULAR"]"""))
        assertEquals(
            """["EXAM","CONTROL","PRACTICAL","REGULAR"]""",
            dao.getConfig("ТОЭ")?.priorityJson,
        )

        dao.upsertConfig(GradeConfigEntity("ТОЭ", """["REGULAR","EXAM"]"""))
        assertEquals(1, dao.allConfigs().first().size)
        assertEquals("""["REGULAR","EXAM"]""", dao.getConfig("ТОЭ")?.priorityJson)
    }

    @Test
    fun `deleteBySubject стирает все оценки предмета и не трогает чужие`() = runBlocking {
        dao.insert(entry(subject = "Физическая культура и здоровье", value = 9))
        dao.insert(entry(subject = "Физическая культура и здоровье", category = "PRACTICAL", value = 7))
        dao.insert(entry(subject = "Математика", value = 4))

        dao.deleteBySubject("Физическая культура и здоровье")

        assertEquals(0, dao.entriesForSubject("Физическая культура и здоровье").first().size)
        assertEquals(1, dao.entriesForSubject("Математика").first().size)
        assertEquals(1, dao.allEntries().first().size)
    }

    @Test
    fun `deleteBySubject по отображаемому имени со звёздой не трогает оригинал`() = runBlocking {
        dao.insert(entry(subject = "ТОЭ", value = 8))

        dao.deleteBySubject("*ТОЭ")

        assertEquals(1, dao.entriesForSubject("ТОЭ").first().size)

        dao.deleteBySubject("ТОЭ")
        assertEquals(0, dao.entriesForSubject("ТОЭ").first().size)
    }
}
