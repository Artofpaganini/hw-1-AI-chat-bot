package com.example.aiagentchat.data

import com.example.aiagentchat.BuildConfig
import com.example.aiagentchat.domain.model.AiModel

object PricingConfig {
    fun getInputPricePerMillion(model: AiModel): Double {
        return when (model) {
            is AiModel.DeepSeek -> BuildConfig.DEEPSEEK_INPUT_PRICE
            is AiModel.Claude35Sonnet -> BuildConfig.CLAUDE_35_SONNET_INPUT_PRICE
            is AiModel.Gpt4oMini -> BuildConfig.GPT_4O_MINI_INPUT_PRICE
            is AiModel.GeminiPro15 -> BuildConfig.GEMINI_PRO_15_INPUT_PRICE
        }
    }
    
    fun getOutputPricePerMillion(model: AiModel): Double {
        return when (model) {
            is AiModel.DeepSeek -> BuildConfig.DEEPSEEK_OUTPUT_PRICE
            is AiModel.Claude35Sonnet -> BuildConfig.CLAUDE_35_SONNET_OUTPUT_PRICE
            is AiModel.Gpt4oMini -> BuildConfig.GPT_4O_MINI_OUTPUT_PRICE
            is AiModel.GeminiPro15 -> BuildConfig.GEMINI_PRO_15_OUTPUT_PRICE
        }
    }
}

