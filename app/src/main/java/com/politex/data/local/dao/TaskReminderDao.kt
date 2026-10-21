package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.politex.data.local.entity.TaskReminderEntity

@Dao
interface TaskReminderDao {
    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId ORDER BY triggerAtMillis ASC")
    suspend fun getForTask(taskId: Long): List<TaskReminderEntity>

    @Query("SELECT * FROM task_reminders WHERE triggerAtMillis <= :untilMillis ORDER BY triggerAtMillis ASC")
    suspend fun getDueUntil(untilMillis: Long): List<TaskReminderEntity>

    @Insert
    suspend fun insertAll(reminders: List<TaskReminderEntity>)

    @Query("DELETE FROM task_reminders WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("DELETE FROM task_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    suspend fun replaceForTask(
        taskId: Long,
        reminders: List<TaskReminderEntity>,
    ) {
        deleteForTask(taskId)
        if (reminders.isNotEmpty()) insertAll(reminders)
    }
}
