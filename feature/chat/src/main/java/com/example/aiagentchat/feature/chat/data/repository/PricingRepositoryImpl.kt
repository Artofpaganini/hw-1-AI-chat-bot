package com.example.aiagentchat.feature.chat.data.repository

import com.example.aiagentchat.feature.chat.data.PricingConfig
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.repository.PricingRepository

class PricingRepositoryImpl(
    private val pricingConfig: PricingConfig
) : PricingRepository {
    override fun getInputPricePerMillion(model: AiModel): Double {
        return pricingConfig.getInputPricePerMillion(model)
    }

    override fun getOutputPricePerMillion(model: AiModel): Double {
        return pricingConfig.getOutputPricePerMillion(model)
    }
}

