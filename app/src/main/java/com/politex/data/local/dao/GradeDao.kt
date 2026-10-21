package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.politex.data.local.entity.GradeConfigEntity
import com.politex.data.local.entity.GradeEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GradeDao {
    @Query("SELECT * FROM grade_entries")
    fun allEntries(): Flow<List<GradeEntryEntity>>

    @Query(
        "SELECT * FROM grade_entries WHERE subjectName = :subjectName " +
            "ORDER BY dateIso ASC, id ASC",
    )
    fun entriesForSubject(subjectName: String): Flow<List<GradeEntryEntity>>

    @Query("SELECT * FROM grade_config")
    fun allConfigs(): Flow<List<GradeConfigEntity>>

    @Query("SELECT * FROM grade_config WHERE subjectName = :subjectName")
    suspend fun getConfig(subjectName: String): GradeConfigEntity?

    @Insert
    suspend fun insert(entry: GradeEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: GradeConfigEntity)

    @Query("DELETE FROM grade_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM grade_entries WHERE subjectName = :subjectName")
    suspend fun deleteBySubject(subjectName: String)

    @Query("DELETE FROM grade_entries WHERE subjectName = :subjectName AND category = 'EXAM'")
    suspend fun deleteExam(subjectName: String)

    @Transaction
    suspend fun upsertExam(entry: GradeEntryEntity) {
        deleteExam(entry.subjectName)
        insert(entry)
    }
}
