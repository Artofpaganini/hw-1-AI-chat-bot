package com.example.aiagentchat.feature.chat.domain.usecase

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.OllamaApi
import com.example.aiagentchat.feature.chat.data.api.OllamaChatMessage
import com.example.aiagentchat.feature.chat.data.api.OllamaChatRequest
import com.example.aiagentchat.feature.chat.data.service.MatchedChunk
import com.example.aiagentchat.feature.chat.data.service.VectorJsonService
import com.example.aiagentchat.feature.chat.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RagResponse(
    val content: String,
    val matchedChunks: List<ChunkInfo>,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

data class ChunkInfo(
    val chunkIndex: Int,
    val summary: String,
    val text: String
)

class SendRagMessageUseCase(
    private val ollamaApi: OllamaApi,
    private val vectorJsonService: VectorJsonService
) {
    companion object {
        private const val TAG = "SendRagMessageUseCase"
        private const val DEFAULT_CHAT_MODEL = OllamaApi.DEFAULT_CHAT_MODEL
    }

    suspend operator fun invoke(
        query: String,
        queryEmbedding: List<Float>,
        matchedChunks: List<MatchedChunk>,
        chatModel: String = DEFAULT_CHAT_MODEL
    ): Result<RagResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (matchedChunks.isEmpty()) {
                    Log.w(TAG, "No matched chunks found for query")
                    return@withContext Result.failure(Exception("No relevant chunks found"))
                }

                Log.d(TAG, "Processing RAG request with ${matchedChunks.size} matched chunks")

                val contextText = matchedChunks.joinToString("\n\n---\n\n") { it.text }
                val enhancedPrompt = buildString {
                    appendLine("Based on the following context from indexed documents, please answer the user's question:")
                    appendLine()
                    appendLine("=== RELEVANT CONTEXT ===")
                    appendLine(contextText)
                    appendLine("=== END OF CONTEXT ===")
                    appendLine()
                    appendLine("=== USER QUESTION ===")
                    appendLine(query)
                    appendLine("=== END OF QUESTION ===")
                    appendLine()
                    appendLine("Please provide a clear and accurate answer based on the context provided.")
                }

                val messages = listOf(
                    OllamaChatMessage(
                        role = "user",
                        content = enhancedPrompt
                    )
                )

                val request = OllamaChatRequest(
                    model = chatModel,
                    messages = messages,
                    stream = false
                )

                Log.d(TAG, "Sending request to Ollama chat API with model: $chatModel")
                val response = ollamaApi.generateChat(request)

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    val errorMsg = "Failed to generate chat response: HTTP ${response.code()} - $errorBody"
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val responseBody = response.body()
                if (responseBody == null || responseBody.message == null) {
                    val errorMsg = "Empty response body from Ollama"
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val content = responseBody.message.content
                if (content.isBlank()) {
                    val errorMsg = "Empty content in response from Ollama"
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                Log.d(TAG, "✅ Generated response from Ollama (${content.length} chars)")

                val chunkInfos = generateChunkSummaries(matchedChunks, chatModel)

                val ragResponse = RagResponse(
                    content = content,
                    matchedChunks = chunkInfos,
                    inputTokens = responseBody.promptEvalCount ?: 0,
                    outputTokens = responseBody.evalCount ?: 0
                )

                Result.success(ragResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Error in SendRagMessageUseCase", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun generateChunkSummaries(
        matchedChunks: List<MatchedChunk>,
        chatModel: String
    ): List<ChunkInfo> {
        return try {
            matchedChunks.map { chunk ->
                val summary = generateSummaryForChunk(chunk.text, chatModel)
                ChunkInfo(
                    chunkIndex = chunk.chunkIndex,
                    summary = summary,
                    text = chunk.text
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating chunk summaries", e)
            matchedChunks.map { chunk ->
                ChunkInfo(
                    chunkIndex = chunk.chunkIndex,
                    summary = "Summary generation failed",
                    text = chunk.text
                )
            }
        }
    }

    private suspend fun generateSummaryForChunk(chunkText: String, chatModel: String): String {
        return try {
            val summaryPrompt = buildString {
                appendLine("Provide a brief summary (1-2 sentences) of the following text:")
                appendLine()
                appendLine(chunkText.take(500))
                appendLine()
                appendLine("Summary:")
            }

            val messages = listOf(
                OllamaChatMessage(
                    role = "user",
                    content = summaryPrompt
                )
            )

            val request = OllamaChatRequest(
                model = chatModel,
                messages = messages,
                stream = false
            )

            val response = ollamaApi.generateChat(request)

            if (response.isSuccessful && response.body()?.message != null) {
                val summary = response.body()!!.message!!.content.trim()
                if (summary.isNotBlank()) {
                    return summary
                }
            }

            fallbackSummary(chunkText)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating summary for chunk", e)
            fallbackSummary(chunkText)
        }
    }

    private fun fallbackSummary(chunkText: String): String {
        val firstSentence = chunkText.split(". ").firstOrNull() ?: chunkText.take(100)
        return if (firstSentence.length > 150) {
            firstSentence.take(150) + "..."
        } else {
            firstSentence
        }
    }
}

