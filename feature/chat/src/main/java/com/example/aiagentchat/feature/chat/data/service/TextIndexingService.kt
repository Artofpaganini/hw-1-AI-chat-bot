package com.example.aiagentchat.feature.chat.data.service

import android.content.Context
import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.OllamaApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

class TextIndexingService(
    private val context: Context,
    private val ollamaApi: OllamaApi,
    private val vectorJsonService: VectorJsonService,
    private val model: String = OllamaApi.DEFAULT_MODEL
) {
    companion object {
        private const val TAG = "TextIndexingService"
        // Требования: чанки размером 500-700 токенов (уменьшено для безопасности)
        private const val MIN_CHUNK_SIZE_TOKENS = 500
        private const val MAX_CHUNK_SIZE_TOKENS = 700
        // Требования: перекрытие 50-70 токенов (уменьшено для безопасности)
        private const val MIN_OVERLAP_TOKENS = 50
        private const val MAX_OVERLAP_TOKENS = 70
        // Используем средние значения для оптимального баланса
        private const val TARGET_CHUNK_SIZE_TOKENS = (MIN_CHUNK_SIZE_TOKENS + MAX_CHUNK_SIZE_TOKENS) / 2 // 600
        private const val TARGET_OVERLAP_TOKENS = (MIN_OVERLAP_TOKENS + MAX_OVERLAP_TOKENS) / 2 // 60
        // Более консервативная оценка токенов: 1 токен ≈ 4-5 символов (0.2 токенов на символ)
        // Это гарантирует, что чанки не превысят лимит модели
        private const val TOKENS_PER_CHAR = 0.2
        // Максимальный размер чанка в символах для дополнительной проверки
        private const val MAX_CHUNK_CHARS = (MAX_CHUNK_SIZE_TOKENS / TOKENS_PER_CHAR * 0.9).toInt() // ~3150 (90% от максимума для запаса)
    }

    private val _indexingProgress = MutableStateFlow<IndexingProgress?>(null)
    val indexingProgress: Flow<IndexingProgress?> = _indexingProgress.asStateFlow()

    data class IndexingProgress(
        val currentChunk: Int,
        val totalChunks: Int,
        val percentage: Float,
        val status: String
    )

    suspend fun indexFile(
        filePath: String,
        fileName: String = File(filePath).name
    ): Result<Unit> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $filePath"))
            }

            // Проверяем расширение файла
            val fileExtension = file.extension.lowercase()
            if (fileExtension !in listOf("md", "txt", "pdf")) {
                return Result.failure(Exception("Unsupported file type: $fileExtension. Supported types: .md, .txt, .pdf"))
            }

            // Читаем содержимое файла в зависимости от типа
            val fileContent = when (fileExtension) {
                "pdf" -> {
                    // Для PDF нужна специальная библиотека, пока возвращаем ошибку
                    return Result.failure(Exception("PDF support is not yet implemented. Please use .md or .txt files."))
                }
                else -> file.readText()
            }
            val fileHash = calculateFileHash(fileContent)

            Log.d(TAG, "Starting indexing for file: $fileName")
            Log.d(TAG, "File hash: $fileHash")

            if (vectorJsonService.hasDocument(fileName, fileHash)) {
                Log.d(TAG, "File already indexed with same hash, skipping")
                _indexingProgress.value = IndexingProgress(
                    currentChunk = 0,
                    totalChunks = 0,
                    percentage = 100f,
                    status = "Already indexed"
                )
                return Result.success(Unit)
            }

            val chunks = splitIntoChunks(fileContent)
            Log.i(TAG, "📄 File: $fileName")
            Log.i(TAG, "📊 Split into ${chunks.size} chunks")
            Log.i(TAG, "🚀 Starting vector indexing...")

            val vectorChunks = mutableListOf<VectorChunk>()

            chunks.forEachIndexed { index, chunk ->
                try {
                    _indexingProgress.value = IndexingProgress(
                        currentChunk = index + 1,
                        totalChunks = chunks.size,
                        percentage = ((index + 1).toFloat() / chunks.size) * 100f,
                        status = "Processing chunk ${index + 1}/${chunks.size}"
                    )

                    val embedding = generateEmbedding(chunk.text)
                    // Нормализуем векторы к диапазону [0,1]
                    val normalizedEmbedding = normalizeVector(embedding)
                    
                    // Проверяем, что нормализация работает корректно
                    val minVal = normalizedEmbedding.minOrNull() ?: 0f
                    val maxVal = normalizedEmbedding.maxOrNull() ?: 1f
                    Log.d(TAG, "Embedding normalized: min=$minVal, max=$maxVal, dimensions=${normalizedEmbedding.size}")

                    vectorChunks.add(
                        VectorChunk(
                            text = chunk.text,
                            embedding = normalizedEmbedding,
                            chunkIndex = index
                        )
                    )

                    val progressPercent = ((index + 1).toFloat() / chunks.size * 100).toInt()
                    Log.i(TAG, "✅ Indexed chunk ${index + 1}/${chunks.size} ($progressPercent%)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error indexing chunk ${index + 1}", e)
                    throw e
                }
            }

            val document = IndexedDocument(
                fileName = fileName,
                fileHash = fileHash,
                chunks = vectorChunks
            )

            // Сохраняем документ с хешем файла в JSON
            vectorJsonService.addDocument(document)
            Log.i(TAG, "💾 Saved document to JSON: $fileName (hash: $fileHash)")

            _indexingProgress.value = IndexingProgress(
                currentChunk = chunks.size,
                totalChunks = chunks.size,
                percentage = 100f,
                status = "Indexing completed"
            )

            Log.i(TAG, "🎉 Indexing completed successfully for file: $fileName")
            Log.i(TAG, "📈 Total chunks indexed: ${vectorChunks.size}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing file", e)
            _indexingProgress.value = null
            Result.failure(e)
        }
    }

    suspend fun checkIfIndexingNeeded(
        filePath: String,
        fileName: String = File(filePath).name
    ): Boolean {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "File does not exist: $filePath")
            return false
        }

        val fileContent = file.readText()
        val fileHash = calculateFileHash(fileContent)

        val index = vectorJsonService.loadVectorIndex()
        val hasDocument = vectorJsonService.hasDocument(fileName, fileHash)

        return index.documents.isEmpty() || !hasDocument
    }

    private fun splitIntoChunks(text: String): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        // Используем целевой размер (600 токенов) для оптимального баланса
        val targetCharsPerChunk = (TARGET_CHUNK_SIZE_TOKENS / TOKENS_PER_CHAR).toInt() // ~3000 символов для 600 токенов
        val overlapChars = (TARGET_OVERLAP_TOKENS / TOKENS_PER_CHAR).toInt() // ~300 символов для 60 токенов
        
        Log.i(TAG, "Splitting text into chunks:")
        Log.i(TAG, "  Target chunk size: $TARGET_CHUNK_SIZE_TOKENS tokens (~$targetCharsPerChunk chars)")
        Log.i(TAG, "  Overlap: $TARGET_OVERLAP_TOKENS tokens (~$overlapChars chars)")
        Log.i(TAG, "  Range: $MIN_CHUNK_SIZE_TOKENS-$MAX_CHUNK_SIZE_TOKENS tokens")
        Log.i(TAG, "  Overlap range: $MIN_OVERLAP_TOKENS-$MAX_OVERLAP_TOKENS tokens")
        Log.i(TAG, "  Max chunk chars (with safety margin): $MAX_CHUNK_CHARS")

        var startIndex = 0
        var chunkIndex = 0
        
        // Используем 90% от максимума для дополнительного запаса
        val safeMaxTokens = (MAX_CHUNK_SIZE_TOKENS * 0.9).toInt()

        while (startIndex < text.length) {
            // Вычисляем конец чанка
            var endIndex = minOf(startIndex + targetCharsPerChunk, text.length)
            
            // Если чанк слишком большой, уменьшаем его
            val chunkText = text.substring(startIndex, endIndex)
            val estimatedTokens = estimateTokenCount(chunkText)
            
            // Если оценка токенов превышает максимум, уменьшаем размер чанка
            if (estimatedTokens > safeMaxTokens) {
                Log.w(TAG, "Chunk $chunkIndex estimated tokens ($estimatedTokens) exceeds safe max ($safeMaxTokens), reducing size")
                // Уменьшаем размер пропорционально с дополнительным запасом
                val reductionFactor = safeMaxTokens.toFloat() / estimatedTokens
                val newSize = (chunkText.length * reductionFactor * 0.85).toInt() // 0.85 для дополнительного запаса
                endIndex = startIndex + newSize
            }
            
            val finalChunkText = text.substring(startIndex, endIndex)
            val finalEstimatedTokens = estimateTokenCount(finalChunkText)
            
            Log.d(TAG, "Chunk $chunkIndex: ${finalChunkText.length} chars, ~$finalEstimatedTokens tokens")
            
            // Проверяем, что чанк не превышает безопасный максимум (90% от MAX)
            if (finalEstimatedTokens > safeMaxTokens) {
                Log.e(TAG, "Chunk $chunkIndex still too large: $finalEstimatedTokens tokens (safe max: $safeMaxTokens). Splitting further...")
                // Разбиваем на более мелкие части
                val subChunks = splitLargeChunk(finalChunkText, chunkIndex)
                chunks.addAll(subChunks)
                chunkIndex += subChunks.size
            } else {
                chunks.add(TextChunk(text = finalChunkText, index = chunkIndex))
                chunkIndex++
            }

            if (endIndex >= text.length) break

            // Перекрытие: начинаем с позиции, отступая на overlapChars от конца
            startIndex = maxOf(startIndex + 1, endIndex - overlapChars)
        }

        Log.d(TAG, "Created ${chunks.size} chunks from text")
        chunks.forEachIndexed { index, chunk ->
            val tokens = estimateTokenCount(chunk.text)
            Log.d(TAG, "  Chunk $index: ${chunk.text.length} chars, ~$tokens tokens")
        }

        return chunks
    }
    
    private fun splitLargeChunk(text: String, baseIndex: Int): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        // Используем 70% от максимума для дополнительного запаса при разбиении больших чанков
        val maxChars = (MAX_CHUNK_SIZE_TOKENS / TOKENS_PER_CHAR * 0.7).toInt() // ~2450 символов для 700 токенов
        var start = 0
        var index = 0
        
        Log.d(TAG, "Splitting large chunk into smaller pieces (max ${maxChars} chars per piece)")
        
        while (start < text.length) {
            val end = minOf(start + maxChars, text.length)
            val chunkText = text.substring(start, end)
            val estimatedTokens = estimateTokenCount(chunkText)
            Log.d(TAG, "  Sub-chunk ${baseIndex + index}: ${chunkText.length} chars, ~$estimatedTokens tokens")
            chunks.add(TextChunk(text = chunkText, index = baseIndex + index))
            start = end
            index++
        }
        
        return chunks
    }
    
    private fun estimateTokenCount(text: String): Int {
        // Консервативная оценка токенов: для английского текста обычно 1 токен ≈ 4-5 символов
        // Используем более консервативную оценку для безопасности
        val chars = text.length
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        
        // Используем два метода оценки и берем максимум для безопасности
        val charBasedEstimate = (chars * TOKENS_PER_CHAR).toInt() // 0.2 токенов на символ = 5 символов на токен
        val wordBasedEstimate = (words * 1.2).toInt() // Обычно 1 слово ≈ 1-1.5 токена, используем 1.2 для запаса
        
        // Берем максимум для безопасности (чтобы не превысить лимит)
        val estimate = maxOf(charBasedEstimate, wordBasedEstimate)
        
        // Добавляем дополнительный запас (10%) для учета специальных символов и форматирования
        return (estimate * 1.1).toInt().coerceAtMost(MAX_CHUNK_SIZE_TOKENS)
    }

    private suspend fun generateEmbedding(text: String): List<Float> {
        // Проверяем размер текста перед отправкой
        val estimatedTokens = estimateTokenCount(text)
        
        // Используем более строгую проверку: не более 90% от максимума для запаса
        val maxAllowedTokens = (MAX_CHUNK_SIZE_TOKENS * 0.9).toInt()
        
        if (estimatedTokens > maxAllowedTokens) {
            val errorMsg = "Chunk size ($estimatedTokens tokens) exceeds safe maximum ($maxAllowedTokens tokens, 90% of $MAX_CHUNK_SIZE_TOKENS). Text length: ${text.length} chars"
            Log.e(TAG, errorMsg)
            Log.e(TAG, "Text preview (first 200 chars): ${text.take(200)}...")
            throw Exception(errorMsg)
        }
        
        if (estimatedTokens > MAX_CHUNK_SIZE_TOKENS) {
            Log.w(TAG, "Warning: Estimated tokens ($estimatedTokens) exceeds max ($MAX_CHUNK_SIZE_TOKENS), but within safe limit")
        }
        
        Log.d(TAG, "Generating embedding for text: ${text.length} chars, ~$estimatedTokens tokens (max allowed: $maxAllowedTokens)")
        val request = com.example.aiagentchat.feature.chat.data.api.OllamaEmbedRequest(
            model = model,
            input = text
        )

        Log.d(TAG, "Generating embedding for text (length: ${text.length} chars) using model: $model")
        
        try {
            val response = ollamaApi.generateEmbedding(request)
            
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                val errorMsg = "Failed to generate embedding: HTTP ${response.code()} - $errorBody"
                Log.e(TAG, errorMsg)
                Log.e(TAG, "Check if Ollama server is running at: http://10.0.2.2:11434")
                Log.e(TAG, "For emulator: http://10.0.2.2:11434")
                Log.e(TAG, "Test connection: curl http://10.0.2.2:11434/api/tags")
                throw Exception(errorMsg)
            }

            val responseBody = response.body()
            if (responseBody == null) {
                val errorMsg = "Empty response body from Ollama"
                Log.e(TAG, errorMsg)
                throw Exception(errorMsg)
            }

            val embeddings = responseBody.embeddings
            if (embeddings.isEmpty()) {
                val errorMsg = "Empty embedding response from Ollama"
                Log.e(TAG, errorMsg)
                throw Exception(errorMsg)
            }

            val embedding = embeddings[0]
            Log.d(TAG, "✅ Generated embedding with ${embedding.size} dimensions")
            return embedding
        } catch (e: java.net.UnknownHostException) {
            val errorMsg = "Cannot connect to Ollama server at http://10.0.2.2:11434. Make sure Ollama is running on your Mac."
            Log.e(TAG, errorMsg, e)
            throw Exception(errorMsg, e)
        } catch (e: java.net.ConnectException) {
            val errorMsg = "Connection refused to Ollama server. Is Ollama running? Test: curl http://localhost:11434/api/tags"
            Log.e(TAG, errorMsg, e)
            throw Exception(errorMsg, e)
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "Timeout connecting to Ollama server. Check network connection."
            Log.e(TAG, errorMsg, e)
            throw Exception(errorMsg, e)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding", e)
            throw e
        }
    }

    private fun normalizeVector(vector: List<Float>): List<Float> {
        val min = vector.minOrNull() ?: 0f
        val max = vector.maxOrNull() ?: 1f
        val range = max - min

        return if (range > 0) {
            vector.map { (it - min) / range }
        } else {
            vector.map { 0.5f }
        }
    }

    private fun calculateFileHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private data class TextChunk(
        val text: String,
        val index: Int
    )
}

