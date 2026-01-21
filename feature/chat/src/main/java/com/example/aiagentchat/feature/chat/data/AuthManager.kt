package com.example.aiagentchat.feature.chat.data

import com.example.aiagentchat.feature.chat.domain.model.AiModel

class AuthManager(
    private val deepSeekApiKey: String = "",
    private val openRouterApiKey: String = "",
    private val vpsOllamaUrl: String = ""
) {
    fun getApiKey(model: AiModel): String {
        return when (model) {
            is AiModel.DeepSeek -> deepSeekApiKey
            is AiModel.Claude35Sonnet,
            is AiModel.Gpt4oMini,
            is AiModel.GeminiPro15 -> openRouterApiKey
            is AiModel.VpsOllama -> vpsOllamaUrl
        }
    }

    fun isKeyConfigured(model: AiModel): Boolean {
        return when (model) {
            is AiModel.VpsOllama -> vpsOllamaUrl.isNotBlank()
            else -> getApiKey(model).isNotBlank()
        }
    }
    
    fun getVpsOllamaUrl(): String {
        return vpsOllamaUrl
    }
}

