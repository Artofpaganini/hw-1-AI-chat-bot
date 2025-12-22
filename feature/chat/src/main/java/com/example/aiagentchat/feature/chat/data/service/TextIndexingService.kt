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
        private const val CHUNK_SIZE_TOKENS = 750
        private const val OVERLAP_TOKENS = 75
        private const val TOKENS_PER_CHAR = 0.25
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

            val fileContent = file.readText()
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
                    val normalizedEmbedding = normalizeVector(embedding)

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

            vectorJsonService.addDocument(document)

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
        val estimatedCharsPerChunk = (CHUNK_SIZE_TOKENS / TOKENS_PER_CHAR).toInt()
        val overlapChars = (OVERLAP_TOKENS / TOKENS_PER_CHAR).toInt()

        var startIndex = 0
        var chunkIndex = 0

        while (startIndex < text.length) {
            val endIndex = minOf(startIndex + estimatedCharsPerChunk, text.length)
            val chunkText = text.substring(startIndex, endIndex)

            chunks.add(TextChunk(text = chunkText, index = chunkIndex))

            if (endIndex >= text.length) break

            startIndex = endIndex - overlapChars
            chunkIndex++
        }

        return chunks
    }

    private suspend fun generateEmbedding(text: String): List<Float> {
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

