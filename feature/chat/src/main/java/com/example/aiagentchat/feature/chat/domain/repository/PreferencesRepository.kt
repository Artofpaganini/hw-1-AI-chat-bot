package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.AiModel

interface PreferencesRepository {
    suspend fun saveSelectedModel(model: AiModel)
    suspend fun getSelectedModel(): AiModel?
}
