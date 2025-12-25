package com.example.aiagentchat.core.database.util

import com.example.aiagentchat.core.database.entity.BookChunkEntity

/**
 * Вычисляет косинусное сходство между двумя векторами
 * @param vector1 первый вектор (нормализованный)
 * @param vector2 второй вектор (нормализованный)
 * @return косинусное сходство в диапазоне [-1, 1]
 */
fun cosineSimilarity(vector1: List<Float>, vector2: List<Float>): Float {
    if (vector1.size != vector2.size) {
        return 0f
    }
    
    var dotProduct = 0f
    var norm1 = 0f
    var norm2 = 0f
    
    for (i in vector1.indices) {
        dotProduct += vector1[i] * vector2[i]
        norm1 += vector1[i] * vector1[i]
        norm2 += vector2[i] * vector2[i]
    }
    
    val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
    return if (denominator == 0f) 0f else dotProduct / denominator
}

/**
 * Находит наиболее похожие чанки по косинусному сходству
 * @param queryEmbedding вектор запроса (нормализованный)
 * @param chunks список чанков для поиска
 * @param limit максимальное количество результатов
 * @return список чанков с вычисленным similarity, отсортированный по убыванию
 */
fun findSimilarChunks(
    queryEmbedding: List<Float>,
    chunks: List<BookChunkEntity>,
    limit: Int = 10
): List<BookChunkWithSimilarity> {
    return chunks
        .map { chunk ->
            val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)
            BookChunkWithSimilarity(
                chunkId = chunk.chunkId,
                bookId = chunk.bookId,
                chunkIndex = chunk.chunkIndex,
                chunkText = chunk.chunkText,
                embedding = chunk.embedding,
                createdAt = chunk.createdAt,
                similarity = similarity
            )
        }
        .sortedByDescending { it.similarity }
        .take(limit)
}

data class BookChunkWithSimilarity(
    val chunkId: Long,
    val bookId: Long,
    val chunkIndex: Int,
    val chunkText: String,
    val embedding: List<Float>,
    val createdAt: Long,
    val similarity: Float
)

