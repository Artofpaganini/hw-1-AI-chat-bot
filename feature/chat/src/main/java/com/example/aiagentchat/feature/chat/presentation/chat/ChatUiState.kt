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
    val mcpTools: List<McpTool> = emptyList(),
    val enabledMcpTools: Set<String> = emptySet(),
    val mcpServers: List<McpServer> = emptyList(),
    val enabledMcpServerTools: Map<String, Set<String>> = emptyMap(), // serverId -> Set<toolName>
    val showMcpToolsDialog: Boolean = false,
    val weatherNotificationsEnabled: Boolean = false,
    val testModeEnabled: Boolean = false,
    val remoteControlEnabled: Boolean = false,
    val remoteControlDeviceId: String? = null,
    val ollamaEnabled: Boolean = false,
    val exportedJson: String? = null,
    val ollamaSelectedFile: String? = null, // Путь к выбранному файлу для индексации
    val rerankingEnabled: Boolean = false
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
    data class ToggleMcpTool(val toolName: String, val enabled: Boolean) : ChatAction
    data class ToggleMcpServerTool(val serverId: String, val toolName: String, val enabled: Boolean) : ChatAction
    data class ToggleWeatherNotifications(val enabled: Boolean) : ChatAction
    data class ToggleTestMode(val enabled: Boolean) : ChatAction
    data class ToggleRemoteControl(val enabled: Boolean) : ChatAction
    data class SetRemoteControlDeviceId(val deviceId: String?) : ChatAction
    data class ToggleOllama(val enabled: Boolean) : ChatAction
    data class SelectOllamaFile(val filePath: String?) : ChatAction
    data class ToggleReranking(val enabled: Boolean) : ChatAction
    data object ExportJson : ChatAction
    data object DismissJsonExport : ChatAction
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

