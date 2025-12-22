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

            val messagesWithContext = buildMessagesWithContext(currentInput)

            // Передаем MCP tools только если они включены и Ollama не включен
            val ollamaEnabled = _uiState.value.ollamaEnabled
            val enabledMcpTools = if (ollamaEnabled) emptySet() else _uiState.value.enabledMcpTools
            val enabledMcpServerTools = if (ollamaEnabled) emptyMap() else _uiState.value.enabledMcpServerTools
            
            android.util.Log.d("ChatViewModel", "Sending message - Ollama: $ollamaEnabled, MCP tools: ${enabledMcpTools.size}, MCP servers: ${enabledMcpServerTools.size}")
            
            sendMessageUseCase(
                model = _uiState.value.selectedModel,
                messages = messagesWithContext,
                enabledMcpTools = enabledMcpTools,
                enabledMcpServerTools = enabledMcpServerTools,
                remoteControlDeviceId = if (_uiState.value.remoteControlEnabled && !ollamaEnabled) _uiState.value.remoteControlDeviceId else null
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
        _uiState.update { it.copy(ollamaEnabled = enabled) }
        if (enabled) {
            // Проверяем доступность Ollama сервера перед началом индексации
            checkOllamaConnection()
            startIndexingIfNeeded()
        }
    }
    
    private fun handleToggleOllama(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle Ollama: $enabled")
        preferencesManager.ollamaEnabled = enabled
        _uiState.update { it.copy(ollamaEnabled = enabled) }
        if (enabled) {
            // Проверяем доступность Ollama сервера перед началом индексации
            checkOllamaConnection()
            startIndexingIfNeeded()
        }
    }
    
    private fun checkOllamaConnection() {
        viewModelScope.launch {
            try {
                android.util.Log.i("ChatViewModel", "🔍 Checking Ollama server connection at http://10.0.2.2:11434...")
                // Пробуем простой запрос для проверки доступности
                val testRequest = com.example.aiagentchat.feature.chat.data.api.OllamaEmbedRequest(
                    model = OllamaApi.DEFAULT_MODEL,
                    input = "test"
                )
                val response = ollamaApi.generateEmbedding(testRequest)
                if (response.isSuccessful) {
                    android.util.Log.i("ChatViewModel", "✅ Ollama server is accessible at http://10.0.2.2:11434")
                } else {
                    android.util.Log.w("ChatViewModel", "⚠️ Ollama server responded with error: ${response.code()}")
                }
            } catch (e: java.net.ConnectException) {
                android.util.Log.e("ChatViewModel", "❌ Cannot connect to Ollama server at http://10.0.2.2:11434")
                android.util.Log.e("ChatViewModel", "Make sure:")
                android.util.Log.e("ChatViewModel", "1. Ollama is running on your Mac: ollama serve")
                android.util.Log.e("ChatViewModel", "2. Test from Mac: curl http://localhost:11434/api/tags")
                android.util.Log.e("ChatViewModel", "3. Test from emulator: adb shell curl http://10.0.2.2:11434/api/tags")
                _events.emit(ChatEvent.ShowError("Cannot connect to Ollama server. Make sure Ollama is running on your Mac."))
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error checking Ollama connection", e)
            }
        }
    }
    
    private fun startIndexingIfNeeded() {
        viewModelScope.launch {
            try {
                // Используем внутреннее хранилище приложения или внешнее с проверкой разрешений
                val context = weatherWorkManager.getContext()
                
                android.util.Log.i("ChatViewModel", "🔍 Searching for README.md file...")
                
                var foundFile: File? = null
                val checkedPaths = mutableListOf<String>()
                
                // 1. Внутреннее хранилище приложения (filesDir) - не требует разрешений
                val internalStorageDir = context.filesDir
                val internalReadmeFile = File(internalStorageDir, "README.md")
                checkedPaths.add("Internal storage: ${internalReadmeFile.absolutePath}")
                android.util.Log.d("ChatViewModel", "Checking: ${internalReadmeFile.absolutePath} (exists: ${internalReadmeFile.exists()})")
                
                if (internalReadmeFile.exists() && internalReadmeFile.canRead()) {
                    foundFile = internalReadmeFile
                    android.util.Log.i("ChatViewModel", "✅ Found README.md in internal storage: ${foundFile.absolutePath}")
                } else {
                    // 2. Внешнее хранилище приложения (getExternalFilesDir) - не требует разрешений
                    val externalStorageDir = context.getExternalFilesDir(null)
                    if (externalStorageDir != null) {
                        val externalReadmeFile = File(externalStorageDir, "README.md")
                        checkedPaths.add("External app storage: ${externalReadmeFile.absolutePath}")
                        android.util.Log.d("ChatViewModel", "Checking: ${externalReadmeFile.absolutePath} (exists: ${externalReadmeFile.exists()})")
                        
                        if (externalReadmeFile.exists() && externalReadmeFile.canRead()) {
                            foundFile = externalReadmeFile
                            android.util.Log.i("ChatViewModel", "✅ Found README.md in external app storage: ${foundFile.absolutePath}")
                        }
                    } else {
                        checkedPaths.add("External app storage: null (not available)")
                        android.util.Log.w("ChatViewModel", "External app storage is not available")
                    }
                    
                    // 3. Стандартные пути внешнего хранилища (требуют разрешений)
                    if (foundFile == null) {
                        val alternativePaths = listOf(
                            File(android.os.Environment.getExternalStorageDirectory(), "README.md"),
                            File("/sdcard/README.md"),
                            File("/storage/emulated/0/README.md"),
                            File("/storage/emulated/0/Download/README.md"),
                            File("/storage/emulated/0/Documents/README.md")
                        )
                        
                        for (path in alternativePaths) {
                            checkedPaths.add("External storage: ${path.absolutePath}")
                            try {
                                android.util.Log.d("ChatViewModel", "Checking: ${path.absolutePath} (exists: ${path.exists()}, canRead: ${path.canRead()})")
                                if (path.exists() && path.canRead()) {
                                    foundFile = path
                                    android.util.Log.i("ChatViewModel", "✅ Found README.md at: ${foundFile.absolutePath}")
                                    break
                                }
                            } catch (e: SecurityException) {
                                android.util.Log.w("ChatViewModel", "⚠️ Cannot access ${path.absolutePath}: ${e.message}")
                            } catch (e: Exception) {
                                android.util.Log.w("ChatViewModel", "⚠️ Error checking ${path.absolutePath}: ${e.message}")
                            }
                        }
                    }
                }
                
                if (foundFile == null) {
                    android.util.Log.e("ChatViewModel", "❌ README.md not found in any location")
                    android.util.Log.e("ChatViewModel", "Checked paths:")
                    checkedPaths.forEach { path ->
                        android.util.Log.e("ChatViewModel", "  - $path")
                    }
                    
                    val errorMessage = buildString {
                        appendLine("README.md file not found.")
                        appendLine()
                        appendLine("Checked locations:")
                        checkedPaths.take(5).forEach { appendLine("  • $it") }
                        appendLine()
                        appendLine("To fix:")
                        appendLine("1. Copy README.md to internal storage:")
                        appendLine("   adb push README.md /sdcard/README.md")
                        appendLine("   adb shell \"run-as com.example.aiagentchat cp /sdcard/README.md /data/data/com.example.aiagentchat/files/README.md\"")
                        appendLine()
                        appendLine("2. Or grant storage permissions and copy to /sdcard/README.md")
                    }
                    
                    // Пробуем создать тестовый файл во внутреннем хранилище для демонстрации
                    android.util.Log.i("ChatViewModel", "💡 Creating sample README.md in internal storage for testing...")
                    try {
                        val sampleContent = """
# Sample Document for Vector Search

This is a sample document created automatically for testing vector search functionality.

## Features

- Vector embeddings generation
- Semantic search
- Document indexing

## Usage

Ask questions about this document to test the vector search feature.

## Example Questions

- What is this document about?
- What features are mentioned?
- How does vector search work?

                        """.trimIndent()
                        
                        val internalStorageDir = context.filesDir
                        val sampleFile = File(internalStorageDir, "README.md")
                        sampleFile.writeText(sampleContent)
                        
                        if (sampleFile.exists() && sampleFile.canRead()) {
                            foundFile = sampleFile
                            android.util.Log.i("ChatViewModel", "✅ Created sample README.md at: ${foundFile.absolutePath}")
                            _events.emit(ChatEvent.ShowError("ℹ️ Created sample README.md for testing. You can replace it with your own file."))
                        } else {
                            _events.emit(ChatEvent.ShowError(errorMessage))
                            return@launch
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "Failed to create sample file", e)
                        _events.emit(ChatEvent.ShowError(errorMessage))
                        return@launch
                    }
                }
                
                val needsIndexing = textIndexingService.checkIfIndexingNeeded(
                    foundFile.absolutePath,
                    "README.md"
                )
                
                if (needsIndexing) {
                    android.util.Log.i("ChatViewModel", "🚀 Starting indexing of README.md")
                    
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
                    
                    textIndexingService.indexFile(
                        foundFile.absolutePath,
                        "README.md"
                    ).onSuccess {
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
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error checking indexing", e)
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

