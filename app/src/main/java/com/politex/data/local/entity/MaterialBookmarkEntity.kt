package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "material_bookmarks",
    indices = [Index("itemId")],
)
data class MaterialBookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val page: Int,
    val label: String = "",
)
