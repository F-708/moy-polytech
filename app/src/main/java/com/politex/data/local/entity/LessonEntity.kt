package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.politex.util.SubjectFormatter

@Entity(
    tableName = "lessons",
    indices = [
        Index("groupName", "dayOfWeek"),
        Index("groupName", "dateString"),
        Index("dateString"),
        Index("roomFirst"),
        Index("roomSecond"),
    ],
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupName: String,
    val dayOfWeek: Int,
    val lessonNumber: Int,
    val timeStart: String,
    val timeEnd: String,
    val subjectRaw: String,
    val roomFirst: String,
    val teacherFirst: String,
    val roomSecond: String = "",
    val teacherSecond: String = "",
    val isSplit: Boolean = false,
    val dateString: String = "",
) {
    @delegate:Ignore
    val shortSubjectName: String by lazy {
        SubjectFormatter.getShortName(subjectRaw)
    }

    @delegate:Ignore
    val subjectAcronym: String by lazy {
        SubjectFormatter.getAcronym(subjectRaw)
    }

    @delegate:Ignore
    val officialSubjectName: String by lazy {
        SubjectFormatter.normalize(subjectRaw)
    }

    @delegate:Ignore
    val timeRangeDisplay: String by lazy {
        if (timeStart.isNotBlank() && timeEnd.isNotBlank()) "$timeStart – $timeEnd" else ""
    }
}
