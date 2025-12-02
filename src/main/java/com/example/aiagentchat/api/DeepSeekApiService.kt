package com.example.aiagentchat.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepSeekApiService {

    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: DeepSeekChatRequest
    ): Response<DeepSeekChatResponse>
}

data class DeepSeekChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048
)

data class DeepSeekChatResponse(
    val id: String,
    val choices: List<DeepSeekChoice>,
    val usage: Usage?
)

data class DeepSeekChoice(
    val message: ChatMessage,
    val finish_reason: String?
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)


