package com.example.aiagentchat.domain.model

sealed interface AiModel {
    val displayName: String
    val modelId: String

    data object DeepSeek : AiModel {
        override val displayName: String = "DeepSeek"
        override val modelId: String = "deepseek-chat"
    }

    data object Zai : AiModel {
        override val displayName: String = "Z.ai"
        override val modelId: String = "z-ai/glm-4.6v"
    }

    companion object {
        val entries: List<AiModel> = listOf(DeepSeek, Zai)
        
        fun fromDisplayName(name: String): AiModel = when (name) {
            DeepSeek.displayName -> DeepSeek
            Zai.displayName -> Zai
            else -> DeepSeek
        }
    }
}

