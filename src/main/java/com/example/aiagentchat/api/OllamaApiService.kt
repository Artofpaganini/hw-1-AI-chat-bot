package com.example.aiagentchat.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApiService {
    
    @POST("api/generate")
    suspend fun sendMessage(
        @Body request: OllamaRequest
    ): Response<OllamaResponse>
}

data class OllamaRequest(
    val model: String = "llama2",
    val prompt: String,
    val stream: Boolean = false
)

data class OllamaResponse(
    val response: String
)

