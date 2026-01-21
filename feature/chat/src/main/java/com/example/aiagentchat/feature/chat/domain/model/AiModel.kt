package com.example.aiagentchat.feature.chat.domain.model

sealed interface AiModel {
    val displayName: String
    val modelId: String

    data object DeepSeek : AiModel {
        override val displayName: String = "DeepSeek"
        override val modelId: String = "deepseek-chat"
    }

    data object Claude35Sonnet : AiModel {
        override val displayName: String = "Claude 3.5 Sonnet"
        override val modelId: String = "anthropic/claude-3.5-sonnet"
    }

    data object Gpt4oMini : AiModel {
        override val displayName: String = "GPT-4o Mini"
        override val modelId: String = "openai/gpt-4o-mini"
    }

    data object GeminiPro15 : AiModel {
        override val displayName: String = "Gemini Pro 1.5"
        override val modelId: String = "google/gemini-pro-1.5"
    }

    data object VpsOllama : AiModel {
        override val displayName: String = "VPS - Ollama: llama3.2:3b"
        override val modelId: String = "llama3.2:3b"
    }

    companion object {
        val entries: List<AiModel> = listOf(
            DeepSeek,
            Claude35Sonnet,
            Gpt4oMini,
            GeminiPro15,
            VpsOllama
        )

        fun fromDisplayName(name: String): AiModel = when (name) {
            DeepSeek.displayName -> DeepSeek
            Claude35Sonnet.displayName -> Claude35Sonnet
            Gpt4oMini.displayName -> Gpt4oMini
            GeminiPro15.displayName -> GeminiPro15
            VpsOllama.displayName -> VpsOllama
            else -> DeepSeek
        }
    }
}

