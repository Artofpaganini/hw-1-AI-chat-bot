package com.example.aiagentchat.feature.chat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.model.SessionContext
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import com.example.aiagentchat.feature.chat.domain.usecase.CompareModelMetricsUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.CompressionScheduler
import com.example.aiagentchat.feature.chat.domain.usecase.ContextInitializer
import com.example.aiagentchat.feature.chat.domain.usecase.ExportChatHistoryUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SwitchAiModelUseCase
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.service.TextIndexingService
import com.example.aiagentchat.feature.chat.data.service.VectorDatabaseService
import com.example.aiagentchat.feature.chat.data.service.MatchedChunkWithBook
import com.example.aiagentchat.feature.chat.data.service.GitFileDetector
import com.example.aiagentchat.feature.chat.data.repository.ReviewRepository
import com.example.aiagentchat.feature.chat.data.api.OllamaApi
import com.example.aiagentchat.feature.chat.data.api.ProjectHelperMcpApi
import com.example.aiagentchat.feature.chat.data.api.GitHubMcpApi
import com.example.aiagentchat.feature.chat.data.api.JsonRpcRequest
import com.example.aiagentchat.feature.chat.data.api.JsonRpcResponse
import com.example.aiagentchat.core.network.ApiClient
import android.os.Environment
import java.io.File
import com.google.gson.Gson
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
    private val preferencesManager: com.example.aiagentchat.core.common.preferences.PreferencesManager,
    private val textIndexingService: TextIndexingService,
    private val vectorDatabaseService: com.example.aiagentchat.feature.chat.data.service.VectorDatabaseService,
    private val ollamaApi: OllamaApi,
    private val reviewRepository: ReviewRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Legacy compatibility - expose state as 'state' for old code
    val state: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()
    
    private val projectHelperMcpApi: ProjectHelperMcpApi by lazy {
        val baseUrl = "http://10.0.2.2:8081/"
        val retrofit = ApiClient.createRetrofit(baseUrl)
        retrofit.create(ProjectHelperMcpApi::class.java)
    }
    
    private val githubMcpApi: GitHubMcpApi? by lazy {
        if (preferencesManager.githubMcpEnabled) {
            try {
                val baseUrl = "http://10.0.2.2:8083/"
                val retrofit = ApiClient.createRetrofit(baseUrl)
                retrofit.create(GitHubMcpApi::class.java)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to initialize GitHub MCP API", e)
                null
            }
        } else {
            null
        }
    }
    
    private val gson = Gson()
    private var requestId = 1

    init {
        updateConfiguredModels()
        loadMessages()
        loadSessionContext()
        observeContextSummaries()
        loadOllamaState()
        loadRerankingState()
        loadGitHubMcpState()
        loadProjectReviewModeState()
        loadProjectTeamAssistantState()
        loadLocalMcpServerState()
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
            is ChatAction.ToggleOllama -> handleToggleOllama(action.enabled)
            is ChatAction.ToggleReranking -> handleToggleReranking(action.enabled)
            is ChatAction.ExportJson -> handleExportJson()
            is ChatAction.DismissJsonExport -> handleDismissJsonExport()
            is ChatAction.ToggleGitHubMcp -> handleToggleGitHubMcp(action.enabled)
            is ChatAction.ToggleProjectReviewMode -> handleToggleProjectReviewMode(action.enabled)
            is ChatAction.ToggleProjectTeamAssistant -> handleToggleProjectTeamAssistant(action.enabled)
            is ChatAction.ToggleLocalMcpServer -> handleToggleLocalMcpServer(action.enabled)
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
            val projectReviewModeEnabled = _uiState.value.projectReviewModeEnabled
            val projectTeamAssistantEnabled = _uiState.value.projectTeamAssistantEnabled
            
            // Проверяем команду /help
            if (currentInput.startsWith("/help", ignoreCase = true)) {
                val question = currentInput.removePrefix("/help").trim()
                if (question.isNotBlank()) {
                    handleHelpCommand(question, userMessage)
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = "Please provide a question after /help"
                        )
                    }
                }
                return@launch
            }
            
            // Проверяем команду /review
            if (currentInput.startsWith("/review", ignoreCase = true)) {
                executeProjectReview(userMessage)
                return@launch
            }
            
            // Проверяем команду /tasks
            if (currentInput.startsWith("/tasks", ignoreCase = true)) {
                executeTasksCommand(userMessage)
                return@launch
            }
            
            // Если включен Project Review Mode + Ollama Vector Search, используем RAG с файлами проекта
            if (projectReviewModeEnabled && ollamaEnabled) {
                handleSendMessageWithProjectReviewMode(currentInput, userMessage)
            } else if (ollamaEnabled) {
                handleSendMessageWithOllama(currentInput, userMessage)
            } else {
                handleSendMessageWithoutOllama(currentInput, userMessage)
            }
        }
    }

    private suspend fun handleSendMessageWithOllama(currentInput: String, userMessage: Message) {
        try {
            // Получаем список всех проиндексированных книг
            val allBooks = vectorDatabaseService.getAllBooksSync()
            
            if (allBooks.isEmpty()) {
                val errorMsg = "No indexed books found. Please index a book first."
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
            
            android.util.Log.d("ChatViewModel", "Total indexed books: ${allBooks.size}")
            android.util.Log.d("ChatViewModel", "Available books: ${allBooks.map { it.title }}")
            
            // Ищем во всех проиндексированных книгах
            val bookIdsToSearch = allBooks.map { it.bookId }
            android.util.Log.d("ChatViewModel", "Searching in all ${allBooks.size} indexed books")
            
            val queryEmbedding = generateQueryEmbedding(currentInput)
            
            // Ищем похожие чанки в выбранных книгах
            val allMatchedChunks = vectorDatabaseService.findSimilarChunks(
                queryEmbedding = queryEmbedding,
                bookIds = bookIdsToSearch,
                limit = 10
            )
            
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
                android.util.Log.d("ChatViewModel", "🔄 Reranking enabled: using LLM-as-a-reranker (${OllamaApi.DEFAULT_RERANKING_MODEL}) for ${allMatchedChunks.size} candidate chunks")
                val rerankedChunks = performLlmReranking(currentInput, allMatchedChunks)
                android.util.Log.d("ChatViewModel", "✅ Reranking completed: ${rerankedChunks.size} chunks reranked")
                // Берем топ-5 самых релевантных чанков (отсортированы по убыванию, где 1.0 = максимальная релевантность)
                val top5Chunks = rerankedChunks.take(5)
                android.util.Log.d("ChatViewModel", "📌 Selected top 5 chunks: ${top5Chunks.mapIndexed { i, chunk -> "#${chunk.chunkIndex} from '${chunk.bookTitle}' (score=${chunk.similarity})" }}")
                top5Chunks
            } else {
                android.util.Log.d("ChatViewModel", "⏭️ Reranking disabled: using top ${allMatchedChunks.size} matched chunks by cosine similarity")
                allMatchedChunks.take(5)
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
            
            android.util.Log.d("ChatViewModel", "Using RAG with Ollama (vector search), ${matchedChunks.size} matched chunks${if (rerankingEnabled) " (after reranking)" else " (without reranking)"}")
            
            // Логируем оценки релевантности для каждого чанка
            matchedChunks.forEachIndexed { index, chunk ->
                val scorePercent = (chunk.similarity * 100).toInt()
                if (rerankingEnabled) {
                    android.util.Log.d("ChatViewModel", "Chunk #${chunk.chunkIndex} from '${chunk.bookTitle}': relevance score = ${chunk.similarity} ($scorePercent%)")
                } else {
                    android.util.Log.d("ChatViewModel", "Chunk #${chunk.chunkIndex} from '${chunk.bookTitle}': similarity score = ${chunk.similarity} ($scorePercent%)")
                }
            }
            
            // Формируем контекст с информацией о релевантности каждого чанка
            val contextText = matchedChunks.joinToString("\n\n---\n\n") { chunk ->
                val relevanceInfo = if (rerankingEnabled) {
                    val relevancePercent = (chunk.similarity * 100).toInt()
                    "Chunk #${chunk.chunkIndex} from '${chunk.bookTitle}' (Релевантность: ${chunk.similarity} / ${relevancePercent}%):\n${chunk.text}"
                } else {
                    val similarityPercent = (chunk.similarity * 100).toInt()
                    "Chunk #${chunk.chunkIndex} from '${chunk.bookTitle}' (Похожесть: ${chunk.similarity} / ${similarityPercent}%):\n${chunk.text}"
                }
                relevanceInfo
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
                if (rerankingEnabled) {
                    appendLine("IMPORTANT: After your answer, please provide:")
                    appendLine("1. A list of chunk numbers that were used to answer the question")
                    appendLine("2. The relevance score (релевантность) for each chunk (shown in the context above)")
                    appendLine("3. A brief summary (1-2 sentences) for each chunk about what information it contained")
                    appendLine()
                    appendLine("Format your response as follows:")
                    appendLine("[Your answer to the question]")
                    appendLine()
                    appendLine("---")
                    appendLine("📚 Источники (chunks):")
                    appendLine("  • Chunk #N from 'Book Title' (Релевантность: X.XX): [brief summary]")
                    appendLine("  • Chunk #M from 'Book Title' (Релевантность: Y.YY): [brief summary]")
                    appendLine("---")
                } else {
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
            }
            
            val messagesWithContext = listOf(
                ChatMessageDto(role = "user", content = enhancedPrompt)
            )
            
            sendMessageUseCase(
                model = _uiState.value.selectedModel,
                messages = messagesWithContext
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
        
        android.util.Log.d("ChatViewModel", "Sending message without Ollama")
        
        sendMessageUseCase(
            model = _uiState.value.selectedModel,
            messages = messagesWithContext
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
            preferencesManager.resetAllToggles()
            _uiState.update { 
                it.copy(
                    messages = emptyList(), 
                    metricsComparison = null,
                    ollamaEnabled = false,
                    rerankingEnabled = false,
                    githubMcpEnabled = false,
                    projectReviewModeEnabled = false,
                    projectTeamAssistantEnabled = false,
                    localMcpServerEnabled = false
                ) 
            }
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
        viewModelScope.launch {
            // Получаем список всех книг из БД
            val allBooks = vectorDatabaseService.getAllBooksSync()
            val jsonContent = if (allBooks.isEmpty()) {
                "{}"
            } else {
                // Формируем JSON для отображения (опционально, можно просто показать список книг)
                com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                    mapOf(
                        "books" to allBooks.map { 
                            mapOf(
                                "bookId" to it.bookId,
                                "title" to it.title,
                                "fileHash" to it.fileHash,
                                "chunkCount" to it.chunkCount,
                                "timestamp" to it.timestamp
                            )
                        }
                    )
                )
            }
            _uiState.update { it.copy(exportedJson = jsonContent) }
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

    
    private fun handleShowMcpTools() {
        _uiState.update { it.copy(showMcpToolsDialog = true) }
    }

    private fun handleDismissMcpTools() {
        _uiState.update { it.copy(showMcpToolsDialog = false) }
    }
    
    private fun loadOllamaState() {
        val enabled = preferencesManager.ollamaEnabled
        _uiState.update { 
            it.copy(ollamaEnabled = enabled) 
        }
        if (enabled) {
            checkOllamaConnection()
        }
    }
    
    private fun handleToggleOllama(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle Ollama: $enabled")
        preferencesManager.ollamaEnabled = enabled
        _uiState.update { it.copy(ollamaEnabled = enabled) }
        if (enabled) {
            android.util.Log.i("ChatViewModel", "🚀 Ollama enabled - checking server connection...")
            android.util.Log.i("ChatViewModel", "📝 Note: Run './setup-ollama.sh' on your Mac to start Ollama server")
            checkOllamaConnection()
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
    
    private fun loadProjectTeamAssistantState() {
        val enabled = preferencesManager.projectTeamAssistantEnabled
        _uiState.update { 
            it.copy(projectTeamAssistantEnabled = enabled) 
        }
    }
    
    private fun handleToggleProjectTeamAssistant(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle Project Team Assistant: $enabled")
        preferencesManager.projectTeamAssistantEnabled = enabled
        _uiState.update { it.copy(projectTeamAssistantEnabled = enabled) }
        
        if (enabled) {
            viewModelScope.launch {
                indexProjectFilesForTeamAssistant()
            }
        }
    }
    
    private fun loadLocalMcpServerState() {
        val enabled = preferencesManager.localMcpServerEnabled
        _uiState.update { 
            it.copy(localMcpServerEnabled = enabled) 
        }
    }
    
    private fun handleToggleLocalMcpServer(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle Local MCP Server: $enabled")
        preferencesManager.localMcpServerEnabled = enabled
        _uiState.update { it.copy(localMcpServerEnabled = enabled) }
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

    private suspend fun buildMessagesWithContext(currentInput: String): List<ChatMessageDto> {
        val ollamaEnabled = _uiState.value.ollamaEnabled
        
        // Если включен Ollama, используем векторный поиск
        if (ollamaEnabled) {
            val allBooks = vectorDatabaseService.getAllBooksSync()
            val hasVectors = allBooks.isNotEmpty()
            
            if (hasVectors) {
                try {
                    val queryEmbedding = generateQueryEmbedding(currentInput)
                    val matchedChunks = vectorDatabaseService.findSimilarChunks(
                        queryEmbedding = queryEmbedding,
                        bookIds = null, // Ищем во всех книгах
                        limit = 5
                    )
                    
                    if (matchedChunks.isNotEmpty()) {
                        val contextText = matchedChunks.joinToString("\n\n---\n\n") { 
                            "Chunk #${it.chunkIndex} from '${it.bookTitle}':\n${it.text}"
                        }
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
        
        // Обычная логика с контекстом
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
        candidateChunks: List<MatchedChunkWithBook>
    ): List<MatchedChunkWithBook> {
        android.util.Log.d("ChatViewModel", "🔄 Starting LLM reranking for ${candidateChunks.size} candidate chunks")
        android.util.Log.d("ChatViewModel", "Using model: ${OllamaApi.DEFAULT_RERANKING_MODEL}")
        
        // Оцениваем релевантность каждого кандидата через LLM
        val rerankedChunks = candidateChunks.mapIndexed { index, chunk ->
            try {
                android.util.Log.d("ChatViewModel", "Evaluating relevance for chunk ${index + 1}/${candidateChunks.size} (chunk #${chunk.chunkIndex} from '${chunk.bookTitle}')")
                val relevanceScore = evaluateRelevanceWithLlm(query, chunk.text, chunk.bookTitle)
                android.util.Log.d("ChatViewModel", "✅ Chunk ${index + 1}: relevance score = $relevanceScore (chunk #${chunk.chunkIndex} from '${chunk.bookTitle}')")
                chunk.copy(similarity = relevanceScore)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "❌ Error evaluating relevance for chunk ${index + 1} (chunk #${chunk.chunkIndex})", e)
                // В случае ошибки используем оригинальную similarity
                chunk
            }
        }
        
        // Сортируем по relevance score по убыванию (1.0 = максимальная релевантность)
        val sorted = rerankedChunks.sortedByDescending { it.similarity }
        android.util.Log.d("ChatViewModel", "📊 Reranking completed. Top 5 scores: ${sorted.take(5).mapIndexed { i, chunk -> "Chunk #${chunk.chunkIndex} from '${chunk.bookTitle}'=${chunk.similarity}" }}")
        
        return sorted
    }
    
    private suspend fun evaluateRelevanceWithLlm(query: String, chunkText: String, source: String): Float {
        // Формируем промпт для оценки релевантности согласно требованиям
        val prompt = buildString {
            appendLine("Оцени релевантность текста запросу по шкале от 0.0 до 1.0.")
            appendLine("Запрос: \"$query\"")
            val sourceText = if (source.isNotBlank()) source else "Неизвестный источник"
            appendLine("Источник: \"$sourceText\"")
            appendLine("Текст: \"${chunkText.take(1000)}\"") // Ограничиваем длину текста для промпта
            appendLine("Ответь Название источника + текст + релевантность текста в виде \"Релевантность число\". Никаких пояснений.")
        }
        
        android.util.Log.d("ChatViewModel", "📝 Reranking prompt: ${prompt.take(200)}...")
        
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
    
    private suspend fun indexProjectFiles() {
        try {
            android.util.Log.d("ChatViewModel", "🚀 Starting project files indexing...")
            
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/call",
                params = mapOf(
                    "name" to "index_project_files",
                    "arguments" to emptyMap<String, Any>()
                )
            )
            
            val response = projectHelperMcpApi.sendRequest(request)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    android.util.Log.e("ChatViewModel", "❌ MCP Error: ${body.error.message}")
                    _events.emit(ChatEvent.ShowError("Indexing failed: ${body.error.message}"))
                } else {
                    val result = body.result
                    val message = result?.get("content") as? String ?: "Project files indexed successfully"
                    android.util.Log.i("ChatViewModel", "✅ Project files indexed: $message")
                    _events.emit(ChatEvent.ShowError("✅ $message"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("ChatViewModel", "❌ HTTP Error: ${response.code()} - $errorBody")
                _events.emit(ChatEvent.ShowError("Indexing failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "❌ Error indexing project files", e)
            _events.emit(ChatEvent.ShowError("Error indexing project files: ${e.message}"))
        }
    }
    
    private suspend fun handleHelpCommand(question: String, userMessage: Message) {
        try {
            android.util.Log.d("ChatViewModel", "🔍 Processing /help command: $question")
            
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/call",
                params = mapOf(
                    "name" to "search_project_files",
                    "arguments" to mapOf(
                        "query" to question,
                        "reranking_enabled" to _uiState.value.rerankingEnabled
                    )
                )
            )
            
            val response = projectHelperMcpApi.sendRequest(request)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    android.util.Log.e("ChatViewModel", "❌ MCP Error: ${body.error.message}")
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = "Search failed: ${body.error.message}"
                        )
                    }
                    _events.emit(ChatEvent.ShowError("Search failed: ${body.error.message}"))
                    return
                }
                
                val result = body.result
                // Парсим content из массива объектов (MCP формат)
                val contentArray = result?.get("content") as? List<*>
                val content = if (contentArray != null && contentArray.isNotEmpty()) {
                    val firstContent = contentArray.firstOrNull() as? Map<*, *>
                    firstContent?.get("text") as? String ?: firstContent?.get("content") as? String
                } else {
                    result?.get("content") as? String
                }
                
                if (content != null && content.isNotBlank()) {
                    android.util.Log.d("ChatViewModel", "✅ Found relevant context from project files: ${content.take(100)}...")
                    
                    // Извлекаем информацию об источнике и релевантности из content
                    val sourceRegex = Regex("Источник:\\s*(.+)")
                    val relevanceRegex = Regex("Релевантность:\\s*([0-9.]+)")
                    val sourceMatch = sourceRegex.find(content)
                    val relevanceMatch = relevanceRegex.find(content)
                    val source = sourceMatch?.groupValues?.get(1)?.trim() ?: "Неизвестный источник"
                    val relevance = relevanceMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                    
                    // Убираем информацию об источнике и релевантности из контекста для промпта
                    val cleanContent = content
                        .replace(Regex("Источник:\\s*.+"), "")
                        .replace(Regex("Релевантность:\\s*[0-9.]+"), "")
                        .trim()
                    
                    val enhancedPrompt = buildString {
                        appendLine("Based on the following context from project documentation, please answer the user's question.")
                        appendLine()
                        appendLine("=== RELEVANT CONTEXT FROM PROJECT ===")
                        appendLine(cleanContent)
                        appendLine("=== END OF CONTEXT ===")
                        appendLine()
                        appendLine("=== USER QUESTION ===")
                        appendLine(question)
                        appendLine("=== END OF QUESTION ===")
                    }
                    
                    val messagesWithContext = listOf(
                        ChatMessageDto(role = "user", content = enhancedPrompt)
                    )
                    
                    sendMessageUseCase(
                        model = _uiState.value.selectedModel,
                        messages = messagesWithContext
                    )
                        .onSuccess { aiMessage ->
                            val relevancePercent = (relevance * 100).toInt()
                            val finalContent = buildString {
                                appendLine(aiMessage.content)
                                appendLine()
                                appendLine("---")
                                appendLine("📚 Источник: $source")
                                appendLine("📊 Релевантность: ${relevance} (${relevancePercent}%)")
                                appendLine("С Project Helper")
                            }
                            val finalMessage = aiMessage.copy(content = finalContent)
                            chatRepository.saveMessage(finalMessage)
                            handleCheckMessageThreshold(finalMessage)
                            val updatedMessages = _uiState.value.messages + userMessage + finalMessage
                            val comparison = compareModelMetricsUseCase(updatedMessages, question)
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    metricsComparison = comparison
                                )
                            }
                        }
                        .onFailure { error ->
                            android.util.Log.e("ChatViewModel", "Error sending message with project context", error)
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    error = error.message ?: "Unknown error occurred"
                                )
                            }
                            _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error occurred"))
                        }
                } else {
                    android.util.Log.w("ChatViewModel", "⚠️ No relevant context found")
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = "No relevant information found in project files"
                        )
                    }
                    _events.emit(ChatEvent.ShowError("No relevant information found in project files"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("ChatViewModel", "❌ HTTP Error: ${response.code()} - $errorBody")
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Search failed: HTTP ${response.code()}"
                    )
                }
                _events.emit(ChatEvent.ShowError("Search failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "❌ Error processing /help command", e)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
            _events.emit(ChatEvent.ShowError("Error processing /help command: ${e.message}"))
        }
    }
    
    private suspend fun handleSendMessageWithProjectReviewMode(currentInput: String, userMessage: Message) {
        try {
            android.util.Log.d("ChatViewModel", "🔍 Processing message with Project Review Mode: $currentInput")
            
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/call",
                params = mapOf(
                    "name" to "search_project_files",
                    "arguments" to mapOf(
                        "query" to currentInput,
                        "reranking_enabled" to _uiState.value.rerankingEnabled
                    )
                )
            )
            
            val response = projectHelperMcpApi.sendRequest(request)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    android.util.Log.e("ChatViewModel", "❌ MCP Error: ${body.error.message}")
                    // Fallback to regular Ollama if Project Review Mode fails
                    handleSendMessageWithOllama(currentInput, userMessage)
                    return
                }
                
                val result = body.result
                // Парсим content из массива объектов (MCP формат)
                val contentArray = result?.get("content") as? List<*>
                val content = if (contentArray != null && contentArray.isNotEmpty()) {
                    val firstContent = contentArray.firstOrNull() as? Map<*, *>
                    firstContent?.get("text") as? String ?: firstContent?.get("content") as? String
                } else {
                    result?.get("content") as? String
                }
                
                if (content != null && content.isNotBlank()) {
                    android.util.Log.d("ChatViewModel", "✅ Found relevant context from project files: ${content.take(100)}...")
                    
                    // Извлекаем информацию об источнике и релевантности из content
                    val sourceRegex = Regex("Источник:\\s*(.+)")
                    val relevanceRegex = Regex("Релевантность:\\s*([0-9.]+)")
                    val sourceMatch = sourceRegex.find(content)
                    val relevanceMatch = relevanceRegex.find(content)
                    val source = sourceMatch?.groupValues?.get(1)?.trim() ?: "Неизвестный источник"
                    val relevance = relevanceMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                    
                    // Убираем информацию об источнике и релевантности из контекста для промпта
                    val cleanContent = content
                        .replace(Regex("Источник:\\s*.+"), "")
                        .replace(Regex("Релевантность:\\s*[0-9.]+"), "")
                        .trim()
                    
                    val enhancedPrompt = buildString {
                        appendLine("Based on the following context from project documentation, please answer the user's question.")
                        appendLine()
                        appendLine("=== RELEVANT CONTEXT FROM PROJECT ===")
                        appendLine(cleanContent)
                        appendLine("=== END OF CONTEXT ===")
                        appendLine()
                        appendLine("=== USER QUESTION ===")
                        appendLine(currentInput)
                        appendLine("=== END OF QUESTION ===")
                    }
                    
                    val messagesWithContext = listOf(
                        ChatMessageDto(role = "user", content = enhancedPrompt)
                    )
                    
                    sendMessageUseCase(
                        model = _uiState.value.selectedModel,
                        messages = messagesWithContext
                    )
                        .onSuccess { aiMessage ->
                            val relevancePercent = (relevance * 100).toInt()
                            val finalContent = buildString {
                                appendLine(aiMessage.content)
                                appendLine()
                                appendLine("---")
                                appendLine("📚 Источник: $source")
                                appendLine("📊 Релевантность: ${relevance} (${relevancePercent}%)")
                                appendLine("С Project Helper и Ollama")
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
                            android.util.Log.e("ChatViewModel", "Error sending message with project context", error)
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    error = error.message ?: "Unknown error occurred"
                                )
                            }
                            _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error occurred"))
                        }
                } else {
                    android.util.Log.w("ChatViewModel", "⚠️ No relevant context found, falling back to regular Ollama")
                    // Fallback to regular Ollama if no context found
                    handleSendMessageWithOllama(currentInput, userMessage)
                }
            } else {
                android.util.Log.w("ChatViewModel", "⚠️ Project Helper request failed, falling back to regular Ollama")
                // Fallback to regular Ollama if request fails
                handleSendMessageWithOllama(currentInput, userMessage)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "❌ Error processing message with Project Helper", e)
            // Fallback to regular Ollama on error
            handleSendMessageWithOllama(currentInput, userMessage)
        }
    }
    
    private fun loadGitHubMcpState() {
        val enabled = preferencesManager.githubMcpEnabled
        _uiState.update { 
            it.copy(githubMcpEnabled = enabled) 
        }
    }
    
    private fun handleToggleGitHubMcp(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle GitHub MCP: $enabled")
        preferencesManager.githubMcpEnabled = enabled
        _uiState.update { it.copy(githubMcpEnabled = enabled) }
        
        if (enabled) {
            // Initialize GitHub MCP API when enabled
            viewModelScope.launch {
                try {
                    val baseUrl = "http://10.0.2.2:8083/"
                    val retrofit = ApiClient.createRetrofit(baseUrl)
                    val api = retrofit.create(GitHubMcpApi::class.java)
                    // Test connection
                    val testRequest = JsonRpcRequest(
                        id = 1,
                        method = "initialize",
                        params = mapOf(
                            "protocolVersion" to "2024-11-05",
                            "capabilities" to emptyMap<String, Any>(),
                            "clientInfo" to mapOf(
                                "name" to "ai-agent-chat",
                                "version" to "1.0.0"
                            )
                        )
                    )
                    val testResponse = api.sendRequest(testRequest)
                    if (testResponse.isSuccessful) {
                        android.util.Log.i("ChatViewModel", "✅ GitHub MCP API initialized and connected")
                    } else {
                        android.util.Log.w("ChatViewModel", "⚠️ GitHub MCP API initialized but connection test failed")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "❌ Failed to initialize GitHub MCP API", e)
                    _events.emit(ChatEvent.ShowError("Failed to initialize GitHub MCP: ${e.message}"))
                }
            }
        }
    }
    
    private fun loadProjectReviewModeState() {
        val enabled = preferencesManager.projectReviewModeEnabled
        _uiState.update { 
            it.copy(projectReviewModeEnabled = enabled) 
        }
    }
    
    private fun handleToggleProjectReviewMode(enabled: Boolean) {
        android.util.Log.d("ChatViewModel", "Toggle Project Review Mode: $enabled")
        preferencesManager.projectReviewModeEnabled = enabled
        _uiState.update { it.copy(projectReviewModeEnabled = enabled) }
    }
    
    private suspend fun executeProjectReview(userMessage: Message) {
        if (reviewRepository == null) {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    error = "Review repository not available"
                )
            }
            _events.emit(ChatEvent.ShowError("Review repository not available"))
            return
        }
        
        try {
            android.util.Log.d("ChatViewModel", "🔍 Starting project review...")
            
            // Get changed files
            val changedFilesResult = reviewRepository.getChangedFiles()
            if (changedFilesResult.isFailure) {
                val exception = changedFilesResult.exceptionOrNull()
                val errorMessage = exception?.message?.takeIf { it.isNotBlank() } 
                    ?: exception?.toString()?.takeIf { it.isNotBlank() }
                    ?: "Unknown error"
                val fullErrorMessage = "Failed to detect changed files: $errorMessage"
                android.util.Log.e("ChatViewModel", fullErrorMessage, exception)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = fullErrorMessage
                    )
                }
                _events.emit(ChatEvent.ShowError(fullErrorMessage))
                return
            }
            
            val changedFiles = changedFilesResult.getOrNull() ?: emptyList()
            if (changedFiles.isEmpty()) {
                val errorMsg = "No changed files found. Make sure you have uncommitted changes."
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
                _events.emit(ChatEvent.ShowError(errorMsg))
                return
            }
            
            android.util.Log.d("ChatViewModel", "Found ${changedFiles.size} changed files")
            
            // Embed files if Project Review Mode is enabled
            if (_uiState.value.projectReviewModeEnabled) {
                reviewRepository.embedFilesForReview(changedFiles)
                    .onFailure { error ->
                        android.util.Log.w("ChatViewModel", "Failed to embed files: ${error.message}")
                    }
            }
            
            // Get PR diff if GitHub MCP is enabled (optional)
            var prDiff: String? = null
            if (_uiState.value.githubMcpEnabled && githubMcpApi != null) {
                // Try to extract PR info from git config or environment
                // For now, we'll skip PR diff if not explicitly provided
                // In the future, we can parse git remote URL to get owner/repo
                // and use git branch name or environment variables to get PR number
                android.util.Log.d("ChatViewModel", "GitHub MCP enabled, but PR diff requires explicit PR number (owner, repo, pullNumber)")
            }
            
            // Generate review prompt
            val reviewPrompt = reviewRepository.generateReviewPrompt(changedFiles, prDiff)
            
            // Send to AI for review
            val messagesWithContext = listOf(
                ChatMessageDto(role = "user", content = reviewPrompt)
            )
            
            sendMessageUseCase(
                model = _uiState.value.selectedModel,
                messages = messagesWithContext
            )
                .onSuccess { aiMessage ->
                    val finalContent = buildString {
                        appendLine(aiMessage.content)
                        appendLine()
                        appendLine("---")
                        appendLine("📝 Reviewed ${changedFiles.size} file(s):")
                        changedFiles.forEach { file ->
                            appendLine("  • ${java.io.File(file).name}")
                        }
                        appendLine("🔍 Code Review")
                    }
                    val finalMessage = aiMessage.copy(content = finalContent)
                    chatRepository.saveMessage(finalMessage)
                    handleCheckMessageThreshold(finalMessage)
                    val updatedMessages = _uiState.value.messages + userMessage + finalMessage
                    val comparison = compareModelMetricsUseCase(updatedMessages, "/review")
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            metricsComparison = comparison
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("ChatViewModel", "Error executing project review", error)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error occurred"
                        )
                    }
                    _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error occurred"))
                }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "❌ Error executing project review", e)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
            _events.emit(ChatEvent.ShowError("Error executing project review: ${e.message}"))
        }
    }
    
    private suspend fun indexProjectFilesForTeamAssistant() {
        try {
            android.util.Log.d("ChatViewModel", "🚀 Starting project files indexing for Team Assistant...")
            
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/call",
                params = mapOf(
                    "name" to "index_project_files",
                    "arguments" to emptyMap<String, Any>()
                )
            )
            
            val response = projectHelperMcpApi.sendRequest(request)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    android.util.Log.e("ChatViewModel", "❌ MCP Error: ${body.error.message}")
                    _events.emit(ChatEvent.ShowError("Indexing failed: ${body.error.message}"))
                } else {
                    val result = body.result
                    val message = result?.get("content") as? String ?: "Project files indexed successfully"
                    android.util.Log.i("ChatViewModel", "✅ Project files indexed for Team Assistant: $message")
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("ChatViewModel", "❌ HTTP Error: ${response.code()} - $errorBody")
                _events.emit(ChatEvent.ShowError("Indexing failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "❌ Error indexing project files for Team Assistant", e)
            _events.emit(ChatEvent.ShowError("Error indexing project files: ${e.message}"))
        }
    }
    
    private suspend fun executeTasksCommand(userMessage: Message) {
        try {
            android.util.Log.d("ChatViewModel", "🔍 Executing /tasks command...")
            
            val ollamaEnabled = _uiState.value.ollamaEnabled
            val projectTeamAssistantEnabled = _uiState.value.projectTeamAssistantEnabled
            val rerankingEnabled = _uiState.value.rerankingEnabled
            val githubMcpEnabled = _uiState.value.githubMcpEnabled
            val projectReviewModeEnabled = _uiState.value.projectReviewModeEnabled
            
            if (!ollamaEnabled || !projectTeamAssistantEnabled) {
                val errorMsg = "Project Team Assistant requires Ollama Vector Search to be enabled. Please enable both in Tools settings."
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
                _events.emit(ChatEvent.ShowError(errorMsg))
                return
            }
            
            val query = "Find problematic code areas related to: memory leaks, crashes, non-security values, clean architecture violations, non-thread-safe logic"
            
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/call",
                params = mapOf(
                    "name" to "search_project_files",
                    "arguments" to mapOf(
                        "query" to query,
                        "reranking_enabled" to rerankingEnabled
                    )
                )
            )
            
            val response = projectHelperMcpApi.sendRequest(request)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    android.util.Log.e("ChatViewModel", "❌ MCP Error: ${body.error.message}")
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = "Tasks generation failed: ${body.error.message}"
                        )
                    }
                    _events.emit(ChatEvent.ShowError("Tasks generation failed: ${body.error.message}"))
                    return
                }
                
                val result = body.result
                val contentArray = result?.get("content") as? List<*>
                val content = if (contentArray != null && contentArray.isNotEmpty()) {
                    val firstContent = contentArray.firstOrNull() as? Map<*, *>
                    firstContent?.get("text") as? String ?: firstContent?.get("content") as? String
                } else {
                    result?.get("content") as? String
                }
                
                if (content != null && content.isNotBlank()) {
                    val tasksPrompt = buildString {
                        appendLine("Based on the following problematic code areas from the project, generate exactly 3 technical tasks in the following format:")
                        appendLine()
                        appendLine("For each task, provide:")
                        appendLine("1. Название: (max 300 tokens) - Brief task title")
                        appendLine("2. Источник проблемы: (max 300 tokens) - Source of the problem (file path, line numbers)")
                        appendLine("3. Описание: (max 1000 tokens) - Detailed description of the problem")
                        appendLine("4. Ожидаемый результат: (max 500 tokens) - Expected outcome and rules to follow")
                        appendLine()
                        appendLine("Prioritize tasks as: 1 critical, 1 important, 1 normal")
                        appendLine()
                        appendLine("Rules to check:")
                        appendLine("- memory leaks")
                        appendLine("- crashes")
                        appendLine("- non security values")
                        appendLine("- clean architecture")
                        appendLine("- non thread safe logic")
                        appendLine()
                        appendLine("=== PROBLEMATIC CODE AREAS ===")
                        appendLine(content)
                        appendLine("=== END OF CODE AREAS ===")
                        appendLine()
                        appendLine("Generate exactly 3 tasks in the format above, one critical, one important, one normal.")
                    }
                    
                    val messagesWithContext = listOf(
                        ChatMessageDto(role = "user", content = tasksPrompt)
                    )
                    
                    sendMessageUseCase(
                        messages = messagesWithContext,
                        model = _uiState.value.selectedModel
                    )
                        .onSuccess { response ->
                            val aiMessage = Message(
                                content = response.content,
                                isUser = false
                            )
                            chatRepository.saveMessage(aiMessage)
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    error = null
                                )
                            }
                        }
                        .onFailure { error ->
                            android.util.Log.e("ChatViewModel", "Error generating tasks", error)
                            _uiState.update { state ->
                                state.copy(
                                    isLoading = false,
                                    error = error.message ?: "Unknown error occurred"
                                )
                            }
                            _events.emit(ChatEvent.ShowError(error.message ?: "Unknown error occurred"))
                        }
                } else {
                    val errorMsg = "No problematic code areas found. The project appears to be clean."
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = errorMsg
                        )
                    }
                    _events.emit(ChatEvent.ShowError(errorMsg))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                android.util.Log.e("ChatViewModel", "❌ HTTP Error: ${response.code()} - $errorBody")
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Tasks generation failed: HTTP ${response.code()}"
                    )
                }
                _events.emit(ChatEvent.ShowError("Tasks generation failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "❌ Error executing /tasks command", e)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
            _events.emit(ChatEvent.ShowError("Error executing /tasks command: ${e.message}"))
        }
    }
    
}

