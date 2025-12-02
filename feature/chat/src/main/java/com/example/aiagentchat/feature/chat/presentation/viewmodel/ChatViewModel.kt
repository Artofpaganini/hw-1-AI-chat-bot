package com.example.aiagentchat.feature.chat.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiagentchat.core.common.Result
import com.example.aiagentchat.core.domain.usecase.ClearHistoryUseCase
import com.example.aiagentchat.core.domain.usecase.GetApiKeyUseCase
import com.example.aiagentchat.core.domain.usecase.GetMessagesUseCase
import com.example.aiagentchat.core.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.core.domain.usecase.SetApiKeyUseCase
import com.example.aiagentchat.feature.chat.presentation.event.ChatEvent
import com.example.aiagentchat.feature.chat.presentation.state.ChatState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val clearHistoryUseCase: ClearHistoryUseCase,
    private val setApiKeyUseCase: SetApiKeyUseCase,
    private val getApiKeyUseCase: GetApiKeyUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        loadApiKey()
        observeMessages()
        setApiKey("sk-d06b698223034c60a9cdb3d7bc8fab15")
    }

    fun handleEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.SendMessage -> sendMessage(event.text)
            is ChatEvent.UpdateInputText -> updateInputText(event.text)
            is ChatEvent.SetApiKey -> setApiKey(event.apiKey)
            is ChatEvent.ClearHistory -> clearHistory()
            is ChatEvent.DismissError -> dismissError()
        }
    }

    private fun loadApiKey() {
        getApiKeyUseCase()
            .onEach { apiKey ->
                _state.value = _state.value.copy(apiKey = apiKey)
            }
            .launchIn(viewModelScope)
    }

    private fun observeMessages() {
        getMessagesUseCase()
            .onEach { messages ->
                _state.value = _state.value.copy(messages = messages)
            }
            .launchIn(viewModelScope)
    }

    private fun sendMessage(text: String) {
        if (text.isBlank()) {
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                inputText = ""
            )
            sendMessageUseCase(text)
                .onEach { result ->
                    when (result) {
                        is Result.Loading -> {
                            _state.value = _state.value.copy(isLoading = true)
                        }
                        is Result.Success -> {
                            _state.value = _state.value.copy(isLoading = false)
                        }
                        is Result.Error -> {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = result.exception.message ?: "Unknown error"
                            )
                        }
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun updateInputText(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    private fun setApiKey(apiKey: String) {
        viewModelScope.launch {
            setApiKeyUseCase(apiKey)
            _state.value = _state.value.copy(apiKey = apiKey)
        }
    }

    private fun clearHistory() {
        clearHistoryUseCase()
    }

    private fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}

