package com.example.aiagentchat.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface QwenApiService {
    
    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: QwenChatRequest
    ): Response<QwenChatResponse>
}

data class QwenChatRequest(
    val model: String = "Qwen/Qwen2.5-VL-72B-Instruct",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048
)

data class QwenChatResponse(
    val id: String,
    val choices: List<QwenChoice>,
    val usage: Usage?
)

data class QwenChoice(
    val message: ChatMessage,
    val finish_reason: String?
)

