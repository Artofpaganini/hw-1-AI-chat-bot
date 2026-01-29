package com.example.aiagentchat.feature.chat.data.repository

import android.util.Log
import com.example.aiagentchat.core.database.dao.VectorDao
import com.example.aiagentchat.core.database.entity.VectorEntity
import com.example.aiagentchat.feature.chat.domain.repository.IndexedFile
import com.example.aiagentchat.feature.chat.domain.repository.VectorRepository
import kotlin.math.sqrt

class VectorRepositoryImpl(
    private val vectorDao: VectorDao
) : VectorRepository {
    companion object {
        private const val TAG = "VectorRepositoryImpl"
    }

    override suspend fun getVectorCount(): Int {
        return vectorDao.getVectorCount()
    }

    override suspend fun hasVectors(): Boolean {
        return vectorDao.hasAnyVectors() != null
    }

    override suspend fun findSimilarVectors(
        queryEmbedding: List<Float>,
        limit: Int
    ): List<VectorEntity> {
        val allVectors = vectorDao.getAllVectorsForSimilarity()
        if (allVectors.isEmpty()) {
            return emptyList()
        }

        val similarities = allVectors.map { vector ->
            val similarity = cosineSimilarity(queryEmbedding, vector.embedding)
            Pair(vector, similarity)
        }.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        Log.d(TAG, "Found ${similarities.size} similar vectors")
        return similarities
    }

    override suspend fun getIndexedFiles(): List<IndexedFile> {
        val indexedFiles = vectorDao.getIndexedFiles()
        return indexedFiles.map { IndexedFile(it.sourceFile, it.fileHash) }
    }

    override suspend fun deleteVectorsForFile(fileName: String) {
        vectorDao.deleteVectorsForFile(fileName)
    }

    private fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float {
        if (vec1.size != vec2.size) {
            return 0f
        }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) {
            dotProduct / denominator
        } else {
            0f
        }
    }
}



