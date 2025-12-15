package com.example.aiagentchat.domain.repository

import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.MessageMetrics

interface MetricsRepository {
    fun calculateMetrics(
        model: AiModel,
        responseTimeMs: Long,
        inputTokens: Int,
        outputTokens: Int
    ): MessageMetrics
    
    fun getInputPricePerMillion(model: AiModel): Double
    fun getOutputPricePerMillion(model: AiModel): Double
}




