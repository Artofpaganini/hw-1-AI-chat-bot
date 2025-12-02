package com.example.aiagentchat.feature.chat.presentation.state

import com.example.aiagentchat.core.domain.model.Message

data class ChatState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val apiKey: String? = null
)

