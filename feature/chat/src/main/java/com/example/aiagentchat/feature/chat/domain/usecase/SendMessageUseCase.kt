package com.example.aiagentchat.feature.chat.domain.usecase

import android.util.Log
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.model.MessageMetrics
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto

class SendMessageUseCase(
    private val aiModelRepository: AiModelRepository,
    private val metricsRepository: MetricsRepository,
    private val mcpRepository: McpRepository
) {
    companion object {
        private const val TAG = "SendMessageUseCase"
    }

    suspend operator fun invoke(
        model: AiModel,
        messages: List<ChatMessageDto>,
        enabledMcpTools: Set<String> = emptySet()
    ): Result<Message> {
        val startTime = System.currentTimeMillis()
        
        val finalMessages = if (enabledMcpTools.contains("get_weather") && messages.isNotEmpty()) {
            val lastUserMessage = messages.lastOrNull()
            if (lastUserMessage != null && lastUserMessage.role == "user") {
                val userQuery = lastUserMessage.content.trim()
                if (userQuery.isNotBlank()) {
                    val weatherResult = mcpRepository.callTool(
                        toolName = "get_weather",
                        arguments = mapOf("location" to userQuery)
                    )
                    
                    return when {
                        weatherResult.isSuccess -> {
                            val weatherData = weatherResult.getOrNull() ?: ""
                            Log.d(TAG, "Weather data retrieved: $weatherData")
                            val updatedMessages = messages.dropLast(1) + listOf(
                                lastUserMessage,
                                ChatMessageDto(
                                    role = "system",
                                    content = "Weather information: $weatherData. Please provide a concise response (maximum 2 sentences) based on this weather data."
                                )
                            )
                            sendToAiModel(model, updatedMessages, startTime)
                        }
                        else -> {
                            val error = weatherResult.exceptionOrNull()
                            Log.e(TAG, "Failed to get weather data", error)
                            val errorMessage = "Failed to retrieve weather information: ${error?.message ?: "Unknown error"}"
                            val updatedMessages = messages.dropLast(1) + listOf(
                                lastUserMessage,
                                ChatMessageDto(
                                    role = "system",
                                    content = errorMessage
                                )
                            )
                            sendToAiModel(model, updatedMessages, startTime)
                        }
                    }
                }
            }
            messages
        } else {
            messages
        }
        
        return sendToAiModel(model, finalMessages, startTime)
    }
    
    private suspend fun sendToAiModel(
        model: AiModel,
        messages: List<ChatMessageDto>,
        startTime: Long
    ): Result<Message> {
        return aiModelRepository.sendMessage(model, messages).map { response ->
            val responseTimeMs = System.currentTimeMillis() - startTime
            val metrics = metricsRepository.calculateMetrics(
                model = model,
                responseTimeMs = responseTimeMs,
                inputTokens = response.inputTokens,
                outputTokens = response.outputTokens
            )
            Message(
                content = response.content,
                isUser = false,
                model = model,
                metrics = metrics
            )
        }
    }
}

