package com.example.aiagentchat.feature.chat.data.api

import android.util.Log
import com.example.aiagentchat.core.network.ApiClient
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface VpsOllamaApi {
    @GET("api/tags")
    suspend fun getTags(): Response<OllamaTagsResponse>
    
    @POST("api/chat")
    suspend fun generateChat(
        @Body request: OllamaChatRequest
    ): Response<OllamaChatResponse>

    companion object {
        private const val TAG = "VpsOllamaApi"

        fun create(baseUrl: String): VpsOllamaApi {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            Log.d(TAG, "Creating VpsOllamaApi with baseUrl: $normalizedUrl")
            return ApiClient.createRetrofit(normalizedUrl).create(VpsOllamaApi::class.java)
        }
    }
}
