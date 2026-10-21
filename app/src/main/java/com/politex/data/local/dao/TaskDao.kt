package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.politex.data.local.entity.StudentTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM student_tasks WHERE groupName = :groupName ORDER BY isCompleted ASC, deadlineDate ASC, id DESC")
    fun getTasksForGroup(groupName: String): Flow<List<StudentTaskEntity>>

    @Query(
        "SELECT * FROM student_tasks WHERE groupName = :groupName AND subjectName = :subjectName ORDER BY isCompleted ASC, deadlineDate ASC",
    )
    fun getTasksForGroupAndSubject(
        groupName: String,
        subjectName: String,
    ): Flow<List<StudentTaskEntity>>

    @Query("SELECT * FROM student_tasks WHERE groupName = :groupName AND isCompleted = 0 ORDER BY deadlineDate ASC")
    fun getPendingTasksForGroup(groupName: String): Flow<List<StudentTaskEntity>>

    @Query("SELECT * FROM student_tasks ORDER BY id ASC")
    suspend fun getAllTasks(): List<StudentTaskEntity>

    @Query("SELECT * FROM student_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): StudentTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: StudentTaskEntity): Long

    @Update
    suspend fun updateTask(task: StudentTaskEntity)

    @Query("UPDATE student_tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setTaskCompleted(
        id: Long,
        isCompleted: Boolean,
    )

    @Delete
    suspend fun deleteTask(task: StudentTaskEntity)

    @Query("DELETE FROM student_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM student_tasks WHERE groupName = :groupName")
    suspend fun clearTasksForGroup(groupName: String)
}
