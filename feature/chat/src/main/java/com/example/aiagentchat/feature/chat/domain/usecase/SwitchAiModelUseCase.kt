package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository

class SwitchAiModelUseCase(
    private val aiModelRepository: AiModelRepository
) {
    operator fun invoke(model: AiModel): Result<AiModel> {
        return if (aiModelRepository.isModelConfigured(model)) {
            Result.success(model)
        } else {
            Result.failure(ModelNotConfiguredException(model))
        }
    }
}

class ModelNotConfiguredException(val model: AiModel) : Exception(
    "API key for ${model.displayName} is not configured. Please add it to local.properties"
)

