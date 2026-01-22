package com.example.aiagentchat.feature.chat.data

import com.example.aiagentchat.core.common.preferences.PreferencesManager
import com.example.aiagentchat.feature.chat.domain.model.AiModel

class AuthManager(
    private val deepSeekApiKey: String = "",
    private val openRouterApiKey: String = "",
    private val preferencesManager: PreferencesManager
) {
    fun getApiKey(model: AiModel): String {
        return when (model) {
            is AiModel.DeepSeek -> deepSeekApiKey
            is AiModel.Claude35Sonnet,
            is AiModel.Gpt4oMini,
            is AiModel.GeminiPro15 -> openRouterApiKey
            is AiModel.VpsOllama -> preferencesManager.vpsOllamaUrl
        }
    }

    fun isKeyConfigured(model: AiModel): Boolean {
        return when (model) {
            is AiModel.VpsOllama -> preferencesManager.vpsOllamaUrl.trim().isNotBlank()
            else -> getApiKey(model).isNotBlank()
        }
    }
    
    fun getVpsOllamaUrl(): String {
        return preferencesManager.vpsOllamaUrl
    }
}

