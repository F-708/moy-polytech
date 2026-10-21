package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.politex.data.local.entity.LessonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE groupName = :groupName AND dayOfWeek = :dayOfWeek ORDER BY lessonNumber ASC")
    fun getLessonsForDay(
        groupName: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE groupName = :groupName AND dateString = :dateString ORDER BY lessonNumber ASC")
    fun getLessonsForDate(
        groupName: String,
        dateString: String,
    ): Flow<List<LessonEntity>>

    @Query(
        """
        SELECT * FROM lessons
        WHERE groupName = :groupName
          AND (
            (dateString != '' AND dateString = :dateString)
            OR (dateString = '' AND dayOfWeek = :dayOfWeek)
          )
        ORDER BY lessonNumber ASC
    """,
    )
    fun getLessonsForDateOrDay(
        groupName: String,
        dateString: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>>

    @Query(
        """
        SELECT * FROM lessons
        WHERE (roomFirst = :room OR roomSecond = :room)
          AND (
            (dateString != '' AND dateString = :dateString)
            OR (dateString = '' AND dayOfWeek = :dayOfWeek)
          )
        ORDER BY lessonNumber ASC
    """,
    )
    fun getLessonsForRoomOnDate(
        room: String,
        dateString: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE groupName = :groupName ORDER BY dayOfWeek ASC, lessonNumber ASC")
    fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons")
    suspend fun getAllLessonsSync(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE groupName = :groupName AND dayOfWeek = :dayOfWeek ORDER BY lessonNumber ASC")
    suspend fun getLessonsForDaySync(
        groupName: String,
        dayOfWeek: Int,
    ): List<LessonEntity>

    @Query(
        """
        SELECT DISTINCT groupName FROM lessons
        WHERE (dateString != '' AND dateString = :dateString)
           OR (dateString = '' AND dayOfWeek = :dayOfWeek)
        ORDER BY groupName ASC
    """,
    )
    suspend fun getDistinctGroupsForDateOrDay(
        dateString: String,
        dayOfWeek: Int,
    ): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLesson(lesson: LessonEntity): Long

    @Query("DELETE FROM lessons WHERE groupName = :groupName")
    suspend fun clearLessonsForGroup(groupName: String)

    @Query("DELETE FROM lessons WHERE groupName = :groupName AND dateString = :dateString")
    suspend fun deleteLessonsForDate(
        groupName: String,
        dateString: String,
    )

    @Query("DELETE FROM lessons WHERE groupName = :groupName AND dayOfWeek = :dayOfWeek")
    suspend fun deleteLessonsForDay(
        groupName: String,
        dayOfWeek: Int,
    )

    @Query(
        """
        SELECT DISTINCT dateString FROM lessons
        WHERE groupName = :groupName AND dateString != ''
        ORDER BY substr(dateString, 7, 4) DESC, substr(dateString, 4, 2) DESC, substr(dateString, 1, 2) DESC
    """,
    )
    fun getDistinctDatesForGroup(groupName: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM lessons WHERE groupName = :groupName")
    suspend fun getLessonCountForGroup(groupName: String): Int

    @Query("SELECT DISTINCT dateString FROM lessons WHERE dateString != ''")
    suspend fun getAllDistinctDatesSync(): List<String>

    @Query("SELECT COUNT(*) FROM lessons WHERE groupName = :groupName AND dateString = :dateString")
    suspend fun getLessonCountForDate(
        groupName: String,
        dateString: String,
    ): Int

    @Query("DELETE FROM lessons WHERE dateString = :dateString")
    suspend fun deleteLessonsByDate(dateString: String)

    @Query("DELETE FROM lessons WHERE dateString IN (:dates)")
    suspend fun deleteLessonsByDates(dates: List<String>)

    @Transaction
    suspend fun replaceScheduleForGroup(
        groupName: String,
        newLessons: List<LessonEntity>,
    ) {
        clearLessonsForGroup(groupName)
        insertLessons(newLessons)
    }

    @Transaction
    suspend fun replaceForDate(
        groupName: String,
        dateString: String,
        lessons: List<LessonEntity>,
    ) {
        deleteLessonsForDate(groupName, dateString)
        insertLessons(lessons)
    }
}
