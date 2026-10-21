package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.politex.data.local.entity.MaterialAnnotationEntity
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialFolderEntity
import com.politex.data.local.entity.MaterialItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Deprecated("W11: папки удалены из UI")
    @Query("SELECT * FROM material_folders ORDER BY sortOrder ASC, id ASC")
    fun getAllFolders(): Flow<List<MaterialFolderEntity>>

    @Deprecated("W11: папки удалены из UI")
    @Query("SELECT DISTINCT subjectName FROM material_folders ORDER BY subjectName ASC")
    fun getSubjects(): Flow<List<String>>

    @Deprecated("W11: папки удалены из UI")
    @Query("SELECT * FROM material_folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): MaterialFolderEntity?

    @Deprecated("W11: папки удалены из UI")
    @Query("SELECT * FROM material_folders WHERE isArchive = 1 LIMIT 1")
    suspend fun getArchiveFolder(): MaterialFolderEntity?

    @Deprecated("W11: папки удалены из UI")
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM material_folders WHERE subjectName = :subjectName")
    suspend fun nextFolderSortOrder(subjectName: String): Int

    @Deprecated("W11: папки удалены из UI")
    @Insert
    suspend fun insertFolder(folder: MaterialFolderEntity): Long

    @Deprecated("W11: папки удалены из UI")
    @Update
    suspend fun updateFolder(folder: MaterialFolderEntity)

    @Deprecated("W11: папки удалены из UI")
    @Query("DELETE FROM material_folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Query("SELECT * FROM material_items ORDER BY sortOrder ASC, id ASC")
    fun getAllItems(): Flow<List<MaterialItemEntity>>

    @Query(
        "SELECT * FROM material_items WHERE subjectName = :subject AND archived = :archived " +
            "ORDER BY sortOrder ASC, id ASC",
    )
    fun getForSubject(
        subject: String,
        archived: Boolean,
    ): Flow<List<MaterialItemEntity>>

    @Query("SELECT * FROM material_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Long): MaterialItemEntity?

    @Query("SELECT * FROM material_items WHERE contentHash = :hash AND contentHash != '' ORDER BY id ASC")
    suspend fun getItemsByContentHash(hash: String): List<MaterialItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM material_items WHERE subjectName = :subject AND archived = 0")
    suspend fun nextItemSortOrder(subject: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM material_items WHERE archived = 1")
    suspend fun nextArchiveSortOrder(): Int

    @Insert
    suspend fun insertItem(item: MaterialItemEntity): Long

    @Update
    suspend fun updateItem(item: MaterialItemEntity)

    @Query("DELETE FROM material_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Deprecated("W11: перенос между папками заменён setArchived")
    @Query("UPDATE material_items SET folderId = :folderId, sortOrder = :sortOrder WHERE id = :itemId")
    suspend fun moveItemToFolder(
        itemId: Long,
        folderId: Long,
        sortOrder: Int,
    )

    @Query("UPDATE material_items SET archived = :archived, sortOrder = :sortOrder WHERE id = :itemId")
    suspend fun setArchived(
        itemId: Long,
        archived: Boolean,
        sortOrder: Int,
    )

    @Query("UPDATE material_items SET title = :title WHERE id = :itemId")
    suspend fun renameItem(
        itemId: Long,
        title: String,
    )

    @Query("UPDATE material_items SET lastPage = :page WHERE id = :itemId")
    suspend fun setLastPage(
        itemId: Long,
        page: Int,
    )

    @Query("UPDATE material_items SET rotationDeg = :deg WHERE id = :itemId")
    suspend fun setRotationDeg(
        itemId: Long,
        deg: Int,
    )

    @Query("UPDATE material_items SET uriPath = :uriPath WHERE id = :itemId")
    suspend fun setImagePaths(
        itemId: Long,
        uriPath: String,
    )

    @Query("UPDATE material_items SET sortOrder = :sortOrder WHERE id = :itemId")
    suspend fun updateSortOrder(
        itemId: Long,
        sortOrder: Int,
    )

    @Transaction
    suspend fun swapSortOrder(
        itemIdA: Long,
        itemIdB: Long,
    ) {
        val a = getItemById(itemIdA) ?: return
        val b = getItemById(itemIdB) ?: return
        if (a.sortOrder == b.sortOrder) return
        updateSortOrder(itemIdA, b.sortOrder)
        updateSortOrder(itemIdB, a.sortOrder)
    }

    @Query("SELECT * FROM material_bookmarks WHERE itemId = :itemId ORDER BY page ASC, id ASC")
    suspend fun getBookmarksForItem(itemId: Long): List<MaterialBookmarkEntity>

    @Query("SELECT * FROM material_bookmarks WHERE itemId = :itemId ORDER BY page ASC, id ASC")
    fun getBookmarksFlow(itemId: Long): Flow<List<MaterialBookmarkEntity>>

    @Insert
    suspend fun insertBookmark(bookmark: MaterialBookmarkEntity): Long

    @Query("DELETE FROM material_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: Long)

    @Query("DELETE FROM material_bookmarks WHERE itemId = :itemId")
    suspend fun deleteBookmarksForItem(itemId: Long)

    @Query("SELECT * FROM material_annotations WHERE itemId = :itemId")
    fun getAnnotationsFlow(itemId: Long): Flow<List<MaterialAnnotationEntity>>

    @Query("SELECT * FROM material_annotations WHERE itemId = :itemId AND pageIndex = :pageIndex")
    suspend fun getAnnotation(itemId: Long, pageIndex: Int): MaterialAnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotation(annotation: MaterialAnnotationEntity)

    @Query("DELETE FROM material_annotations WHERE itemId = :itemId AND pageIndex = :pageIndex")
    suspend fun deletePageAnnotations(itemId: Long, pageIndex: Int)

    @Query("DELETE FROM material_annotations WHERE itemId = :itemId")
    suspend fun deleteAnnotationsForItem(itemId: Long)

    @Query("UPDATE material_items SET textContent = :text, textEdited = 1 WHERE id = :id")
    suspend fun updateTextContent(id: Long, text: String)

    @Transaction
    suspend fun deleteItemWithBookmarks(itemId: Long) {
        deleteBookmarksForItem(itemId)
        deleteAnnotationsForItem(itemId)
        deleteItemById(itemId)
    }
}
