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
import com.example.aiagentchat.data.speech.SpeechRecognizerManager
import com.example.aiagentchat.data.speech.SpeechResult
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
    private val speechRecognizerManager: SpeechRecognizerManager
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

            val messagesWithContext = buildMessagesWithContext(currentInput, userProfile.personalizationPrompt)

            sendMessageUseCase(_uiState.value.selectedModel, messagesWithContext)
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
    
    override fun onCleared() {
        super.onCleared()
        handleStopVoiceInput()
        speechRecognizerManager.cancel()
    }
}

