package com.example.aiagentchat.feature.chat.domain.usecase

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository

class SendMessageUseCase(
    private val aiModelRepository: AiModelRepository,
    private val metricsRepository: MetricsRepository
) {
    companion object {
        private const val TAG = "SendMessageUseCase"
    }

    suspend operator fun invoke(
        model: AiModel,
        messages: List<ChatMessageDto>
    ): Result<Message> {
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.d(TAG, "SendMessageUseCase invoked")
            
            val response = aiModelRepository.sendMessage(model, messages, null)
            
            when {
                response.isSuccess -> {
                    val aiResponse = response.getOrNull() ?: return Result.failure(Exception("Empty response"))
                    Log.d(TAG, "AI response received. Content length: ${aiResponse.content.length}")
                    
                    val responseTimeMs = System.currentTimeMillis() - startTime
                    val metrics = metricsRepository.calculateMetrics(
                        model = model,
                        responseTimeMs = responseTimeMs,
                        inputTokens = aiResponse.inputTokens,
                        outputTokens = aiResponse.outputTokens
                    )
                    Result.success(
                        Message(
                            content = aiResponse.content.ifEmpty { "No response content" },
                            isUser = false,
                            model = model,
                            metrics = metrics
                        )
                    )
                }
                else -> {
                    val error = response.exceptionOrNull()
                    Log.e(TAG, "Error from AI model", error)
                    Result.failure(error ?: Exception("Unknown error"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in SendMessageUseCase", e)
            Result.failure(e)
        }
    }
}
