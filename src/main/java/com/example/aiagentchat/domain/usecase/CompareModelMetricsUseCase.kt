package com.example.aiagentchat.domain.usecase

import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.Message

data class MetricsComparison(
    val prompt: String,
    val deepSeekMessage: Message?,
    val zaiMessage: Message?,
    val timeDifferenceMs: Long?,
    val costDifferenceUsd: Double?,
    val tokensDifference: Int?
) {
    val isComplete: Boolean
        get() = deepSeekMessage != null && zaiMessage != null
}

class CompareModelMetricsUseCase {
    operator fun invoke(messages: List<Message>, prompt: String): MetricsComparison? {
        val userMessages = messages.filter { it.isUser && it.content == prompt }
        if (userMessages.isEmpty()) return null
        
        val lastUserMessageTimestamp = userMessages.maxOf { it.timestamp }
        
        // Ищем ответы на этот конкретный запрос (после последнего сообщения пользователя)
        val responses = messages.filter { 
            !it.isUser && it.timestamp > lastUserMessageTimestamp 
        }
        
        val deepSeekResponse = responses.find { it.model is AiModel.DeepSeek }
        val zaiResponse = responses.find { it.model is AiModel.Zai }
        
        if (deepSeekResponse == null && zaiResponse == null) return null
        
        val timeDiff = if (deepSeekResponse?.metrics != null && zaiResponse?.metrics != null) {
            deepSeekResponse.metrics.responseTimeMs - zaiResponse.metrics.responseTimeMs
        } else null
        
        val costDiff = if (deepSeekResponse?.metrics != null && zaiResponse?.metrics != null) {
            deepSeekResponse.metrics.costUsd - zaiResponse.metrics.costUsd
        } else null
        
        val tokensDiff = if (deepSeekResponse?.metrics != null && zaiResponse?.metrics != null) {
            (deepSeekResponse.metrics.inputTokens + deepSeekResponse.metrics.outputTokens) -
            (zaiResponse.metrics.inputTokens + zaiResponse.metrics.outputTokens)
        } else null
        
        return MetricsComparison(
            prompt = prompt,
            deepSeekMessage = deepSeekResponse,
            zaiMessage = zaiResponse,
            timeDifferenceMs = timeDiff,
            costDifferenceUsd = costDiff,
            tokensDifference = tokensDiff
        )
    }
}

