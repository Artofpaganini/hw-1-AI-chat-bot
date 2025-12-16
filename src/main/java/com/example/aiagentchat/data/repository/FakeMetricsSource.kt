package com.example.aiagentchat.data.repository

import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.MessageMetrics
import com.example.aiagentchat.domain.repository.MetricsRepository

/**
 * Fake implementation of MetricsRepository for testing purposes.
 * Provides predictable metrics values for unit and integration tests.
 */
object FakeMetricsSource : MetricsRepository {
    private const val FAKE_INPUT_PRICE = 0.10
    private const val FAKE_OUTPUT_PRICE = 0.20
    
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
            inputPricePerMillion = FAKE_INPUT_PRICE,
            outputPricePerMillion = FAKE_OUTPUT_PRICE
        )
    }
    
    override fun getInputPricePerMillion(model: AiModel): Double = FAKE_INPUT_PRICE
    
    override fun getOutputPricePerMillion(model: AiModel): Double = FAKE_OUTPUT_PRICE
    
    /**
     * Creates a fake MessageMetrics with predefined values for testing
     */
    fun createFakeMetrics(
        responseTimeMs: Long = 500L,
        inputTokens: Int = 100,
        outputTokens: Int = 200
    ): MessageMetrics {
        return calculateMetrics(
            model = AiModel.DeepSeek,
            responseTimeMs = responseTimeMs,
            inputTokens = inputTokens,
            outputTokens = outputTokens
        )
    }
}





