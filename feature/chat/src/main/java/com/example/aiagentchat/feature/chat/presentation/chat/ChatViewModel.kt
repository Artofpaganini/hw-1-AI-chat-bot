package com.example.aiagentchat.feature.chat.presentation.chat

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagentchat.feature.chat.data.service.WeatherNotificationService
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.model.SessionContext
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MultiMcpRepository
import com.example.aiagentchat.feature.chat.domain.model.McpServer
import com.example.aiagentchat.feature.chat.domain.usecase.CompareModelMetricsUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.CompressionScheduler
import com.example.aiagentchat.feature.chat.domain.usecase.ContextInitializer
import com.example.aiagentchat.feature.chat.domain.usecase.ExportChatHistoryUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SwitchAiModelUseCase
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val switchAiModelUseCase: SwitchAiModelUseCase,
    private val compareModelMetricsUseCase: CompareModelMetricsUseCase,
    private val exportChatHistoryUseCase: ExportChatHistoryUseCase,
    private val aiModelRepository: AiModelRepository,
    private val chatRepository: ChatRepository,
    private val compressionScheduler: CompressionScheduler,
    private val contextInitializer: ContextInitializer,
    private val mcpRepository: McpRepository,
    private val multiMcpRepository: MultiMcpRepository,
    private val preferencesManager: com.example.aiagentchat.core.common.preferences.PreferencesManager,
    private val weatherWorkManager: com.example.aiagentchat.feature.chat.data.worker.WeatherWorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Legacy compatibility - expose state as 'state' for old code
    val state: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    init {
        updateConfiguredModels()
        loadMessages()
        loadSessionContext()
        observeContextSummaries()
        observeMcpTools()
        observeMcpServers()
        loadWeatherNotificationsState()
        loadEnabledMcpTools()
        loadEnabledMcpServerTools()
        loadTestModeState()
    }

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.InputChanged -> handleInputChanged(action.input)
            is ChatAction.SendMessage -> handleSendMessage()
            is ChatAction.ModelSelected -> handleModelSelected(action.model)
            is ChatAction.DismissError -> handleDismissError()
            is ChatAction.ClearChat -> handleClearChat()
            is ChatAction.ExportChat -> handleExportChat()
            is ChatAction.DismissExport -> handleDismissExport()
            is ChatAction.CheckMessageThreshold -> handleCheckMessageThreshold(action.message)
            is ChatAction.ShowMcpTools -> handleShowMcpTools()
            is ChatAction.DismissMcpTools -> handleDismissMcpTools()
            is ChatAction.ToggleMcpTool -> handleToggleMcpTool(action.toolName, action.enabled)
            is ChatAction.ToggleMcpServerTool -> handleToggleMcpServerTool(action.serverId, action.toolName, action.enabled)
            is ChatAction.ToggleWeatherNotifications -> handleToggleWeatherNotifications(action.enabled)
            is ChatAction.ToggleTestMode -> handleToggleTestMode(action.enabled)
        }
    }
    
    // Legacy compatibility - expose onEvent for old code
    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.OnInputChange -> handleInputChanged(event.input)
            is ChatEvent.OnSendMessage -> handleSendMessage()
            is ChatEvent.OnModelSelected -> handleModelSelected(event.model)
            is ChatEvent.OnDismissError -> handleDismissError()
            is ChatEvent.OnClearChat -> handleClearChat()
            is ChatEvent.OnExportChat -> handleExportChat()
            is ChatEvent.OnDismissExport -> handleDismissExport()
            is ChatEvent.ShowError -> handleDismissError()
            is ChatEvent.ShowExport -> { /* handled in UI */ }
        }
    }

    private fun updateConfiguredModels() {
        val configured = AiModel.entries.filter { aiModelRepository.isModelConfigured(it) }.toSet()
        _uiState.update { it.copy(configuredModels = configured) }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            chatRepository.getAllMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun loadSessionContext() {
        viewModelScope.launch {
            val context = contextInitializer.loadLatest()
            _uiState.update { it.copy(sessionContext = context) }
        }
    }

    private fun observeContextSummaries() {
        viewModelScope.launch {
            combine(
                chatRepository.getContextSummaries(ContextSummary.SummaryType.USER_PACK),
                chatRepository.getContextSummaries(ContextSummary.SummaryType.AI_PACK)
            ) { user, ai ->
                SessionContext(
                    userSummaries = user,
                    aiSummaries = ai
                )
            }.collect { sessionContext ->
                _uiState.update { it.copy(sessionContext = sessionContext) }
            }
        }
    }

    private fun handleInputChanged(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    private fun handleSendMessage() {
        val currentInput = _uiState.value.currentInput.trim()
        if (currentInput.isBlank()) return

        val userMessage = Message(
            content = currentInput,
            isUser = true
        )

        viewModelScope.launch {
            chatRepository.saveMessage(userMessage)
            handleCheckMessageThreshold(userMessage)
            
            preferencesManager.lastUserQuery = currentInput
            
            _uiState.update { state ->
                state.copy(
                    currentInput = "",
                    isLoading = true,
                    error = null
                )
            }

            val messagesWithContext = buildMessagesWithContext(currentInput)

            sendMessageUseCase(
                model = _uiState.value.selectedModel,
                messages = messagesWithContext,
                enabledMcpTools = _uiState.value.enabledMcpTools,
                enabledMcpServerTools = _uiState.value.enabledMcpServerTools
            )
                .onSuccess { aiMessage ->
                    chatRepository.saveMessage(aiMessage)
                    handleCheckMessageThreshold(aiMessage)
                    val updatedMessages = _uiState.value.messages + userMessage + aiMessage
                    val comparison = compareModelMetricsUseCase(updatedMessages, currentInput)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            metricsComparison = comparison
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error occurred"
                        )
                    }
                    _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error occurred"))
                }
        }
    }

    private fun handleModelSelected(model: AiModel) {
        switchAiModelUseCase(model)
            .onSuccess { selectedModel ->
                _uiState.update { it.copy(selectedModel = selectedModel, error = null) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
                viewModelScope.launch {
                    _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error"))
                }
            }
    }

    private fun handleDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun handleClearChat() {
        viewModelScope.launch {
            chatRepository.deleteAllMessages()
            _uiState.update { it.copy(messages = emptyList(), metricsComparison = null) }
        }
    }

    private fun handleExportChat() {
        val currentState = _uiState.value
        if (currentState.messages.isEmpty()) return

        val toonExport = exportChatHistoryUseCase(
            messages = currentState.messages,
            comparison = currentState.metricsComparison
        )

        _uiState.update { it.copy(exportedToon = toonExport) }
        viewModelScope.launch {
            _events.emit(ChatEvent.ShowExport(toonExport))
        }
    }

    private fun handleDismissExport() {
        _uiState.update { it.copy(exportedToon = null) }
    }

    private fun handleCheckMessageThreshold(message: Message) {
        viewModelScope.launch {
            compressionScheduler.onMessageSaved(
                message = message,
                modelForSummary = _uiState.value.selectedModel
            )
        }
    }

    private fun observeMcpTools() {
        viewModelScope.launch {
            mcpRepository.observeTools().collect { tools ->
                _uiState.update { it.copy(mcpTools = tools) }
            }
        }
    }

    private fun observeMcpServers() {
        viewModelScope.launch {
            multiMcpRepository.observeServers().collect { servers ->
                _uiState.update { it.copy(mcpServers = servers) }
            }
        }
    }

    private fun handleShowMcpTools() {
        viewModelScope.launch {
            // Load tools for all servers
            val servers = multiMcpRepository.listServers()
            servers.forEach { server ->
                multiMcpRepository.listToolsForServer(server.id).onSuccess { tools ->
                    // Tools are automatically updated in the server via observeServers
                }
            }
            _uiState.update { it.copy(showMcpToolsDialog = true) }
        }
    }

    private fun handleDismissMcpTools() {
        _uiState.update { it.copy(showMcpToolsDialog = false) }
    }

    private fun loadEnabledMcpTools() {
        val enabledTools = preferencesManager.getEnabledMcpTools()
        _uiState.update { it.copy(enabledMcpTools = enabledTools) }
    }

    private fun loadEnabledMcpServerTools() {
        val enabledServerTools = preferencesManager.getEnabledMcpServerTools()
        _uiState.update { it.copy(enabledMcpServerTools = enabledServerTools) }
    }

    private fun handleToggleMcpTool(toolName: String, enabled: Boolean) {
        _uiState.update { state ->
            val newEnabled = if (enabled) {
                state.enabledMcpTools + toolName
            } else {
                state.enabledMcpTools - toolName
            }
            val updatedState = state.copy(enabledMcpTools = newEnabled)
            // Сохраняем состояние в PreferencesManager
            preferencesManager.setEnabledMcpTools(newEnabled)
            updatedState
        }
    }
    
    private fun handleToggleMcpServerTool(serverId: String, toolName: String, enabled: Boolean) {
        _uiState.update { state ->
            val currentServerTools = state.enabledMcpServerTools.toMutableMap()
            val serverTools = currentServerTools[serverId]?.toMutableSet() ?: mutableSetOf()
            
            if (enabled) {
                serverTools.add(toolName)
            } else {
                serverTools.remove(toolName)
            }
            
            currentServerTools[serverId] = serverTools
            val updatedState = state.copy(enabledMcpServerTools = currentServerTools)
            
            // Сохраняем состояние в PreferencesManager
            preferencesManager.setEnabledToolsForServer(serverId, serverTools)
            updatedState
        }
    }

    private fun loadWeatherNotificationsState() {
        val enabled = preferencesManager.weatherNotificationsEnabled
        val testMode = preferencesManager.testModeEnabled
        _uiState.update { 
            it.copy(
                weatherNotificationsEnabled = enabled,
                testModeEnabled = testMode
            )
        }
        
        // Планируем задачу если уведомления уже были включены
        // WorkManager сохраняет задачи даже после перезагрузки устройства
        if (enabled && !testMode) {
            // Используем WorkManager только если не включен test mode
            // WorkManager работает даже когда приложение убито
            weatherWorkManager.scheduleWeatherNotifications(true)
            android.util.Log.d("ChatViewModel", "WorkManager scheduled on app start")
        } else if (enabled && testMode) {
            // Test mode использует Foreground Service
            startWeatherService()
        }
    }

    private fun loadTestModeState() {
        val enabled = preferencesManager.testModeEnabled
        _uiState.update { it.copy(testModeEnabled = enabled) }
    }

    private fun handleToggleTestMode(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle test mode: $enabled")
        preferencesManager.testModeEnabled = enabled
        _uiState.update { it.copy(testModeEnabled = enabled) }
        
        // Если включен test mode и уведомления включены, перезапускаем сервис
        if (enabled && preferencesManager.weatherNotificationsEnabled) {
            startWeatherService()
        } else if (!enabled) {
            stopWeatherService()
        }
    }

    private fun handleToggleWeatherNotifications(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle weather notifications: $enabled")
        preferencesManager.weatherNotificationsEnabled = enabled
        _uiState.update { it.copy(weatherNotificationsEnabled = enabled) }
        
        val testMode = preferencesManager.testModeEnabled
        
        if (enabled) {
            if (testMode) {
                // Используем Foreground Service
                startWeatherService()
            } else {
                // Используем WorkManager
                weatherWorkManager.scheduleWeatherNotifications(true)
            }
        } else {
            if (testMode) {
                stopWeatherService()
            } else {
                weatherWorkManager.scheduleWeatherNotifications(false)
            }
        }
    }

    private fun startWeatherService() {
        val context = weatherWorkManager.getContext()
        val intent = Intent(context, WeatherNotificationService::class.java).apply {
            action = WeatherNotificationService.ACTION_START
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.util.Log.d("ChatViewModel", "Weather service started")
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to start weather service", e)
        }
    }

    private fun stopWeatherService() {
        val context = weatherWorkManager.getContext()
        val intent = Intent(context, WeatherNotificationService::class.java).apply {
            action = WeatherNotificationService.ACTION_STOP
        }
        try {
            context.startService(intent)
            android.util.Log.d("ChatViewModel", "Weather service stopped")
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to stop weather service", e)
        }
    }

    private fun buildMessagesWithContext(currentInput: String): List<ChatMessageDto> {
        val enabledMcpTools = _uiState.value.enabledMcpTools
        
        // Если включены MCP tools, отправляем только текущий вопрос без контекста
        if (enabledMcpTools.isNotEmpty()) {
            return listOf(
                ChatMessageDto(role = "user", content = currentInput)
            )
        }
        
        // Обычная логика с контекстом для случаев без MCP tools
        val sessionContext = _uiState.value.sessionContext
        val messages = mutableListOf<ChatMessageDto>()
        
        val contextParts = mutableListOf<String>()
        
        if (sessionContext.userSummaries.isNotEmpty()) {
            val userContext = sessionContext.userSummaries
                .sortedByDescending { it.timestamp }
                .take(3)
                .joinToString(separator = "\n\n") { summary ->
                    "Пользователь: ${summary.summary}" + 
                    if (summary.keyFacts.isNotEmpty()) {
                        "\nКлючевые факты: ${summary.keyFacts.joinToString(", ")}"
                    } else ""
                }
            contextParts.add("Контекст предыдущих сообщений пользователя:\n$userContext")
        }
        
        if (sessionContext.aiSummaries.isNotEmpty()) {
            val aiContext = sessionContext.aiSummaries
                .sortedByDescending { it.timestamp }
                .take(3)
                .joinToString(separator = "\n\n") { summary ->
                    "AI: ${summary.summary}" + 
                    if (summary.keyFacts.isNotEmpty()) {
                        "\nКлючевые факты: ${summary.keyFacts.joinToString(", ")}"
                    } else ""
                }
            contextParts.add("Контекст предыдущих ответов AI:\n$aiContext")
        }
        
        val currentMessages = _uiState.value.messages
            .filter { !it.isCompressed }
            .map { message ->
                ChatMessageDto(
                    role = if (message.isUser) "user" else "assistant",
                    content = message.content
                )
            }
        
        if (contextParts.isNotEmpty()) {
            val systemMessage = contextParts.joinToString("\n\n") + 
                "\n\nИспользуй этот контекст для понимания истории разговора. " +
                "Отвечай с учетом предыдущих обсуждений. " +
                "Если пользователь спрашивает о чем-то из прошлого, используй этот контекст для ответа."
            messages.add(ChatMessageDto(role = "system", content = systemMessage))
        }
        
        messages.addAll(currentMessages)
        messages.add(ChatMessageDto(role = "user", content = currentInput))
        
        return messages
    }
}

