package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.AiModel

interface PreferencesRepository {
    suspend fun saveSelectedModel(model: AiModel)
    suspend fun getSelectedModel(): AiModel?
    suspend fun getOllamaEnabled(): Boolean
    suspend fun setOllamaEnabled(enabled: Boolean)
    suspend fun getOllamaSelectedFiles(): List<String>
    suspend fun setOllamaSelectedFiles(files: List<String>)
    suspend fun getRerankingEnabled(): Boolean
    suspend fun setRerankingEnabled(enabled: Boolean)
    suspend fun getProjectHelperEnabled(): Boolean
    suspend fun setProjectHelperEnabled(enabled: Boolean)
    suspend fun getProjectUserAssistantEnabled(): Boolean
    suspend fun setProjectUserAssistantEnabled(enabled: Boolean)
    suspend fun getUserFormatType(): String
    suspend fun setUserFormatType(formatType: String)
    suspend fun getProjectFilesEnabled(): Boolean
    suspend fun setProjectFilesEnabled(enabled: Boolean)
    suspend fun getProjectAnalyticEnabled(): Boolean
    suspend fun setProjectAnalyticEnabled(enabled: Boolean)
}
