package com.example.aiagentchat.data

/**
 * Модель сообщения в чате
 */
data class Message(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val rawResponse: String? = null,
    val parsedData: AiResponseData? = null
)
