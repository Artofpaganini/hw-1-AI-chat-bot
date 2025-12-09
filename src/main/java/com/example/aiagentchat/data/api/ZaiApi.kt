package com.example.aiagentchat.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ZaiApi {
    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): Response<ChatResponse>
    
    companion object {
        // Z.ai API endpoint - замените на актуальный URL
        const val BASE_URL = "https://openrouter.ai/api/"
    }
}

