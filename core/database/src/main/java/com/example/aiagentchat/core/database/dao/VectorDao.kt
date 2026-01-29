package com.example.aiagentchat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.aiagentchat.core.database.entity.VectorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VectorDao {
    @Query("SELECT COUNT(*) FROM vectors")
    suspend fun getVectorCount(): Int

    @Query("SELECT COUNT(*) FROM vectors WHERE source_file = :fileName AND file_hash = :fileHash")
    suspend fun getVectorCountForFile(fileName: String, fileHash: String): Int

    @Query("SELECT DISTINCT source_file AS sourceFile, file_hash AS fileHash FROM vectors")
    suspend fun getIndexedFiles(): List<IndexedFile>

    @Query("SELECT * FROM vectors WHERE source_file = :fileName AND file_hash = :fileHash")
    suspend fun getVectorsForFile(fileName: String, fileHash: String): List<VectorEntity>

    @Query("SELECT * FROM vectors")
    suspend fun getAllVectors(): List<VectorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVector(vector: VectorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVectors(vectors: List<VectorEntity>)

    @Query("DELETE FROM vectors WHERE source_file = :fileName")
    suspend fun deleteVectorsForFile(fileName: String)

    @Query("DELETE FROM vectors")
    suspend fun deleteAllVectors()

    @Query("SELECT * FROM vectors")
    suspend fun getAllVectorsForSimilarity(): List<VectorEntity>

    @Transaction
    @Query("SELECT * FROM vectors LIMIT 1")
    suspend fun hasAnyVectors(): VectorEntity?
}

data class IndexedFile(
    val sourceFile: String,
    val fileHash: String
)

