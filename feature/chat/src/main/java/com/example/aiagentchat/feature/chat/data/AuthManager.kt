package com.example.aiagentchat.feature.chat.data

import com.example.aiagentchat.feature.chat.domain.model.AiModel

class AuthManager(
    private val deepSeekApiKey: String = "",
    private val openRouterApiKey: String = ""
) {
    fun getApiKey(model: AiModel): String {
        return when (model) {
            is AiModel.DeepSeek -> deepSeekApiKey
            is AiModel.Claude35Sonnet,
            is AiModel.Gpt4oMini,
            is AiModel.GeminiPro15 -> openRouterApiKey
            is AiModel.OllamaLlama32b -> "" // Ollama не требует API ключа
        }
    }

    fun isKeyConfigured(model: AiModel): Boolean {
        return when (model) {
            is AiModel.OllamaLlama32b -> true // Ollama всегда доступна, если сервер запущен
            else -> getApiKey(model).isNotBlank()
        }
    }
}

