package com.example.aiagentchat.presentation

import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.Message
import com.example.aiagentchat.domain.usecase.MetricsComparison

data class ChatState(
    val messages: List<Message> = emptyList(),
    val selectedModel: AiModel = AiModel.DeepSeek,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentInput: String = "",
    val metricsComparison: MetricsComparison? = null,
    val availableModels: List<AiModel> = AiModel.entries,
    val configuredModels: Set<AiModel> = emptySet()
)

sealed interface ChatEvent {
    data class OnInputChange(val input: String) : ChatEvent
    data object OnSendMessage : ChatEvent
    data class OnModelSelected(val model: AiModel) : ChatEvent
    data object OnDismissError : ChatEvent
    data object OnClearChat : ChatEvent
}

