package com.example.aiagentchat.domain.model

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val model: AiModel? = null,
    val metrics: MessageMetrics? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompressed: Boolean = false
)

data class MessageMetrics(
    val responseTimeMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val costUsd: Double
) {
    companion object {
        fun calculate(
            responseTimeMs: Long,
            inputTokens: Int,
            outputTokens: Int,
            inputPricePerMillion: Double,
            outputPricePerMillion: Double
        ): MessageMetrics {
            val inputCost = (inputTokens.toDouble() / 1_000_000) * inputPricePerMillion
            val outputCost = (outputTokens.toDouble() / 1_000_000) * outputPricePerMillion
            return MessageMetrics(
                responseTimeMs = responseTimeMs,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                costUsd = inputCost + outputCost
            )
        }
    }
}

