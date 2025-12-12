package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.MessageMetrics

interface MetricsRepository {
    fun calculateMetrics(
        model: AiModel,
        responseTimeMs: Long,
        inputTokens: Int,
        outputTokens: Int
    ): MessageMetrics
}

