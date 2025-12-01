package com.example.aiagentchat.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface HuggingFaceApiService {
    
    @POST("api/models/microsoft/DialoGPT-medium")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String?,
        @Body request: HuggingFaceRequest
    ): Response<HuggingFaceResponse>
}

data class HuggingFaceRequest(
    val inputs: HuggingFaceInputs
)

data class HuggingFaceInputs(
    val past_user_inputs: List<String> = emptyList(),
    val generated_responses: List<String> = emptyList(),
    val text: String
)

data class HuggingFaceResponse(
    val generated_text: String
)

