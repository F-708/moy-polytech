package com.politex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LessonRoomDaoTest {
    private lateinit var db: MpkDatabase
    private lateinit var dao: LessonDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.lessonDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun lesson(
        group: String,
        number: Int,
        roomFirst: String,
        roomSecond: String = "",
        dateString: String = "21.10.2026",
        dayOfWeek: Int = 3,
        teacher: String = "Мешалкина И.В.",
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = dayOfWeek,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = "09:00",
        subjectRaw = "Предмет",
        roomFirst = roomFirst,
        teacherFirst = teacher,
        roomSecond = roomSecond,
        teacherSecond = "",
        isSplit = roomSecond.isNotBlank(),
        dateString = dateString,
    )

    @Test
    fun `запрос по кабинету учитывает roomSecond у split-урока`() = runBlocking {
        dao.insertLessons(
            listOf(
                lesson("23М", 7, roomFirst = "340", roomSecond = "339"),
                lesson("11А", 1, roomFirst = "101"),
            ),
        )
        val rows = dao.getLessonsForRoomOnDate("339", "21.10.2026", 3).first()
        assertEquals(listOf(7), rows.map { it.lessonNumber })
        assertEquals("23М", rows.single().groupName)
    }

    @Test
    fun `запрос по кабинету возвращает уроки всех групп на дату`() = runBlocking {
        dao.insertLessons(
            listOf(
                lesson("41Н", 3, roomFirst = "339"),
                lesson("41Н", 4, roomFirst = "339"),
                lesson("21М", 5, roomFirst = "339"),
                lesson("23М", 7, roomFirst = "339"),
                lesson("11А", 2, roomFirst = "215"),
            ),
        )
        val rows = dao.getLessonsForRoomOnDate("339", "21.10.2026", 3).first()
        assertEquals(listOf(3, 4, 5, 7), rows.map { it.lessonNumber })
        assertEquals(listOf("41Н", "41Н", "21М", "23М"), rows.map { it.groupName })
    }

    @Test
    fun `кабинет 339x340 сплитом виден по обоим номерам`() = runBlocking {
        dao.insertLessons(listOf(lesson("23М", 7, roomFirst = "339", roomSecond = "340")))
        assertEquals(1, dao.getLessonsForRoomOnDate("339", "21.10.2026", 3).first().size)
        assertEquals(1, dao.getLessonsForRoomOnDate("340", "21.10.2026", 3).first().size)
    }

    @Test
    fun `недельный кэш без даты отдаётся по dayOfWeek когда точных записей нет`() = runBlocking {
        dao.insertLessons(
            listOf(
                lesson("41Н", 3, roomFirst = "339", dateString = "", dayOfWeek = 3),
                lesson("21М", 5, roomFirst = "339", dateString = "", dayOfWeek = 4),
            ),
        )
        val rows = dao.getLessonsForRoomOnDate("339", "21.10.2026", 3).first()
        assertEquals(listOf(3), rows.map { it.lessonNumber })
    }

    @Test
    fun `чужая дата не отдаётся`() = runBlocking {
        dao.insertLessons(
            listOf(
                lesson("41Н", 3, roomFirst = "339", dateString = "20.10.2026", dayOfWeek = 2),
            ),
        )
        val rows = dao.getLessonsForRoomOnDate("339", "21.10.2026", 3).first()
        assertEquals(emptyList<Int>(), rows.map { it.lessonNumber })
    }
}
