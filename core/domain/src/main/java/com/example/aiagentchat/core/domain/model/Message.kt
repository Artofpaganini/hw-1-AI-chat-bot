package com.example.aiagentchat.core.domain.model

data class Message(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val rawResponse: String? = null,
    val parsedData: ParsedAiResponse? = null
)

