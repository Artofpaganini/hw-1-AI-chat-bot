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
import com.example.aiagentchat.feature.chat.data.service.TextIndexingService
import com.example.aiagentchat.feature.chat.data.service.VectorJsonService
import com.example.aiagentchat.feature.chat.data.service.VectorChunk
import com.example.aiagentchat.feature.chat.data.service.MatchedChunk
import com.example.aiagentchat.feature.chat.data.api.OllamaApi
import android.os.Environment
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

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
    private val weatherWorkManager: com.example.aiagentchat.feature.chat.data.worker.WeatherWorkManager,
    private val textIndexingService: TextIndexingService,
    private val vectorJsonService: VectorJsonService,
    private val ollamaApi: OllamaApi
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
        loadRemoteControlState()
        loadRemoteControlDeviceId()
        loadOllamaState()
        loadRerankingState()
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
            is ChatAction.ToggleRemoteControl -> handleToggleRemoteControl(action.enabled)
            is ChatAction.SetRemoteControlDeviceId -> handleSetRemoteControlDeviceId(action.deviceId)
            is ChatAction.ToggleOllama -> handleToggleOllama(action.enabled)
            is ChatAction.SelectOllamaFile -> handleSelectOllamaFile(action.filePath)
            is ChatAction.ToggleReranking -> handleToggleReranking(action.enabled)
            is ChatAction.ExportJson -> handleExportJson()
            is ChatAction.DismissJsonExport -> handleDismissJsonExport()
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
            is ChatEvent.OnExportJson -> handleExportJson()
            is ChatEvent.OnDismissJsonExport -> handleDismissJsonExport()
            is ChatEvent.ShowJsonExport -> {
                _uiState.update { it.copy(exportedJson = event.json) }
            }
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

            val ollamaEnabled = _uiState.value.ollamaEnabled
            
            if (ollamaEnabled) {
                handleSendMessageWithOllama(currentInput, userMessage)
            } else {
                handleSendMessageWithoutOllama(currentInput, userMessage)
            }
        }
    }

    private suspend fun handleSendMessageWithOllama(currentInput: String, userMessage: Message) {
        try {
            val index = vectorJsonService.loadVectorIndex()
            val selectedFilePath = _uiState.value.ollamaSelectedFile
            val selectedFileName = selectedFilePath?.let { 
                val fileName = java.io.File(it).name
                android.util.Log.d("ChatViewModel", "Extracted file name from path '$it': '$fileName'")
                fileName
            }
            
            android.util.Log.d("ChatViewModel", "Selected file path: $selectedFilePath")
            android.util.Log.d("ChatViewModel", "Selected file name: $selectedFileName")
            android.util.Log.d("ChatViewModel", "Total documents in index: ${index.documents.size}")
            android.util.Log.d("ChatViewModel", "Available documents in index: ${index.documents.map { it.fileName }}")
            
            val documentsToSearch = if (selectedFileName != null && selectedFileName.isNotBlank()) {
                val filtered = index.documents.filter { 
                    val matches = it.fileName.equals(selectedFileName, ignoreCase = true)
                    android.util.Log.d("ChatViewModel", "Comparing: '${it.fileName}' == '$selectedFileName' -> $matches")
                    matches
                }
                android.util.Log.d("ChatViewModel", "Filtered documents for '$selectedFileName': ${filtered.size} found - ${filtered.map { it.fileName }}")
                if (filtered.isEmpty()) {
                    android.util.Log.w("ChatViewModel", "⚠️ No documents found for selected file '$selectedFileName'. Available: ${index.documents.map { it.fileName }}")
                }
                filtered
            } else {
                android.util.Log.d("ChatViewModel", "No file selected (selectedFileName is null or blank), using all ${index.documents.size} documents")
                index.documents
            }
            
            if (documentsToSearch.isEmpty()) {
                val errorMsg = if (selectedFileName != null) {
                    "No indexed documents found for selected file: $selectedFileName. Available documents: ${index.documents.map { it.fileName }}. Please index the file first."
                } else {
                    "No indexed documents found. Please index a file first."
                }
                android.util.Log.w("ChatViewModel", errorMsg)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
                _events.emit(ChatEvent.ShowError(errorMsg))
                return
            }
            
            val filteredIndex = index.copy(documents = documentsToSearch)
            android.util.Log.d("ChatViewModel", "Searching in ${documentsToSearch.size} document(s)${if (selectedFileName != null) " (selected: $selectedFileName)" else ""}")
            
            val queryEmbedding = generateQueryEmbedding(currentInput)
            val allMatchedChunks = findSimilarVectorsInJson(queryEmbedding, filteredIndex, limit = 10)
            
            if (allMatchedChunks.isEmpty()) {
                val errorMsg = "No relevant chunks found for the query."
                android.util.Log.w("ChatViewModel", errorMsg)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
                _events.emit(ChatEvent.ShowError(errorMsg))
                return
            }
            
            val rerankingEnabled = _uiState.value.rerankingEnabled
            
            val matchedChunks = if (rerankingEnabled) {
                android.util.Log.d("ChatViewModel", "Reranking enabled: using LLM-as-a-reranker (phi3:medium) for ${allMatchedChunks.size} candidate chunks")
                val rerankedChunks = performLlmReranking(currentInput, allMatchedChunks)
                android.util.Log.d("ChatViewModel", "Reranking completed: ${rerankedChunks.size} chunks reranked")
                rerankedChunks.take(3) // Берем топ-3 после reranking
            } else {
                android.util.Log.d("ChatViewModel", "Reranking disabled: using all ${allMatchedChunks.size} matched chunks")
                allMatchedChunks.take(3)
            }
            
            if (matchedChunks.isEmpty()) {
                val errorMsg = "No relevant chunks found after reranking."
                android.util.Log.w("ChatViewModel", errorMsg)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
                _events.emit(ChatEvent.ShowError(errorMsg))
                return
            }
            vectorJsonService.updateQuery(currentInput, queryEmbedding, matchedChunks)
            
            android.util.Log.d("ChatViewModel", "Using RAG with Ollama (vector search), ${matchedChunks.size} matched chunks${if (rerankingEnabled) " (after reranking)" else " (without reranking)"}")
            
            val contextText = matchedChunks.joinToString("\n\n---\n\n") { chunk ->
                "Chunk #${chunk.chunkIndex}:\n${chunk.text}"
            }
            
            val enhancedPrompt = buildString {
                appendLine("Based on the following context from indexed documents, please answer the user's question.")
                appendLine()
                appendLine("=== RELEVANT CONTEXT ===")
                appendLine(contextText)
                appendLine("=== END OF CONTEXT ===")
                appendLine()
                appendLine("=== USER QUESTION ===")
                appendLine(currentInput)
                appendLine("=== END OF QUESTION ===")
                appendLine()
                appendLine("IMPORTANT: After your answer, please provide:")
                appendLine("1. A list of chunk numbers that were used to answer the question")
                appendLine("2. A brief summary (1-2 sentences) for each chunk about what information it contained")
                appendLine()
                appendLine("Format your response as follows:")
                appendLine("[Your answer to the question]")
                appendLine()
                appendLine("---")
                appendLine("📚 Источники (chunks):")
                appendLine("  • Chunk #N: [brief summary]")
                appendLine("  • Chunk #M: [brief summary]")
                appendLine("---")
            }
            
            val messagesWithContext = listOf(
                ChatMessageDto(role = "user", content = enhancedPrompt)
            )
            
            val enabledMcpTools = emptySet<String>()
            val enabledMcpServerTools = emptyMap<String, Set<String>>()
            
            sendMessageUseCase(
                model = _uiState.value.selectedModel,
                messages = messagesWithContext,
                enabledMcpTools = enabledMcpTools,
                enabledMcpServerTools = enabledMcpServerTools,
                remoteControlDeviceId = null
            )
                .onSuccess { aiMessage ->
                    val finalContent = if (rerankingEnabled) {
                        aiMessage.content + "\n\n---\nС Ollama и фильтрацией"
                    } else {
                        aiMessage.content + "\n\n---\nС Ollama и без фильтрацией"
                    }
                    
                    val finalMessage = aiMessage.copy(content = finalContent)
                    chatRepository.saveMessage(finalMessage)
                    handleCheckMessageThreshold(finalMessage)
                    val updatedMessages = _uiState.value.messages + userMessage + finalMessage
                    val comparison = compareModelMetricsUseCase(updatedMessages, currentInput)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            metricsComparison = comparison
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("ChatViewModel", "Error sending message with Ollama context", error)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error occurred"
                        )
                    }
                    _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error occurred"))
                }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error in handleSendMessageWithOllama", e)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
            _events.emit(ChatEvent.ShowError(e.message ?: "Unknown error occurred"))
        }
    }

    private suspend fun handleSendMessageWithoutOllama(currentInput: String, userMessage: Message) {
        val messagesWithContext = buildMessagesWithContext(currentInput)
        
        val enabledMcpTools = _uiState.value.enabledMcpTools
        val enabledMcpServerTools = _uiState.value.enabledMcpServerTools
        
        android.util.Log.d("ChatViewModel", "Sending message without Ollama, MCP tools: ${enabledMcpTools.size}, MCP servers: ${enabledMcpServerTools.size}")
        
        sendMessageUseCase(
            model = _uiState.value.selectedModel,
            messages = messagesWithContext,
            enabledMcpTools = enabledMcpTools,
            enabledMcpServerTools = enabledMcpServerTools,
            remoteControlDeviceId = if (_uiState.value.remoteControlEnabled) _uiState.value.remoteControlDeviceId else null
        )
            .onSuccess { aiMessage ->
                val contentWithMarker = aiMessage.content + "\n\n---\nБез Ollama"
                val modifiedMessage = aiMessage.copy(content = contentWithMarker)
                
                chatRepository.saveMessage(modifiedMessage)
                handleCheckMessageThreshold(modifiedMessage)
                val updatedMessages = _uiState.value.messages + userMessage + modifiedMessage
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
    
    private fun handleExportJson() {
        val jsonContent = vectorJsonService.getJsonContent()
        _uiState.update { it.copy(exportedJson = jsonContent) }
        viewModelScope.launch {
            _events.emit(ChatEvent.ShowJsonExport(jsonContent))
        }
    }
    
    private fun handleDismissJsonExport() {
        _uiState.update { it.copy(exportedJson = null) }
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
    
    private fun loadRemoteControlState() {
        val enabled = preferencesManager.remoteControlEnabled
        _uiState.update { it.copy(remoteControlEnabled = enabled) }
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
    
    private fun loadRemoteControlDeviceId() {
        val deviceId = preferencesManager.remoteControlDeviceId
        _uiState.update { it.copy(remoteControlDeviceId = deviceId) }
    }
    
    private fun handleToggleRemoteControl(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle Remote Control: $enabled")
        preferencesManager.remoteControlEnabled = enabled
        _uiState.update { it.copy(remoteControlEnabled = enabled) }
    }
    
    private fun handleSetRemoteControlDeviceId(deviceId: String?) {
        android.util.Log.d("ChatViewModel", "Set Remote Control Device ID: $deviceId")
        preferencesManager.remoteControlDeviceId = deviceId
        _uiState.update { it.copy(remoteControlDeviceId = deviceId) }
    }
    
    private fun loadOllamaState() {
        val enabled = preferencesManager.ollamaEnabled
        val selectedFile = preferencesManager.ollamaSelectedFile
        _uiState.update { 
            it.copy(
                ollamaEnabled = enabled,
                ollamaSelectedFile = selectedFile
            ) 
        }
        if (enabled) {
            // Проверяем доступность Ollama сервера
            checkOllamaConnection()
            // Файл должен быть выбран пользователем
        }
    }
    
    private fun handleToggleOllama(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle Ollama: $enabled")
        preferencesManager.ollamaEnabled = enabled
        _uiState.update { it.copy(ollamaEnabled = enabled) }
        if (enabled) {
            // При включении Ollama проверяем подключение
            android.util.Log.i("ChatViewModel", "🚀 Ollama enabled - checking server connection...")
            android.util.Log.i("ChatViewModel", "📝 Note: Run './setup-ollama.sh' on your Mac to start Ollama server")
            checkOllamaConnection()
            // Файл должен быть выбран пользователем через file picker
            // Индексация начнется после выбора файла
        } else {
            // При выключении очищаем выбранный файл
            preferencesManager.ollamaSelectedFile = null
            _uiState.update { it.copy(ollamaSelectedFile = null) }
        }
    }
    
    private fun handleSelectOllamaFile(filePath: String?) {
        android.util.Log.d("ChatViewModel", "Select Ollama file: $filePath")
        preferencesManager.ollamaSelectedFile = filePath
        _uiState.update { it.copy(ollamaSelectedFile = filePath) }
        if (filePath != null && _uiState.value.ollamaEnabled) {
            // Если Ollama включен и файл выбран, начинаем индексацию
            startIndexingForFile(filePath)
        }
    }
    
    private fun loadRerankingState() {
        val enabled = preferencesManager.rerankingEnabled
        _uiState.update { 
            it.copy(rerankingEnabled = enabled) 
        }
    }
    
    private fun handleToggleReranking(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle Reranking: $enabled")
        preferencesManager.rerankingEnabled = enabled
        _uiState.update { it.copy(rerankingEnabled = enabled) }
    }
    
    private fun checkOllamaConnection() {
        viewModelScope.launch {
            try {
                android.util.Log.i("ChatViewModel", "🔍 Checking Ollama server connection at http://10.0.2.2:11434...")
                
                // Используем простой GET запрос для проверки доступности сервера
                val response = ollamaApi.getTags()
                
                if (response.isSuccessful) {
                    val tagsResponse = response.body()
                    val modelCount = tagsResponse?.models?.size ?: 0
                    android.util.Log.i("ChatViewModel", "✅ Ollama server is accessible at http://10.0.2.2:11434")
                    android.util.Log.i("ChatViewModel", "📦 Found $modelCount model(s) on server")
                    
                    // Проверяем наличие нужной модели
                    val hasEmbeddingModel = tagsResponse?.models?.any { 
                        it.name.contains("nomic-embed-text", ignoreCase = true) 
                    } ?: false
                    
                    if (hasEmbeddingModel) {
                        android.util.Log.i("ChatViewModel", "✅ Embedding model 'nomic-embed-text' is available")
                    } else {
                        android.util.Log.w("ChatViewModel", "⚠️ Embedding model 'nomic-embed-text' not found. Run: ollama pull nomic-embed-text")
                        _events.emit(ChatEvent.ShowError("⚠️ Embedding model not found. Please run: ollama pull nomic-embed-text"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.w("ChatViewModel", "⚠️ Ollama server responded with error: ${response.code()} - $errorBody")
                    _events.emit(ChatEvent.ShowError("Ollama server responded with error: ${response.code()}"))
                }
            } catch (e: java.net.ConnectException) {
                android.util.Log.e("ChatViewModel", "❌ Cannot connect to Ollama server at http://10.0.2.2:11434")
                android.util.Log.e("ChatViewModel", "Connection error: ${e.message}")
                android.util.Log.e("ChatViewModel", "Make sure:")
                android.util.Log.e("ChatViewModel", "1. Ollama is running on your Mac: ollama serve")
                android.util.Log.e("ChatViewModel", "2. Test from Mac: curl http://localhost:11434/api/tags")
                android.util.Log.e("ChatViewModel", "3. Test from emulator: adb shell curl http://10.0.2.2:11434/api/tags")
                android.util.Log.e("ChatViewModel", "4. Check firewall settings on Mac")
                _events.emit(ChatEvent.ShowError("Cannot connect to Ollama server. Make sure Ollama is running on your Mac and accessible via http://10.0.2.2:11434"))
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("ChatViewModel", "❌ Timeout connecting to Ollama server")
                android.util.Log.e("ChatViewModel", "Timeout error: ${e.message}")
                _events.emit(ChatEvent.ShowError("Timeout connecting to Ollama server. Check network connection and firewall settings."))
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("ChatViewModel", "❌ Unknown host: ${e.message}")
                android.util.Log.e("ChatViewModel", "DNS resolution failed. Check network configuration.")
                _events.emit(ChatEvent.ShowError("Cannot resolve Ollama server address. Check network configuration."))
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "❌ Error checking Ollama connection", e)
                android.util.Log.e("ChatViewModel", "Error type: ${e.javaClass.simpleName}, message: ${e.message}")
                _events.emit(ChatEvent.ShowError("Error checking Ollama connection: ${e.message}"))
            }
        }
    }
    
    private fun startIndexingForFile(filePath: String) {
        viewModelScope.launch {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    android.util.Log.e("ChatViewModel", "❌ File not found: $filePath")
                    _events.emit(ChatEvent.ShowError("File not found: ${file.name}"))
                    return@launch
                }
                
                if (!file.canRead()) {
                    android.util.Log.e("ChatViewModel", "❌ Cannot read file: $filePath")
                    _events.emit(ChatEvent.ShowError("Cannot read file: ${file.name}"))
                    return@launch
                }
                
                val fileName = file.name
                android.util.Log.i("ChatViewModel", "🚀 Starting indexing of file: $fileName")
                
                val needsIndexing = textIndexingService.checkIfIndexingNeeded(filePath, fileName)
                
                if (needsIndexing) {
                    // Запускаем наблюдение за прогрессом в отдельной корутине
                    val progressJob = viewModelScope.launch {
                        textIndexingService.indexingProgress.collect { progress ->
                            progress?.let {
                                val percent = it.percentage.toInt()
                                android.util.Log.i("ChatViewModel", 
                                    "📊 Indexing progress: $percent% - ${it.status} (chunk ${it.currentChunk}/${it.totalChunks})")
                            }
                        }
                    }
                    
                    textIndexingService.indexFile(filePath, fileName)
                        .onSuccess {
                            progressJob.cancel()
                            android.util.Log.i("ChatViewModel", "✅ Indexing completed successfully")
                            _events.emit(ChatEvent.ShowError("✅ Vector indexing completed successfully! You can now ask questions about the document."))
                        }.onFailure { error ->
                            progressJob.cancel()
                            android.util.Log.e("ChatViewModel", "❌ Indexing failed", error)
                            _events.emit(ChatEvent.ShowError("Indexing failed: ${error.message}"))
                        }
                } else {
                    val index = vectorJsonService.loadVectorIndex()
                    val totalChunks = index.documents.sumOf { it.chunks.size }
                    android.util.Log.i("ChatViewModel", "✅ File already indexed ($totalChunks chunks), ready to use")
                    _events.emit(ChatEvent.ShowError("ℹ️ File already indexed. Ready to use."))
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error indexing file", e)
                _events.emit(ChatEvent.ShowError("Error: ${e.message}"))
            }
        }
    }

    private suspend fun buildMessagesWithContext(currentInput: String): List<ChatMessageDto> {
        val enabledMcpTools = _uiState.value.enabledMcpTools
        val enabledMcpServerTools = _uiState.value.enabledMcpServerTools
        val ollamaEnabled = _uiState.value.ollamaEnabled
        
        // Проверяем, есть ли включенные MCP tools
        val hasEnabledMcpTools = enabledMcpTools.isNotEmpty() || enabledMcpServerTools.values.any { it.isNotEmpty() }
        
        // Если включен Ollama, используем векторный поиск (приоритет над MCP tools)
        if (ollamaEnabled) {
            val index = vectorJsonService.loadVectorIndex()
            val hasVectors = index.documents.isNotEmpty()
            
            if (hasVectors) {
                try {
                    val queryEmbedding = generateQueryEmbedding(currentInput)
                    val matchedChunks = findSimilarVectorsInJson(queryEmbedding, index, limit = 3)
                    
                    if (matchedChunks.isNotEmpty()) {
                        // Обновляем JSON с текущим запросом
                        vectorJsonService.updateQuery(currentInput, queryEmbedding, matchedChunks)
                        
                        val contextText = matchedChunks.joinToString("\n\n---\n\n") { it.text }
                        val enhancedInput = buildString {
                            appendLine("Based on the following context from indexed documents, please answer the user's question:")
                            appendLine()
                            appendLine("=== RELEVANT CONTEXT ===")
                            appendLine(contextText)
                            appendLine("=== END OF CONTEXT ===")
                            appendLine()
                            appendLine("=== USER QUESTION ===")
                            appendLine(currentInput)
                            appendLine("=== END OF QUESTION ===")
                        }
                        android.util.Log.d("ChatViewModel", "Using vector search context with ${matchedChunks.size} chunks")
                        return listOf(
                            ChatMessageDto(role = "user", content = enhancedInput)
                        )
                    } else {
                        android.util.Log.w("ChatViewModel", "No similar vectors found for query")
                        // Все равно обновляем JSON с запросом
                        vectorJsonService.updateQuery(currentInput, queryEmbedding, emptyList())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error in vector search", e)
                }
            } else {
                val progress = textIndexingService.indexingProgress.first()
                val progressText = progress?.let {
                    "Indexing in progress: ${it.percentage.toInt()}% (${it.currentChunk}/${it.totalChunks})"
                } ?: "Indexing vectors..."
                
                return listOf(
                    ChatMessageDto(role = "user", content = "$currentInput\n\nNote: $progressText")
                )
            }
        }
        
        // Если включены MCP tools (но не Ollama), отправляем только текущий вопрос без контекста
        if (hasEnabledMcpTools) {
            android.util.Log.d("ChatViewModel", "Using MCP tools, skipping context")
            return listOf(
                ChatMessageDto(role = "user", content = currentInput)
            )
        }
        
        // Обычная логика с контекстом для случаев без MCP tools
        val sessionContext = _uiState.value.sessionContext
        val messages = mutableListOf<ChatMessageDto>()
        
        // Используем Toon формат для контекста для экономии токенов
        val contextData = mutableMapOf<String, Any?>()
        
        if (sessionContext.userSummaries.isNotEmpty()) {
            val userSummariesData = sessionContext.userSummaries
                .sortedByDescending { it.timestamp }
                .take(3)
                .map { summary ->
                    mapOf(
                        "summary" to summary.summary,
                        "keyFacts" to summary.keyFacts,
                        "timestamp" to summary.timestamp
                    )
                }
            contextData["userSummaries"] = userSummariesData
        }
        
        if (sessionContext.aiSummaries.isNotEmpty()) {
            val aiSummariesData = sessionContext.aiSummaries
                .sortedByDescending { it.timestamp }
                .take(3)
                .map { summary ->
                    mapOf(
                        "summary" to summary.summary,
                        "keyFacts" to summary.keyFacts,
                        "timestamp" to summary.timestamp
                    )
                }
            contextData["aiSummaries"] = aiSummariesData
        }
        
        val currentMessages = _uiState.value.messages
            .filter { !it.isCompressed }
            .map { message ->
                ChatMessageDto(
                    role = if (message.isUser) "user" else "assistant",
                    content = message.content
                )
            }
        
        if (contextData.isNotEmpty()) {
            // Форматируем контекст в Toon для экономии токенов
            val toonContext = com.example.aiagentchat.core.common.toon.ToonEncoder.encode(contextData)
            val systemMessage = buildString {
                appendLine("Контекст предыдущих обсуждений (в формате TOON для экономии токенов):")
                appendLine()
                appendLine("```toon")
                appendLine(toonContext)
                appendLine("```")
                appendLine()
                appendLine("Используй этот контекст для понимания истории разговора. Отвечай с учетом предыдущих обсуждений. Если пользователь спрашивает о чем-то из прошлого, используй этот контекст для ответа.")
            }
            messages.add(ChatMessageDto(role = "system", content = systemMessage))
        }
        
        messages.addAll(currentMessages)
        messages.add(ChatMessageDto(role = "user", content = currentInput))
        
        return messages
    }
    
    private suspend fun generateQueryEmbedding(text: String): List<Float> {
        android.util.Log.d("ChatViewModel", "Generating query embedding for: ${text.take(50)}...")
        
        val request = com.example.aiagentchat.feature.chat.data.api.OllamaEmbedRequest(
            model = OllamaApi.DEFAULT_MODEL,
            input = text
        )
        
        try {
            val response = ollamaApi.generateEmbedding(request)
            
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                val errorMsg = "Failed to generate query embedding: HTTP ${response.code()} - $errorBody"
                android.util.Log.e("ChatViewModel", errorMsg)
                android.util.Log.e("ChatViewModel", "Check if Ollama server is running at: http://10.0.2.2:11434")
                throw Exception(errorMsg)
            }
            
            val responseBody = response.body()
            if (responseBody == null) {
                throw Exception("Empty response body from Ollama")
            }
            
            val embeddings = responseBody.embeddings
            if (embeddings.isEmpty()) {
                throw Exception("Empty embedding response from Ollama")
            }
            
            val embedding = embeddings[0]
            android.util.Log.d("ChatViewModel", "✅ Generated query embedding with ${embedding.size} dimensions")
            return normalizeVector(embedding)
        } catch (e: java.net.UnknownHostException) {
            val errorMsg = "Cannot connect to Ollama server. Make sure Ollama is running on your Mac at http://localhost:11434"
            android.util.Log.e("ChatViewModel", errorMsg, e)
            throw Exception(errorMsg, e)
        } catch (e: java.net.ConnectException) {
            val errorMsg = "Connection refused to Ollama server. Is Ollama running? Test: curl http://localhost:11434/api/tags"
            android.util.Log.e("ChatViewModel", errorMsg, e)
            throw Exception(errorMsg, e)
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "Timeout connecting to Ollama server. Check network connection."
            android.util.Log.e("ChatViewModel", errorMsg, e)
            throw Exception(errorMsg, e)
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error generating query embedding", e)
            throw e
        }
    }
    
    private fun normalizeVector(vector: List<Float>): List<Float> {
        val min = vector.minOrNull() ?: 0f
        val max = vector.maxOrNull() ?: 1f
        val range = max - min
        
        return if (range > 0) {
            vector.map { (it - min) / range }
        } else {
            vector.map { 0.5f }
        }
    }
    
    private suspend fun performLlmReranking(
        query: String,
        candidateChunks: List<MatchedChunk>
    ): List<MatchedChunk> {
        android.util.Log.d("ChatViewModel", "Starting LLM reranking for ${candidateChunks.size} chunks")
        
        val rerankedChunks = candidateChunks.mapIndexed { index, chunk ->
            try {
                val relevanceScore = evaluateRelevanceWithLlm(query, chunk.text)
                android.util.Log.d("ChatViewModel", "Chunk $index: relevance score = $relevanceScore")
                chunk.copy(similarity = relevanceScore)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error evaluating relevance for chunk $index", e)
                // В случае ошибки используем оригинальную similarity
                chunk
            }
        }
        
        // Сортируем по relevance score (от большего к меньшему)
        val sorted = rerankedChunks.sortedByDescending { it.similarity }
        android.util.Log.d("ChatViewModel", "Reranking completed. Top scores: ${sorted.take(3).map { it.similarity }}")
        
        return sorted
    }
    
    private suspend fun evaluateRelevanceWithLlm(query: String, chunkText: String): Float {
        val prompt = buildString {
            appendLine("Оцени релевантность текста запросу по шкале от 0.0 до 1.0.")
            appendLine("Запрос: \"$query\"")
            appendLine("Текст: \"${chunkText.take(1000)}\"") // Ограничиваем длину текста
            appendLine("Ответь текст + релевантность текста в виде \"Релевантность число\". Никаких пояснений.")
        }
        
        try {
            val messages = listOf(
                com.example.aiagentchat.feature.chat.data.api.OllamaChatMessage(
                    role = "user",
                    content = prompt
                )
            )
            
            val request = com.example.aiagentchat.feature.chat.data.api.OllamaChatRequest(
                model = OllamaApi.DEFAULT_RERANKING_MODEL,
                messages = messages,
                stream = false
            )
            
            android.util.Log.d("ChatViewModel", "Sending reranking request to ${OllamaApi.DEFAULT_RERANKING_MODEL}")
            val response = ollamaApi.generateChat(request)
            
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.w("ChatViewModel", "Reranking request failed: HTTP ${response.code()} - $errorBody")
                return 0.5f // Возвращаем среднее значение при ошибке
            }
            
            val responseBody = response.body()
            val content = responseBody?.message?.content ?: ""
            
            if (content.isBlank()) {
                android.util.Log.w("ChatViewModel", "Empty response from reranking LLM")
                return 0.5f
            }
            
            // Парсим ответ: ищем число от 0.0 до 1.0
            val relevanceRegex = Regex("Релевантность\\s*([0-9.]+)|([0-9.]+)")
            val match = relevanceRegex.find(content)
            
            if (match != null) {
                val scoreStr = match.groupValues[1].takeIf { it.isNotBlank() } ?: match.groupValues[2]
                val score = scoreStr.toFloatOrNull()?.coerceIn(0f, 1f)
                
                if (score != null) {
                    android.util.Log.d("ChatViewModel", "Parsed relevance score: $score from response: ${content.take(100)}")
                    return score
                }
            }
            
            // Если не удалось распарсить, пытаемся найти любое число от 0.0 до 1.0
            val numberRegex = Regex("0\\.[0-9]+|1\\.0|1")
            val numberMatch = numberRegex.find(content)
            if (numberMatch != null) {
                val score = numberMatch.value.toFloatOrNull()?.coerceIn(0f, 1f)
                if (score != null) {
                    android.util.Log.d("ChatViewModel", "Parsed relevance score from number: $score")
                    return score
                }
            }
            
            android.util.Log.w("ChatViewModel", "Could not parse relevance score from response: ${content.take(200)}")
            return 0.5f // Возвращаем среднее значение если не удалось распарсить
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error in evaluateRelevanceWithLlm", e)
            return 0.5f
        }
    }
    
    private fun findSimilarVectorsInJson(
        queryEmbedding: List<Float>,
        index: com.example.aiagentchat.feature.chat.data.service.VectorIndexJson,
        limit: Int
    ): List<MatchedChunk> {
        val allChunks = index.documents.flatMap { doc ->
            doc.chunks.map { chunk ->
                Pair(chunk, doc.fileName)
            }
        }
        
        if (allChunks.isEmpty()) {
            return emptyList()
        }
        
        val similarities = allChunks.map { (chunk, fileName) ->
            val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)
            MatchedChunk(
                text = chunk.text,
                chunkIndex = chunk.chunkIndex,
                similarity = similarity
            )
        }.sortedByDescending { it.similarity }
            .take(limit)
        
        return similarities
    }
    
    private fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float {
        if (vec1.size != vec2.size) {
            return 0f
        }
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (denominator > 0) {
            dotProduct / denominator
        } else {
            0f
        }
    }
}

