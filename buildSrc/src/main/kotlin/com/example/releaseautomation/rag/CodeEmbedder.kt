package com.example.releaseautomation.rag

import java.util.logging.Logger

/**
 * Упрощенная реализация генерации embeddings через TF-IDF like хеширование.
 * В production рекомендуется использовать ONNX Runtime с моделью sentence-transformers.
 */
class CodeEmbedder {
    private val logger = Logger.getLogger(CodeEmbedder::class.java.name)
    private val embeddingSize = 384
    
    /**
     * Генерирует embedding для текста кода.
     * Использует упрощенный алгоритм на основе хеширования слов.
     */
    fun generateEmbedding(text: String): FloatArray {
        // Нормализуем текст
        val normalized = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        
        // Создаем вектор на основе хешей слов
        val vector = FloatArray(embeddingSize) { 0f }
        val wordCounts = mutableMapOf<String, Int>()
        
        normalized.forEach { word ->
            wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
        }
        
        // Распределяем веса по вектору на основе хешей
        wordCounts.forEach { (word, count) ->
            val hash = word.hashCode()
            val index = Math.abs(hash) % embeddingSize
            val weight = count.toFloat() / normalized.size
            vector[index] += weight
        }
        
        // Нормализуем вектор
        var sumSquares = 0.0
        for (value in vector) {
            sumSquares += value * value
        }
        val magnitude = kotlin.math.sqrt(sumSquares).toFloat()
        if (magnitude > 0) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }
        
        return vector
    }
    
    fun toByteArray(embedding: FloatArray): ByteArray {
        val bytes = ByteArray(embedding.size * 4)
        for (i in embedding.indices) {
            val bits = java.lang.Float.floatToIntBits(embedding[i])
            bytes[i * 4] = (bits shr 24).toByte()
            bytes[i * 4 + 1] = (bits shr 16).toByte()
            bytes[i * 4 + 2] = (bits shr 8).toByte()
            bytes[i * 4 + 3] = bits.toByte()
        }
        return bytes
    }
    
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) {
            val byte1 = bytes[i * 4].toInt() and 0xFF
            val byte2 = bytes[i * 4 + 1].toInt() and 0xFF
            val byte3 = bytes[i * 4 + 2].toInt() and 0xFF
            val byte4 = bytes[i * 4 + 3].toInt() and 0xFF
            val bits = (byte1 shl 24) or (byte2 shl 16) or (byte3 shl 8) or byte4
            floats[i] = java.lang.Float.intBitsToFloat(bits)
        }
        return floats
    }
    
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same size" }
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = kotlin.math.sqrt(normA * normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
}
