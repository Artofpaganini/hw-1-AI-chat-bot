package com.example.aiagentchat.data

import com.example.aiagentchat.BuildConfig
import com.example.aiagentchat.domain.model.AiModel

class AuthManager {
    fun getApiKey(model: AiModel): String {
        return when (model) {
            is AiModel.DeepSeek -> getDeepSeekKey()
            is AiModel.Claude35Sonnet,
            is AiModel.Gpt4oMini,
            is AiModel.GeminiPro15 -> getOpenRouterKey()
        }
    }
    
    fun getDeepSeekKey(): String {
        return BuildConfig.DEEPSEEK_API_KEY
    }
    
    fun getOpenRouterKey(): String {
        return BuildConfig.OPENROUTER_API_KEY
    }
    
    fun isKeyConfigured(model: AiModel): Boolean {
        return getApiKey(model).isNotBlank()
    }
}

