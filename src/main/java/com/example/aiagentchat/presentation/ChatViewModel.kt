package com.example.aiagentchat.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.Message
import com.example.aiagentchat.domain.repository.AiModelRepository
import com.example.aiagentchat.domain.usecase.CompareModelMetricsUseCase
import com.example.aiagentchat.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.domain.usecase.SwitchAiModelUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val switchAiModelUseCase: SwitchAiModelUseCase,
    private val compareModelMetricsUseCase: CompareModelMetricsUseCase,
    private val aiModelRepository: AiModelRepository
) : ViewModel() {
    
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
        Log.w("EWQ", "sendMessage: ${_state.value.selectedModel}", )
        viewModelScope.launch {
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
}

