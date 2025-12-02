package com.example.aiagentchat.core.network.api

import com.example.aiagentchat.core.network.dto.DeepSeekChatRequestDto
import com.example.aiagentchat.core.network.dto.DeepSeekChatResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepSeekApiService {
    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: DeepSeekChatRequestDto
    ): Response<DeepSeekChatResponseDto>
}

