package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.AiResponse
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.api.ToolDto

interface AiModelRepository {
    suspend fun sendMessage(
        model: AiModel,
        messages: List<ChatMessageDto>,
        tools: List<ToolDto>? = null
    ): Result<AiResponse>
    fun isModelConfigured(model: AiModel): Boolean
}

