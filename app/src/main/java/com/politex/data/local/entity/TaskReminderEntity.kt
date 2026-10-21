package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_reminders",
    indices = [Index("taskId")],
)
data class TaskReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val triggerAtMillis: Long,
)
