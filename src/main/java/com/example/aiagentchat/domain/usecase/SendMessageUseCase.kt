package com.example.aiagentchat.domain.usecase

import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.Message
import com.example.aiagentchat.domain.repository.AiModelRepository
import com.example.aiagentchat.domain.repository.MetricsRepository

class SendMessageUseCase(
    private val aiModelRepository: AiModelRepository,
    private val metricsRepository: MetricsRepository
) {
    suspend operator fun invoke(model: AiModel, prompt: String): Result<Message> {
        val startTime = System.currentTimeMillis()
        
        return aiModelRepository.sendMessage(model, prompt).map { response ->
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







