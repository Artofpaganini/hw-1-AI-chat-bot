package com.example.aiagentchat.feature.chat.presentation.chat

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.McpTool
import com.example.aiagentchat.feature.chat.domain.model.McpServer
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
    val sessionContext: SessionContext = SessionContext(),
    val showMcpToolsDialog: Boolean = false,
    val ollamaEnabled: Boolean = false,
    val exportedJson: String? = null,
    val ollamaSelectedFiles: List<String> = emptyList(), // Список путей к выбранным файлам для индексации (максимум 5)
    val rerankingEnabled: Boolean = false,
    val projectHelperEnabled: Boolean = false,
    val projectUserAssistantEnabled: Boolean = false,
    val userFormatType: String = "программист",
    val projectFilesEnabled: Boolean = false,
    val projectAnalyticEnabled: Boolean = false
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
    data object ShowMcpTools : ChatAction
    data object DismissMcpTools : ChatAction
    data class ToggleOllama(val enabled: Boolean) : ChatAction
    data class ToggleReranking(val enabled: Boolean) : ChatAction
    data class ToggleProjectHelper(val enabled: Boolean) : ChatAction
    data object ExportJson : ChatAction
    data object DismissJsonExport : ChatAction
    data class ToggleProjectUserAssistant(val enabled: Boolean) : ChatAction
    data class SetUserFormatType(val formatType: String) : ChatAction
    data class ToggleProjectFiles(val enabled: Boolean) : ChatAction
    data class ToggleProjectAnalytic(val enabled: Boolean) : ChatAction
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
    data class ShowJsonExport(val json: String) : ChatEvent
    data object OnExportJson : ChatEvent
    data object OnDismissJsonExport : ChatEvent
}

