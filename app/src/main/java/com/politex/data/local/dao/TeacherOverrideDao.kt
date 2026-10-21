package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.politex.data.local.entity.TeacherOverrideEntity

@Dao
interface TeacherOverrideDao {
    @Query("SELECT * FROM teacher_overrides WHERE dateString = :dateString")
    suspend fun forDate(dateString: String): List<TeacherOverrideEntity>

    @Query("SELECT * FROM teacher_overrides")
    suspend fun all(): List<TeacherOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TeacherOverrideEntity)

    @Query("DELETE FROM teacher_overrides WHERE dateString = :dateString AND lessonNumber = :lessonNumber")
    suspend fun deleteForLesson(
        dateString: String,
        lessonNumber: Int,
    )

    @Query("DELETE FROM teacher_overrides WHERE dateString = :dateString")
    suspend fun deleteForDate(dateString: String)
}
