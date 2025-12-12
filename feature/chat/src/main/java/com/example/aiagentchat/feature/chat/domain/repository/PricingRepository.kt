package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.AiModel

interface PricingRepository {
    fun getInputPricePerMillion(model: AiModel): Double
    fun getOutputPricePerMillion(model: AiModel): Double
}

