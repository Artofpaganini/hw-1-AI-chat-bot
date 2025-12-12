package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.AiResponse

interface AiModelRepository {
    suspend fun sendMessage(model: AiModel, prompt: String): Result<AiResponse>
    fun isModelConfigured(model: AiModel): Boolean
}

