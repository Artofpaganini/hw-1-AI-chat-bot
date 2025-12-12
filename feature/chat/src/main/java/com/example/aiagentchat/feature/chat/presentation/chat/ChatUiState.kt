package com.example.aiagentchat.feature.chat.presentation.chat

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.SessionContext
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.usecase.MetricsComparison

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val selectedModel: AiModel = AiModel.DeepSeek,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentInput: String = "",
    val metricsComparison: MetricsComparison? = null,
    val availableModels: List<AiModel> = AiModel.entries,
    val configuredModels: Set<AiModel> = emptySet(),
    val exportedToon: String? = null,
    val sessionContext: SessionContext = SessionContext()
)

sealed interface ChatAction {
    data class InputChanged(val input: String) : ChatAction
    data object SendMessage : ChatAction
    data class ModelSelected(val model: AiModel) : ChatAction
    data object DismissError : ChatAction
    data object ClearChat : ChatAction
    data object ExportChat : ChatAction
    data object DismissExport : ChatAction
    data class CheckMessageThreshold(val message: Message) : ChatAction
}

sealed interface ChatEvent {
    data class ShowError(val message: String) : ChatEvent
    data class ShowExport(val toon: String) : ChatEvent
    data class OnInputChange(val input: String) : ChatEvent
    data object OnSendMessage : ChatEvent
    data class OnModelSelected(val model: AiModel) : ChatEvent
    data object OnDismissError : ChatEvent
    data object OnClearChat : ChatEvent
    data object OnExportChat : ChatEvent
    data object OnDismissExport : ChatEvent
}

