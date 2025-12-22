package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.core.database.entity.VectorEntity
import kotlinx.coroutines.flow.Flow

interface VectorRepository {
    suspend fun getVectorCount(): Int
    suspend fun hasVectors(): Boolean
    suspend fun findSimilarVectors(queryEmbedding: List<Float>, limit: Int = 5): List<VectorEntity>
    suspend fun getIndexedFiles(): List<IndexedFile>
    suspend fun deleteVectorsForFile(fileName: String)
}

data class IndexedFile(
    val sourceFile: String,
    val fileHash: String
)

