package com.example.aiagentchat.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApi {
    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("HTTP-Referer") httpReferer: String? = null,
        @Header("X-Title") xTitle: String? = null,
        @Body request: ChatRequest
    ): Response<ChatResponse>
    
    companion object {
        const val BASE_URL = "https://openrouter.ai/api/"
        const val APP_NAME = "AI Agent Chat"
        const val APP_URL = "https://github.com/yourusername/ai-agent-chat"
    }
}
