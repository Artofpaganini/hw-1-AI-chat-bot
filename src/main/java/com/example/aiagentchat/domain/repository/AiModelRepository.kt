package com.example.aiagentchat.domain.repository

import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.AiResponse

interface AiModelRepository {
    suspend fun sendMessage(model: AiModel, prompt: String): Result<AiResponse>
    fun isModelConfigured(model: AiModel): Boolean
}






