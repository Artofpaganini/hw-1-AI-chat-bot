package com.example.aiagentchat.domain.usecase

import com.example.aiagentchat.data.toon.ChatHistoryExporter
import com.example.aiagentchat.domain.model.Message

class ExportChatHistoryUseCase {
    
    operator fun invoke(
        messages: List<Message>,
        comparison: MetricsComparison? = null
    ): String {
        return ChatHistoryExporter.exportChatHistory(messages, comparison)
    }
    
    fun exportSummary(messages: List<Message>): String {
        return ChatHistoryExporter.exportUsageSummary(messages)
    }
}






