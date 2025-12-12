package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.AiResponse
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto

interface AiModelRepository {
    suspend fun sendMessage(model: AiModel, messages: List<ChatMessageDto>): Result<AiResponse>
    fun isModelConfigured(model: AiModel): Boolean
}

