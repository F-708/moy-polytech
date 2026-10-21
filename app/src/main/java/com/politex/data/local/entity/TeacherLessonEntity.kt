package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "teacher_lessons",

    indices = [Index("surnameKey", "dateString")],
)
data class TeacherLessonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val teacherName: String,

    val surnameKey: String,
    val dateString: String,
    val dayOfWeek: Int,
    val lessonNumber: Int,

    val groups: String,
    val subject: String,
    val room: String,
)
