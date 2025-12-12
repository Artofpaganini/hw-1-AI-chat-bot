package com.example.aiagentchat.data.repository

import com.example.aiagentchat.data.PricingConfig
import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.MessageMetrics
import com.example.aiagentchat.domain.repository.MetricsRepository

class MetricsRepositoryImpl : MetricsRepository {
    
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
            inputPricePerMillion = getInputPricePerMillion(model),
            outputPricePerMillion = getOutputPricePerMillion(model)
        )
    }
    
    override fun getInputPricePerMillion(model: AiModel): Double {
        return PricingConfig.getInputPricePerMillion(model)
    }
    
    override fun getOutputPricePerMillion(model: AiModel): Double {
        return PricingConfig.getOutputPricePerMillion(model)
    }
}


