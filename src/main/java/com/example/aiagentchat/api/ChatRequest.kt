package com.example.aiagentchat.api

data class ChatRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7
)

data class ChatMessage(
    val role: String,
    val content: String
)

