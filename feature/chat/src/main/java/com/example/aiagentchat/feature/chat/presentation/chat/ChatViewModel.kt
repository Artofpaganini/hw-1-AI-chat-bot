package com.example.aiagentchat.feature.chat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.model.SessionContext
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import com.example.aiagentchat.feature.chat.domain.repository.PersonalizationRepository
import com.example.aiagentchat.feature.chat.domain.repository.PreferencesRepository
import com.example.aiagentchat.feature.chat.domain.usecase.CompareModelMetricsUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.CompressionScheduler
import com.example.aiagentchat.feature.chat.domain.usecase.ContextInitializer
import com.example.aiagentchat.feature.chat.domain.usecase.ExportChatHistoryUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.GetDatabaseInfoUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.PersonalizeUserUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SwitchAiModelUseCase
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.api.OllamaApi
import com.example.aiagentchat.feature.chat.data.api.OllamaEmbedRequest
import com.example.aiagentchat.feature.chat.data.service.VectorJsonService
import com.example.aiagentchat.feature.chat.data.service.MatchedChunk
import kotlin.math.sqrt
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MultiMcpRepository
import com.example.aiagentchat.core.common.data.speech.SpeechRecognizerManager
import com.example.aiagentchat.core.common.data.speech.SpeechResult
import com.example.aiagentchat.core.common.preferences.PreferencesManager
import com.example.aiagentchat.feature.chat.data.worker.WeatherWorkManager
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val getDatabaseInfoUseCase: GetDatabaseInfoUseCase,
    private val personalizeUserUseCase: PersonalizeUserUseCase,
    private val aiModelRepository: AiModelRepository,
    private val chatRepository: ChatRepository,
    private val preferencesRepository: PreferencesRepository,
    private val personalizationRepository: PersonalizationRepository,
    private val compressionScheduler: CompressionScheduler,
    private val contextInitializer: ContextInitializer,
    private val speechRecognizerManager: SpeechRecognizerManager,
    private val mcpRepository: McpRepository? = null,
    private val multiMcpRepository: MultiMcpRepository? = null,
    private val preferencesManager: PreferencesManager? = null,
    private val weatherWorkManager: WeatherWorkManager? = null,
    private val vectorJsonService: VectorJsonService? = null,
    private val ollamaApi: OllamaApi? = null
) : ViewModel() {
    
    private var speechRecognitionJob: Job? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Legacy compatibility - expose state as 'state' for old code
    val state: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var currentUserId: String? = null

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    init {
        updateConfiguredModels()
        loadMessages()
        loadSessionContext()
        observeContextSummaries()
        loadSavedModel()
        loadToolsPreferences()
        loadMcpTools()
        observeMcpServers()
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
            is ChatAction.ViewDatabase -> handleViewDatabase()
            is ChatAction.DismissDatabaseView -> handleDismissDatabaseView()
            is ChatAction.CheckMessageThreshold -> handleCheckMessageThreshold(action.message)
            is ChatAction.StartVoiceInput -> handleStartVoiceInput()
            is ChatAction.StopVoiceInput -> handleStopVoiceInput()
            is ChatAction.DismissSpeechError -> handleDismissSpeechError()
            is ChatAction.ShowToolsDialog -> handleShowToolsDialog()
            is ChatAction.DismissToolsDialog -> handleDismissToolsDialog()
            is ChatAction.ToggleOllama -> handleToggleOllama(action.enabled)
            is ChatAction.SelectOllamaFile -> handleSelectOllamaFile(action.filePath)
            is ChatAction.RemoveOllamaFile -> handleRemoveOllamaFile(action.filePath)
            is ChatAction.ToggleReranking -> handleToggleReranking(action.enabled)
            is ChatAction.ToggleProjectHelper -> handleToggleProjectHelper(action.enabled)
            is ChatAction.ToggleProjectUserAssistant -> handleToggleProjectUserAssistant(action.enabled)
            is ChatAction.SetUserFormatType -> handleSetUserFormatType(action.formatType)
            is ChatAction.ToggleProjectFiles -> handleToggleProjectFiles(action.enabled)
            is ChatAction.ToggleProjectAnalytic -> handleToggleProjectAnalytic(action.enabled)
            is ChatAction.ShowMcpTools -> handleShowMcpTools()
            is ChatAction.DismissMcpTools -> handleDismissMcpTools()
            is ChatAction.ToggleMcpTool -> handleToggleMcpTool(action.toolName, action.enabled)
            is ChatAction.ToggleMcpServerTool -> handleToggleMcpServerTool(action.serverId, action.toolName, action.enabled)
            is ChatAction.ToggleWeatherNotifications -> handleToggleWeatherNotifications(action.enabled)
            is ChatAction.ToggleTestMode -> handleToggleTestMode(action.enabled)
            is ChatAction.ToggleRemoteControl -> handleToggleRemoteControl(action.enabled)
            is ChatAction.SetRemoteControlDeviceId -> handleSetRemoteControlDeviceId(action.deviceId)
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
            is ChatEvent.OnViewDatabase -> handleViewDatabase()
            is ChatEvent.OnDismissDatabaseView -> handleDismissDatabaseView()
            is ChatEvent.ShowError -> handleDismissError()
            is ChatEvent.ShowExport -> { /* handled in UI */ }
            is ChatEvent.ShowDatabaseInfo -> { /* handled in UI */ }
            is ChatEvent.OnStartVoiceInput -> handleStartVoiceInput()
            is ChatEvent.OnStopVoiceInput -> handleStopVoiceInput()
            is ChatEvent.OnDismissSpeechError -> handleDismissSpeechError()
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
            _uiState.update { state ->
                state.copy(
                    currentInput = "",
                    isLoading = true,
                    error = null
                )
            }

            val userProfile = personalizeUserUseCase(currentInput, _uiState.value.selectedModel)
                .getOrElse {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = "Ошибка персонализации: ${it.message}"
                        )
                    }
                    _events.emit(ChatEvent.ShowError("Ошибка персонализации: ${it.message}"))
                    return@launch
                }

            currentUserId = userProfile.userId
            
            preferencesManager?.lastUserQuery = currentInput

            val ollamaEnabled = _uiState.value.ollamaEnabled
            
            if (ollamaEnabled && vectorJsonService != null && ollamaApi != null) {
                handleSendMessageWithOllama(currentInput, userMessage, userProfile.personalizationPrompt)
            } else {
                handleSendMessageWithoutOllama(currentInput, userMessage, userProfile.personalizationPrompt)
            }
        }
    }
    
    private suspend fun handleSendMessageWithOllama(
        currentInput: String,
        userMessage: Message,
        personalizationPrompt: String
    ) {
        try {
            val index = vectorJsonService!!.loadVectorIndex()
            val hasVectors = index.documents.isNotEmpty()
            
            if (!hasVectors) {
                val errorMsg = "No indexed documents found. Please index a file first."
                Log.w("ChatViewModel", errorMsg)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
                _events.emit(ChatEvent.ShowError(errorMsg))
                return
            }
            
            val queryEmbedding = generateQueryEmbedding(currentInput)
            val matchedChunks = findSimilarVectorsInJson(queryEmbedding, index, limit = 10)
            
            if (matchedChunks.isEmpty()) {
                val errorMsg = "No relevant chunks found for the query."
                Log.w("ChatViewModel", errorMsg)
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
            
            val rerankingEnabled = _uiState.value.rerankingEnabled
            val finalChunks = if (rerankingEnabled) {
                performLlmReranking(currentInput, matchedChunks)
            } else {
                matchedChunks.take(3)
            }
            
            Log.d("ChatViewModel", "Using RAG with Ollama (vector search), ${finalChunks.size} matched chunks")
            
            val contextText = finalChunks.joinToString("\n\n---\n\n") { chunk ->
                "Chunk #${chunk.chunkIndex}:\n${chunk.text}"
            }
            
            val enhancedPrompt = buildString {
                if (personalizationPrompt.isNotBlank()) {
                    appendLine(personalizationPrompt)
                    appendLine()
                }
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
                finalChunks.forEach { chunk ->
                    val similarityText = if (rerankingEnabled) {
                        "Релевантность: ${String.format("%.2f", chunk.similarity)}"
                    } else {
                        "Похожесть: ${String.format("%.2f", chunk.similarity * 100)}%"
                    }
                    appendLine("  • Chunk #${chunk.chunkIndex} ($similarityText): [brief summary]")
                }
                appendLine("---")
                if (rerankingEnabled) {
                    appendLine("С Ollama и фильтрацией")
                } else {
                    appendLine("С Ollama и без фильтрацией")
                }
            }
            
            val messagesWithContext = listOf(
                ChatMessageDto(role = "user", content = enhancedPrompt)
            )
            
            val enabledMcpTools = _uiState.value.enabledMcpTools
            val enabledMcpServerTools = _uiState.value.enabledMcpServerTools
            
            sendMessageUseCase(
                model = _uiState.value.selectedModel,
                messages = messagesWithContext,
                enabledMcpTools = enabledMcpTools,
                enabledMcpServerTools = enabledMcpServerTools,
                remoteControlDeviceId = if (_uiState.value.remoteControlEnabled) _uiState.value.remoteControlDeviceId else null
            )
                .onSuccess { aiMessage ->
                    val contentWithMarker = aiMessage.content + if (rerankingEnabled) {
                        "\n\n---\nС Ollama и фильтрацией"
                    } else {
                        "\n\n---\nС Ollama и без фильтрацией"
                    }
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
                    Log.e("ChatViewModel", "Error sending message with Ollama context", error)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error occurred"
                        )
                    }
                    _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error occurred"))
                }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error in handleSendMessageWithOllama", e)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
            _events.emit(ChatEvent.ShowError(e.message ?: "Unknown error occurred"))
        }
    }
    
    private suspend fun handleSendMessageWithoutOllama(
        currentInput: String,
        userMessage: Message,
        personalizationPrompt: String
    ) {
        val messagesWithContext = buildMessagesWithContext(currentInput, personalizationPrompt)
        
        val enabledMcpTools = _uiState.value.enabledMcpTools
        val enabledMcpServerTools = _uiState.value.enabledMcpServerTools
        
        Log.d("ChatViewModel", "Sending message without Ollama, MCP tools: ${enabledMcpTools.size}, MCP servers: ${enabledMcpServerTools.size}")
        
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
    
    private suspend fun generateQueryEmbedding(text: String): List<Float> {
        Log.d("ChatViewModel", "Generating query embedding for: ${text.take(50)}...")
        
        val request = OllamaEmbedRequest(
            model = OllamaApi.DEFAULT_MODEL,
            input = text
        )
        
        try {
            val response = ollamaApi!!.generateEmbedding(request)
            
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                val errorMsg = "Failed to generate query embedding: HTTP ${response.code()} - $errorBody"
                Log.e("ChatViewModel", errorMsg)
                Log.e("ChatViewModel", "Check if Ollama server is running at: http://10.0.2.2:11434")
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
            Log.d("ChatViewModel", "✅ Generated query embedding with ${embedding.size} dimensions")
            return embedding
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error generating query embedding", e)
            throw e
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
    
    private suspend fun performLlmReranking(
        query: String,
        matchedChunks: List<MatchedChunk>
    ): List<MatchedChunk> {
        Log.d("ChatViewModel", "🔄 Reranking enabled: using LLM-as-a-reranker (phi3:medium) for ${matchedChunks.size} candidate chunks")
        
        val rerankedChunks = matchedChunks.mapIndexed { index, chunk ->
            val relevanceScore = evaluateRelevanceWithLlm(query, chunk.text)
            chunk.copy(similarity = relevanceScore)
        }.sortedByDescending { it.similarity }
            .take(3)
        
        Log.d("ChatViewModel", "📊 Reranking completed. Top 3 scores: ${rerankedChunks.map { "Chunk #${it.chunkIndex}=${it.similarity}" }}")
        return rerankedChunks
    }
    
    private suspend fun evaluateRelevanceWithLlm(query: String, chunkText: String): Float {
        val prompt = buildString {
            appendLine("Оцени релевантность текста запросу по шкале от 0.0 до 1.0.")
            appendLine("Запрос: \"$query\"")
            appendLine("Текст: \"${chunkText.take(1000)}\"")
            appendLine("Ответь текст + релевантность текста в виде \"Релевантность число\". Никаких пояснений.")
        }
        
        return try {
            val request = com.example.aiagentchat.feature.chat.data.api.OllamaChatRequest(
                model = OllamaApi.DEFAULT_RERANKING_MODEL,
                messages = listOf(
                    com.example.aiagentchat.feature.chat.data.api.OllamaChatMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                stream = false
            )
            
            val response = ollamaApi!!.generateChat(request)
            
            if (response.isSuccessful && response.body()?.message != null) {
                val content = response.body()!!.message!!.content
                val relevanceMatch = Regex("Релевантность\\s+([0-9.]+)").find(content)
                val relevance = relevanceMatch?.groupValues?.get(1)?.toFloatOrNull()
                    ?: Regex("([0-9.]+)").find(content)?.groupValues?.get(1)?.toFloatOrNull()
                    ?: 0.5f
                
                relevance.coerceIn(0f, 1f)
            } else {
                0.5f
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error evaluating relevance with LLM", e)
            0.5f
        }
    }
    
    private fun loadMcpTools() {
        viewModelScope.launch {
            mcpRepository?.listTools()?.getOrNull()?.let { tools ->
                _uiState.update { it.copy(mcpTools = tools) }
            }
        }
    }
    
    private fun observeMcpServers() {
        viewModelScope.launch {
            multiMcpRepository?.observeServers()?.collect { servers ->
                _uiState.update { it.copy(mcpServers = servers) }
            }
        }
    }
    
    private fun handleShowMcpTools() {
        _uiState.update { it.copy(showToolsDialog = true) }
    }
    
    private fun handleDismissMcpTools() {
        _uiState.update { it.copy(showToolsDialog = false) }
    }
    
    private fun handleToggleMcpTool(toolName: String, enabled: Boolean) {
        viewModelScope.launch {
            val currentEnabled = _uiState.value.enabledMcpTools.toMutableSet()
            if (enabled) {
                currentEnabled.add(toolName)
            } else {
                currentEnabled.remove(toolName)
            }
            _uiState.update { it.copy(enabledMcpTools = currentEnabled) }
        }
    }
    
    private fun handleToggleMcpServerTool(serverId: String, toolName: String, enabled: Boolean) {
        viewModelScope.launch {
            val currentEnabled = _uiState.value.enabledMcpServerTools.toMutableMap()
            val serverTools = currentEnabled[serverId]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                serverTools.add(toolName)
            } else {
                serverTools.remove(toolName)
            }
            currentEnabled[serverId] = serverTools
            _uiState.update { it.copy(enabledMcpServerTools = currentEnabled) }
        }
    }
    
    private fun handleToggleWeatherNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager?.weatherNotificationsEnabled = enabled
            _uiState.update { it.copy(weatherNotificationsEnabled = enabled) }
            weatherWorkManager?.scheduleWeatherNotifications(enabled)
        }
    }
    
    private fun handleToggleTestMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager?.testModeEnabled = enabled
            _uiState.update { it.copy(testModeEnabled = enabled) }
        }
    }
    
    private fun handleToggleRemoteControl(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(remoteControlEnabled = enabled) }
        }
    }
    
    private fun handleSetRemoteControlDeviceId(deviceId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(remoteControlDeviceId = deviceId) }
        }
    }

    private fun handleModelSelected(model: AiModel) {
        switchAiModelUseCase(model)
            .onSuccess { selectedModel ->
                _uiState.update { it.copy(selectedModel = selectedModel, error = null) }
                viewModelScope.launch {
                    preferencesRepository.saveSelectedModel(selectedModel)
                }
            }
            .onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
                viewModelScope.launch {
                    _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error"))
                }
            }
    }

    private fun loadSavedModel() {
        viewModelScope.launch {
            val savedModel = preferencesRepository.getSelectedModel()
            savedModel?.let { model ->
                _uiState.update { it.copy(selectedModel = model) }
            }
        }
    }

    private fun handleDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun handleClearChat() {
        viewModelScope.launch {
            chatRepository.deleteAllData()
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

    private fun handleViewDatabase() {
        viewModelScope.launch {
            val databaseInfo = getDatabaseInfoUseCase()
            _uiState.update { it.copy(databaseInfo = databaseInfo) }
            _events.emit(ChatEvent.ShowDatabaseInfo(databaseInfo))
        }
    }

    private fun handleDismissDatabaseView() {
        _uiState.update { it.copy(databaseInfo = null) }
    }

    private fun handleCheckMessageThreshold(message: Message) {
        viewModelScope.launch {
            compressionScheduler.onMessageSaved(
                message = message,
                modelForSummary = _uiState.value.selectedModel
            )
        }
    }

    private fun buildMessagesWithContext(currentInput: String, personalizationPrompt: String): List<ChatMessageDto> {
        val sessionContext = _uiState.value.sessionContext
        val messages = mutableListOf<ChatMessageDto>()
        
        val contextParts = mutableListOf<String>()
        
        contextParts.add("Персонализация пользователя:\n$personalizationPrompt")
        
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
                "\n\nИспользуй этот контекст для понимания истории разговора и персонализации пользователя. " +
                "Отвечай с учетом предыдущих обсуждений и интересов пользователя. " +
                "Если пользователь спрашивает о чем-то из прошлого, используй этот контекст для ответа. " +
                "Предлагай релевантные темы и вопросы на основе интересов пользователя."
            messages.add(ChatMessageDto(role = "system", content = systemMessage))
        }
        
        messages.addAll(currentMessages)
        messages.add(ChatMessageDto(role = "user", content = currentInput))
        
        return messages
    }
    
    private fun handleStartVoiceInput() {
        if (_uiState.value.isListening) {
            return
        }
        if (!speechRecognizerManager.isAvailable()) {
            _uiState.update { it.copy(speechError = "Speech recognition is not available") }
            return
        }
        _uiState.update { 
            it.copy(
                isListening = true,
                speechError = null
            )
        }
        speechRecognitionJob = speechRecognizerManager.startListening()
            .onEach { result ->
                when (result) {
                    is SpeechResult.Listening -> {
                        _uiState.update { it.copy(isListening = true) }
                    }
                    is SpeechResult.Speaking -> {
                        // User is speaking, keep listening state
                    }
                    is SpeechResult.AudioLevel -> {
                        // Audio level updates, can be used for visualization
                    }
                    is SpeechResult.Processing -> {
                        // Processing results
                    }
                    is SpeechResult.PartialResult -> {
                        _uiState.update { it.copy(currentInput = result.text) }
                    }
                    is SpeechResult.Success -> {
                        _uiState.update { 
                            it.copy(
                                currentInput = result.text,
                                isListening = false
                            )
                        }
                        sendMessageFromVoice(result.text)
                    }
                    is SpeechResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isListening = false,
                                speechError = result.message
                            )
                        }
                    }
                }
            }
            .catch { error ->
                _uiState.update { 
                    it.copy(
                        isListening = false,
                        speechError = error.message ?: "Unknown error occurred"
                    )
                }
            }
            .launchIn(viewModelScope)
    }
    
    private fun handleStopVoiceInput() {
        speechRecognizerManager.stopListening()
        speechRecognitionJob?.cancel()
        speechRecognitionJob = null
        _uiState.update { it.copy(isListening = false) }
    }
    
    private fun handleDismissSpeechError() {
        _uiState.update { it.copy(speechError = null) }
    }
    
    private fun sendMessageFromVoice(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return
        
        val userMessage = Message(
            content = trimmedText,
            isUser = true
        )
        
        viewModelScope.launch {
            chatRepository.saveMessage(userMessage)
            handleCheckMessageThreshold(userMessage)
            _uiState.update { state ->
                state.copy(
                    currentInput = "",
                    isLoading = true,
                    error = null
                )
            }
            
            val userProfile = personalizeUserUseCase(trimmedText, _uiState.value.selectedModel)
                .getOrElse {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = "Ошибка персонализации: ${it.message}"
                        )
                    }
                    _events.emit(ChatEvent.ShowError("Ошибка персонализации: ${it.message}"))
                    return@launch
                }
            
            val messagesWithContext = buildMessagesWithContext(trimmedText, userProfile.personalizationPrompt)
            
            sendMessageUseCase(_uiState.value.selectedModel, messagesWithContext)
                .onSuccess { aiMessage ->
                    chatRepository.saveMessage(aiMessage)
                    handleCheckMessageThreshold(aiMessage)
                    val updatedMessages = _uiState.value.messages + userMessage + aiMessage
                    val comparison = compareModelMetricsUseCase(updatedMessages, trimmedText)
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
    
    private fun loadToolsPreferences() {
        viewModelScope.launch {
            val ollamaEnabled = preferencesRepository.getOllamaEnabled()
            val ollamaSelectedFiles = preferencesRepository.getOllamaSelectedFiles()
            val rerankingEnabled = preferencesRepository.getRerankingEnabled()
            val projectHelperEnabled = preferencesRepository.getProjectHelperEnabled()
            val projectUserAssistantEnabled = preferencesRepository.getProjectUserAssistantEnabled()
            val userFormatType = preferencesRepository.getUserFormatType()
            val projectFilesEnabled = preferencesRepository.getProjectFilesEnabled()
            val projectAnalyticEnabled = preferencesRepository.getProjectAnalyticEnabled()
            
            _uiState.update { state ->
                state.copy(
                    ollamaEnabled = ollamaEnabled,
                    ollamaSelectedFiles = ollamaSelectedFiles,
                    rerankingEnabled = rerankingEnabled,
                    projectHelperEnabled = projectHelperEnabled,
                    projectUserAssistantEnabled = projectUserAssistantEnabled,
                    userFormatType = userFormatType,
                    projectFilesEnabled = projectFilesEnabled,
                    projectAnalyticEnabled = projectAnalyticEnabled
                )
            }
        }
    }
    
    private fun handleShowToolsDialog() {
        _uiState.update { it.copy(showToolsDialog = true) }
    }
    
    private fun handleDismissToolsDialog() {
        _uiState.update { it.copy(showToolsDialog = false) }
    }
    
    private fun handleToggleOllama(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOllamaEnabled(enabled)
            _uiState.update { it.copy(ollamaEnabled = enabled) }
        }
    }
    
    private fun handleSelectOllamaFile(filePath: String?) {
        viewModelScope.launch {
            val currentFiles = _uiState.value.ollamaSelectedFiles.toMutableList()
            if (filePath != null && !currentFiles.contains(filePath) && currentFiles.size < 5) {
                currentFiles.add(filePath)
                preferencesRepository.setOllamaSelectedFiles(currentFiles)
                _uiState.update { it.copy(ollamaSelectedFiles = currentFiles) }
            }
        }
    }
    
    private fun handleRemoveOllamaFile(filePath: String) {
        viewModelScope.launch {
            val currentFiles = _uiState.value.ollamaSelectedFiles.toMutableList()
            currentFiles.remove(filePath)
            preferencesRepository.setOllamaSelectedFiles(currentFiles)
            _uiState.update { it.copy(ollamaSelectedFiles = currentFiles) }
        }
    }
    
    private fun handleToggleReranking(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRerankingEnabled(enabled)
            _uiState.update { it.copy(rerankingEnabled = enabled) }
        }
    }
    
    private fun handleToggleProjectHelper(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setProjectHelperEnabled(enabled)
            _uiState.update { it.copy(projectHelperEnabled = enabled) }
        }
    }
    
    private fun handleToggleProjectUserAssistant(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setProjectUserAssistantEnabled(enabled)
            _uiState.update { it.copy(projectUserAssistantEnabled = enabled) }
        }
    }
    
    private fun handleSetUserFormatType(formatType: String) {
        viewModelScope.launch {
            preferencesRepository.setUserFormatType(formatType)
            _uiState.update { it.copy(userFormatType = formatType) }
        }
    }
    
    private fun handleToggleProjectFiles(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setProjectFilesEnabled(enabled)
            _uiState.update { it.copy(projectFilesEnabled = enabled) }
        }
    }
    
    private fun handleToggleProjectAnalytic(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setProjectAnalyticEnabled(enabled)
            _uiState.update { it.copy(projectAnalyticEnabled = enabled) }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        handleStopVoiceInput()
        speechRecognizerManager.cancel()
    }
}

