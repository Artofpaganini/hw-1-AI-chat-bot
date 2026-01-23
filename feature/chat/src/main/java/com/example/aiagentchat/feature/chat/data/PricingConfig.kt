package com.example.aiagentchat.feature.chat.data

import com.example.aiagentchat.feature.chat.domain.model.AiModel

class PricingConfig(
    val deepSeekInputPrice: Double = 0.14,
    val deepSeekOutputPrice: Double = 0.28,
    val claude35SonnetInputPrice: Double = 3.00,
    val claude35SonnetOutputPrice: Double = 15.00,
    val gpt4oMiniInputPrice: Double = 0.15,
    val gpt4oMiniOutputPrice: Double = 0.60,
    val geminiPro15InputPrice: Double = 1.25,
    val geminiPro15OutputPrice: Double = 5.00
) {
    fun getInputPricePerMillion(model: AiModel): Double {
        return when (model) {
            is AiModel.DeepSeek -> deepSeekInputPrice
            is AiModel.Claude35Sonnet -> claude35SonnetInputPrice
            is AiModel.Gpt4oMini -> gpt4oMiniInputPrice
            is AiModel.GeminiPro15 -> geminiPro15InputPrice
            is AiModel.OllamaLlama32b -> 0.0 // Ollama работает локально, без стоимости
        }
    }

    fun getOutputPricePerMillion(model: AiModel): Double {
        return when (model) {
            is AiModel.DeepSeek -> deepSeekOutputPrice
            is AiModel.Claude35Sonnet -> claude35SonnetOutputPrice
            is AiModel.Gpt4oMini -> gpt4oMiniOutputPrice
            is AiModel.GeminiPro15 -> geminiPro15OutputPrice
            is AiModel.OllamaLlama32b -> 0.0 // Ollama работает локально, без стоимости
        }
    }
}

