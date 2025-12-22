package com.example.aiagentchat.feature.chat.data.api

import android.util.Log
import com.example.aiagentchat.core.network.ApiClient
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class OllamaEmbedRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("input")
    val input: String
)

data class OllamaEmbedResponse(
    @SerializedName("model")
    val model: String,
    @SerializedName("embeddings")
    val embeddings: List<List<Float>>,
    @SerializedName("total_duration")
    val totalDuration: Long? = null,
    @SerializedName("load_duration")
    val loadDuration: Long? = null,
    @SerializedName("prompt_eval_count")
    val promptEvalCount: Int? = null
)

interface OllamaApi {
    @POST("api/embed")
    suspend fun generateEmbedding(
        @Body request: OllamaEmbedRequest
    ): Response<OllamaEmbedResponse>
    
    @retrofit2.http.GET("api/tags")
    suspend fun getTags(): Response<OllamaTagsResponse>

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:11434/"
        const val DEFAULT_MODEL = "nomic-embed-text"
        private const val TAG = "OllamaApi"

        fun create(baseUrl: String = DEFAULT_BASE_URL): OllamaApi {
            Log.d(TAG, "Creating OllamaApi with baseUrl: $baseUrl")
            Log.d(TAG, "For Android emulator, use: http://10.0.2.2:11434/")
            Log.d(TAG, "For physical device, use your Mac's IP address")
            return ApiClient.createRetrofit(baseUrl).create(OllamaApi::class.java)
        }
    }
}

data class OllamaTagsResponse(
    @SerializedName("models")
    val models: List<OllamaModel>? = null
)

data class OllamaModel(
    @SerializedName("name")
    val name: String,
    @SerializedName("modified_at")
    val modifiedAt: String? = null,
    @SerializedName("size")
    val size: Long? = null
)

