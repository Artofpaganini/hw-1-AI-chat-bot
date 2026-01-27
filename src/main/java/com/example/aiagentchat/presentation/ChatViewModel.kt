package com.example.aiagentchat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagentchat.data.speech.SpeechRecognizerManager
import com.example.aiagentchat.data.speech.SpeechResult
import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.Message
import com.example.aiagentchat.domain.repository.AiModelRepository
import com.example.aiagentchat.domain.usecase.CompareModelMetricsUseCase
import com.example.aiagentchat.domain.usecase.ExportChatHistoryUseCase
import com.example.aiagentchat.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.domain.usecase.SwitchAiModelUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val switchAiModelUseCase: SwitchAiModelUseCase,
    private val compareModelMetricsUseCase: CompareModelMetricsUseCase,
    private val exportChatHistoryUseCase: ExportChatHistoryUseCase,
    private val aiModelRepository: AiModelRepository,
    private val speechRecognizerManager: SpeechRecognizerManager
) : ViewModel() {
    
    private var speechRecognitionJob: Job? = null
    
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    
    init {
        updateConfiguredModels()
    }
    
    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.OnInputChange -> updateInput(event.input)
            is ChatEvent.OnSendMessage -> sendMessage()
            is ChatEvent.OnModelSelected -> selectModel(event.model)
            is ChatEvent.OnDismissError -> dismissError()
            is ChatEvent.OnClearChat -> clearChat()
            is ChatEvent.OnExportChat -> exportChat()
            is ChatEvent.OnDismissExport -> dismissExport()
            is ChatEvent.OnStartVoiceInput -> startVoiceInput()
            is ChatEvent.OnStopVoiceInput -> stopVoiceInput()
            is ChatEvent.OnDismissSpeechError -> dismissSpeechError()
        }
    }
    
    private fun updateConfiguredModels() {
        val configured = AiModel.entries.filter { aiModelRepository.isModelConfigured(it) }.toSet()
        _state.update { it.copy(configuredModels = configured) }
    }
    
    private fun updateInput(input: String) {
        _state.update { it.copy(currentInput = input) }
    }
    
    private fun sendMessage() {
        val currentInput = _state.value.currentInput.trim()
        if (currentInput.isBlank()) return
        
        val currentMessages = _state.value.messages
        val userMessage = Message(
            content = currentInput,
            isUser = true
        )
        
        _state.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                currentInput = "",
                isLoading = true,
                error = null
            )
        }
        viewModelScope.launch {
            when {
                currentInput.equals("Сжатие", ignoreCase = true) -> {
                    compressChatHistory(currentMessages)
                }
                currentInput.equals("Аналитика", ignoreCase = true) -> {
                    val prompt = buildAnalyticsPrompt(currentMessages)
                    sendMessageUseCase(_state.value.selectedModel, prompt)
                        .onSuccess { aiMessage ->
                            _state.update { state ->
                                val updatedMessages = state.messages + aiMessage
                                val comparison = compareModelMetricsUseCase(updatedMessages, currentInput)
                                state.copy(
                                    messages = updatedMessages,
                                    isLoading = false,
                                    metricsComparison = comparison
                                )
                            }
                        }
                        .onFailure { error ->
                            _state.update { state ->
                                state.copy(
                                    isLoading = false,
                                    error = error.message ?: "Unknown error occurred"
                                )
                            }
                        }
                }
                else -> {
                    sendMessageUseCase(_state.value.selectedModel, currentInput)
                        .onSuccess { aiMessage ->
                            _state.update { state ->
                                val updatedMessages = state.messages + aiMessage
                                val comparison = compareModelMetricsUseCase(updatedMessages, currentInput)
                                state.copy(
                                    messages = updatedMessages,
                                    isLoading = false,
                                    metricsComparison = comparison
                                )
                            }
                        }
                        .onFailure { error ->
                            _state.update { state ->
                                state.copy(
                                    isLoading = false,
                                    error = error.message ?: "Unknown error occurred"
                                )
                            }
                        }
                }
            }
        }
    }
    
    private suspend fun compressChatHistory(messages: List<Message>) {
        val messagesToCompress = messages.filter { !it.isCompressed }
        if (messagesToCompress.isEmpty()) {
            _state.update { state ->
                state.copy(
                    isLoading = false,
                    error = "Нет сообщений для сжатия"
                )
            }
            return
        }
        val chunks = messagesToCompress.chunked(10)
        val fullChunks = chunks.filter { it.size == 10 }
        if (fullChunks.isEmpty()) {
            _state.update { state ->
                state.copy(
                    isLoading = false,
                    error = "Недостаточно сообщений для сжатия. Нужно минимум 10 сообщений."
                )
            }
            return
        }
        val compressedMessages = mutableListOf<Message>()
        for (chunk in fullChunks) {
            val compressionPrompt = buildCompressionPrompt(chunk)
            sendMessageUseCase(_state.value.selectedModel, compressionPrompt)
                .onSuccess { summaryMessage ->
                    val compressedMessage = summaryMessage.copy(isCompressed = true)
                    compressedMessages.add(compressedMessage)
                }
                .onFailure { error ->
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            error = "Ошибка при сжатии: ${error.message}"
                        )
                    }
                    return
                }
        }
        _state.update { state ->
            val existingMessages = state.messages
            val newMessages = existingMessages + compressedMessages
            state.copy(
                messages = newMessages,
                isLoading = false,
                error = null
            )
        }
    }
    
    private fun buildCompressionPrompt(messages: List<Message>): String {
        val prompt = buildString {
            appendLine("Ты помощник для сжатия истории диалога. Дай краткое описание(не больше 1 предложения) по каждому ответу из истории, сохраняя ключевые моменты и контекст.")
            appendLine()
            appendLine("Диалог:")
            messages.forEach { message ->
                val role = if (message.isUser) "Пользователь" else "AI (${message.model?.displayName ?: "Неизвестно"})"
                appendLine("$role: ${message.content}")
            }
            appendLine()
            appendLine("Создай краткое резюме этого диалога, сохраняя важную информацию и контекст.")
        }
        return prompt.toString()
    }
    
    private fun buildAnalyticsPrompt(messages: List<Message>): String {
        val compressionMessageIndex = messages.indexOfFirst { 
            it.isUser && it.content.equals("Сжатие", ignoreCase = true) 
        }
        if (compressionMessageIndex == -1) {
            return "Не найдено сообщение 'Сжатие' в истории. Аналитика доступна только после выполнения сжатия."
        }
        val messagesBeforeCompression = messages.take(compressionMessageIndex)
        val messagesAfterCompression = messages.drop(compressionMessageIndex + 1)
        val aiResponsesBefore = messagesBeforeCompression.filter { !it.isUser && !it.isCompressed }
        val aiResponsesAfter = messagesAfterCompression.filter { !it.isUser && !it.isCompressed }
        if (aiResponsesBefore.isEmpty() && aiResponsesAfter.isEmpty()) {
            return "Недостаточно ответов для аналитики. Нужны ответы до и после сжатия."
        }
        val prompt = buildString {
            appendLine("Ты Аналитик уровня - мастер. Проанализируй ответы AI до и после сжатия истории диалога.")
            appendLine()
            appendLine("В истории диалога было выполнено сжатие. Проанализируй:")
            appendLine("1. Результативность ответов ДО сжатия")
            appendLine("2. Результативность ответов ПОСЛЕ сжатия")
            appendLine("3. Отличия между ответами до и после сжатия")
            appendLine()
            if (aiResponsesBefore.isNotEmpty()) {
                appendLine("=== ОТВЕТЫ ДО СЖАТИЯ ===")
                aiResponsesBefore.forEachIndexed { index, message ->
                    appendLine("--- Ответ ${index + 1} ---")
                    appendLine("Модель: ${message.model?.displayName ?: "Неизвестно"}")
                    if (message.metrics != null) {
                        appendLine("Метрики:")
                        appendLine("  - Входные токены: ${message.metrics.inputTokens}")
                        appendLine("  - Выходные токены: ${message.metrics.outputTokens}")
                        appendLine("  - Всего токенов: ${message.metrics.inputTokens + message.metrics.outputTokens}")
                        appendLine("  - Стоимость: $${String.format("%.6f", message.metrics.costUsd)}")
                        appendLine("  - Время ответа: ${message.metrics.responseTimeMs}ms")
                    }
                    appendLine("Содержание:")
                    appendLine(message.content)
                    appendLine()
                }
            }
            if (aiResponsesAfter.isNotEmpty()) {
                appendLine("=== ОТВЕТЫ ПОСЛЕ СЖАТИЯ ===")
                aiResponsesAfter.forEachIndexed { index, message ->
                    appendLine("--- Ответ ${index + 1} ---")
                    appendLine("Модель: ${message.model?.displayName ?: "Неизвестно"}")
                    if (message.metrics != null) {
                        appendLine("Метрики:")
                        appendLine("  - Входные токены: ${message.metrics.inputTokens}")
                        appendLine("  - Выходные токены: ${message.metrics.outputTokens}")
                        appendLine("  - Всего токенов: ${message.metrics.inputTokens + message.metrics.outputTokens}")
                        appendLine("  - Стоимость: $${String.format("%.6f", message.metrics.costUsd)}")
                        appendLine("  - Время ответа: ${message.metrics.responseTimeMs}ms")
                    }
                    appendLine("Содержание:")
                    appendLine(message.content)
                    appendLine()
                }
            }
            appendLine("=== ЗАДАНИЕ ДЛЯ АНАЛИТИКИ ===")
            appendLine("Дай детальную аналитику:")
            appendLine()
            appendLine("1. РЕЗУЛЬТАТИВНОСТЬ ОТВЕТОВ ДО СЖАТИЯ:")
            appendLine("   - Оцени результативность каждого ответа (шкала 1-10)")
            appendLine("   - Общее количество input/ouput токенов и стоимость")
            appendLine("   - Качество ответов (детальность, точность, полезность)")
            appendLine()
            appendLine("2. РЕЗУЛЬТАТИВНОСТЬ ОТВЕТОВ ПОСЛЕ СЖАТИЯ:")
            appendLine("   - Оцени результативность каждого ответа (шкала 1-10)")
            appendLine("   - Общее количество input/ouput токенов и стоимость")
            appendLine("   - Качество ответов (детальность, точность, полезность)")
            appendLine()
            appendLine("3. ОТЛИЧИЯ МЕЖДУ ОТВЕТАМИ ДО И ПОСЛЕ СЖАТИЯ:")
            appendLine("   - Как изменилось качество ответов?")
            appendLine("   - Как изменилось использование токенов?")
            appendLine("   - Как изменилась скорость ответов?")
            appendLine("   - Как изменилась стоимость?")
            appendLine("   - Влияние сжатия на контекст и понимание")
            appendLine("   - Общие выводы и рекомендации")
        }
        return prompt.toString()
    }
    
    private fun selectModel(model: AiModel) {
        switchAiModelUseCase(model)
            .onSuccess { selectedModel ->
                _state.update { it.copy(selectedModel = selectedModel, error = null) }
            }
            .onFailure { error ->
                _state.update { it.copy(error = error.message) }
            }
    }
    
    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }
    
    private fun clearChat() {
        _state.update { it.copy(messages = emptyList(), metricsComparison = null) }
    }
    
    private fun exportChat() {
        val currentState = _state.value
        if (currentState.messages.isEmpty()) return
        
        val toonExport = exportChatHistoryUseCase(
            messages = currentState.messages,
            comparison = currentState.metricsComparison
        )
        
        _state.update { it.copy(exportedToon = toonExport) }
    }
    
    private fun dismissExport() {
        _state.update { it.copy(exportedToon = null) }
    }
    
    private fun startVoiceInput() {
        if (_state.value.isListening) {
            return
        }
        if (!speechRecognizerManager.isAvailable()) {
            _state.update { it.copy(speechError = "Speech recognition is not available") }
            return
        }
        _state.update { 
            it.copy(
                isListening = true,
                speechError = null
            )
        }
        speechRecognitionJob = speechRecognizerManager.startListening()
            .onEach { result ->
                when (result) {
                    is SpeechResult.Listening -> {
                        _state.update { it.copy(isListening = true) }
                    }
                    is SpeechResult.Speaking -> {
                    }
                    is SpeechResult.AudioLevel -> {
                    }
                    is SpeechResult.Processing -> {
                    }
                    is SpeechResult.PartialResult -> {
                        _state.update { it.copy(currentInput = result.text) }
                    }
                    is SpeechResult.Success -> {
                        _state.update { 
                            it.copy(
                                currentInput = result.text,
                                isListening = false
                            )
                        }
                        sendMessageFromVoice(result.text)
                    }
                    is SpeechResult.Error -> {
                        _state.update { 
                            it.copy(
                                isListening = false,
                                speechError = result.message
                            )
                        }
                    }
                }
            }
            .catch { error ->
                _state.update { 
                    it.copy(
                        isListening = false,
                        speechError = error.message ?: "Unknown error occurred"
                    )
                }
            }
            .launchIn(viewModelScope)
    }
    
    private fun stopVoiceInput() {
        speechRecognizerManager.stopListening()
        speechRecognitionJob?.cancel()
        speechRecognitionJob = null
        _state.update { it.copy(isListening = false) }
    }
    
    private fun dismissSpeechError() {
        _state.update { it.copy(speechError = null) }
    }
    
    private fun sendMessageFromVoice(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return
        val currentMessages = _state.value.messages
        val userMessage = Message(
            content = trimmedText,
            isUser = true
        )
        _state.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                currentInput = "",
                isLoading = true,
                error = null
            )
        }
        viewModelScope.launch {
            sendMessageUseCase(_state.value.selectedModel, trimmedText)
                .onSuccess { aiMessage ->
                    _state.update { state ->
                        val updatedMessages = state.messages + aiMessage
                        val comparison = compareModelMetricsUseCase(updatedMessages, trimmedText)
                        state.copy(
                            messages = updatedMessages,
                            isLoading = false,
                            metricsComparison = comparison
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error occurred"
                        )
                    }
                }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopVoiceInput()
        speechRecognizerManager.cancel()
    }
}

