package com.example.aiagentchat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiagentchat.core.database.entity.ContextSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContextSummaryDao {
    @Query("SELECT * FROM context_summaries WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestSummaries(type: String, limit: Int = 5): Flow<List<ContextSummaryEntity>>

    @Query("SELECT * FROM context_summaries WHERE type = :type ORDER BY timestamp DESC")
    suspend fun getAllByType(type: String): List<ContextSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: ContextSummaryEntity)

    @Query("DELETE FROM context_summaries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

