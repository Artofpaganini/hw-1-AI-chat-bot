package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.Message

data class MetricsComparison(
    val prompt: String,
    val deepSeekMessage: Message?,
    val timeDifferenceMs: Long?,
    val costDifferenceUsd: Double?,
    val tokensDifference: Int?
) {
    val isComplete: Boolean
        get() = deepSeekMessage != null
}

class CompareModelMetricsUseCase {
    operator fun invoke(messages: List<Message>, prompt: String): MetricsComparison? {
        val userMessages = messages.filter { it.isUser && it.content == prompt }
        if (userMessages.isEmpty()) return null
        val lastUserMessageTimestamp = userMessages.maxOf { it.timestamp }
        val responses = messages.filter {
            !it.isUser && it.timestamp > lastUserMessageTimestamp
        }
        val deepSeekResponse = responses.find { it.model is AiModel.DeepSeek }
        if (deepSeekResponse == null) return null
        return MetricsComparison(
            prompt = prompt,
            deepSeekMessage = deepSeekResponse,
            timeDifferenceMs = null,
            costDifferenceUsd = null,
            tokensDifference = null
        )
    }
}

