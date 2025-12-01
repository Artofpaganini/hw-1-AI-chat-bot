package com.example.aiagentchat.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GroqApiService {
    
    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GroqChatRequest
    ): Response<GroqChatResponse>
}

data class GroqChatRequest(
    val model: String = "llama-3.1-8b-instant",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1024
)

data class GroqChatResponse(
    val id: String,
    val choices: List<GroqChoice>,
    val usage: Usage?
)

data class GroqChoice(
    val message: ChatMessage,
    val finish_reason: String?
)

