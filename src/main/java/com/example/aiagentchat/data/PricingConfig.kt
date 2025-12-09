package com.example.aiagentchat.data

import com.example.aiagentchat.BuildConfig
import com.example.aiagentchat.domain.model.AiModel

object PricingConfig {
    fun getInputPricePerMillion(model: AiModel): Double {
        return when (model) {
            is AiModel.DeepSeek -> BuildConfig.DEEPSEEK_INPUT_PRICE
            is AiModel.Zai -> BuildConfig.ZAI_INPUT_PRICE
        }
    }
    
    fun getOutputPricePerMillion(model: AiModel): Double {
        return when (model) {
            is AiModel.DeepSeek -> BuildConfig.DEEPSEEK_OUTPUT_PRICE
            is AiModel.Zai -> BuildConfig.ZAI_OUTPUT_PRICE
        }
    }
}

