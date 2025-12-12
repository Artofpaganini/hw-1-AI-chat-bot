package com.example.aiagentchat.feature.chat.data.api

import com.example.aiagentchat.core.network.ApiClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepSeekApi {
    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): Response<ChatResponse>

    companion object {
        const val BASE_URL = "https://api.deepseek.com/"

        fun create(): DeepSeekApi {
            return ApiClient.createRetrofit(BASE_URL).create(DeepSeekApi::class.java)
        }
    }
}

