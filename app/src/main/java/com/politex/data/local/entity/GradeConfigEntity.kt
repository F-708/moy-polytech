package com.politex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grade_config")
data class GradeConfigEntity(
    @PrimaryKey
    val subjectName: String,
    val priorityJson: String,

    @ColumnInfo(defaultValue = "''")
    val weightsJson: String = "",

    @ColumnInfo(defaultValue = "'0.1'")
    val roundingMode: String = "0.1",

    @ColumnInfo(defaultValue = "0")
    val roundUp: Boolean = false,

    @ColumnInfo(defaultValue = "1")
    val weightsEnabled: Boolean = true,
)
