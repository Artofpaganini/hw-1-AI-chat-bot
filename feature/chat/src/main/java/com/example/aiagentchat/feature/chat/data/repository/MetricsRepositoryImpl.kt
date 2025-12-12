package com.example.aiagentchat.feature.chat.data.repository

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.MessageMetrics
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository
import com.example.aiagentchat.feature.chat.domain.repository.PricingRepository

class MetricsRepositoryImpl(
    private val pricingRepository: PricingRepository
) : MetricsRepository {

    override fun calculateMetrics(
        model: AiModel,
        responseTimeMs: Long,
        inputTokens: Int,
        outputTokens: Int
    ): MessageMetrics {
        return MessageMetrics.calculate(
            responseTimeMs = responseTimeMs,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            inputPricePerMillion = pricingRepository.getInputPricePerMillion(model),
            outputPricePerMillion = pricingRepository.getOutputPricePerMillion(model)
        )
    }
}

