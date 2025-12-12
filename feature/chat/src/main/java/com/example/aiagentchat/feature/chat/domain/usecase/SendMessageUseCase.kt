package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.model.MessageMetrics
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto

class SendMessageUseCase(
    private val aiModelRepository: AiModelRepository,
    private val metricsRepository: MetricsRepository
) {
    suspend operator fun invoke(model: AiModel, messages: List<ChatMessageDto>): Result<Message> {
        val startTime = System.currentTimeMillis()
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

