package com.example.aiagentchat.data

import com.example.aiagentchat.BuildConfig
import com.example.aiagentchat.domain.model.AiModel

class AuthManager {
    fun getApiKey(model: AiModel): String {
        return when (model) {
            is AiModel.DeepSeek -> getDeepSeekKey()
            is AiModel.Zai -> getZaiKey()
        }
    }
    
    fun getDeepSeekKey(): String {
        return BuildConfig.DEEPSEEK_API_KEY
    }
    
    fun getZaiKey(): String {
        return BuildConfig.ZAI_API_KEY
    }
    
    fun isKeyConfigured(model: AiModel): Boolean {
        return getApiKey(model).isNotBlank()
    }
}

