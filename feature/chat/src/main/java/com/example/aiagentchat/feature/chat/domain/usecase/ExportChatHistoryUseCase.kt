package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.feature.chat.domain.model.Message

class ExportChatHistoryUseCase {

    operator fun invoke(
        messages: List<Message>,
        comparison: MetricsComparison? = null
    ): String {
        return buildString {
            appendLine("=== Chat History Export ===")
            messages.forEach { message ->
                appendLine("${if (message.isUser) "User" else "AI"}: ${message.content}")
            }
            comparison?.let {
                appendLine("\n=== Metrics Comparison ===")
                appendLine("Prompt: ${it.prompt}")
            }
        }
    }
}

