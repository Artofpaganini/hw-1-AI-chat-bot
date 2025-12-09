package com.example.aiagentchat.data.repository

import android.util.Log
import com.example.aiagentchat.data.AuthManager
import com.example.aiagentchat.data.api.ApiClient
import com.example.aiagentchat.data.api.ChatMessageDto
import com.example.aiagentchat.data.api.ChatRequest
import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.AiResponse
import com.example.aiagentchat.domain.repository.AiModelRepository

class AiModelRepositoryImpl(
    private val authManager: AuthManager
) : AiModelRepository {
    
    override suspend fun sendMessage(model: AiModel, prompt: String): Result<AiResponse> {
        return try {
            val apiKey = authManager.getApiKey(model)
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API key for ${model.displayName} is not configured"))
            }
            
            val request = ChatRequest(
                model = model.modelId,
                messages = listOf(
                    ChatMessageDto(role = "user", content = prompt)
                )
            )
            Log.w("EWQ", "sendMessage: $model", )
            val response = when (model) {
                is AiModel.DeepSeek -> ApiClient.deepSeekApi.sendMessage(
                    authorization = "Bearer $apiKey",
                    request = request
                )
                is AiModel.Zai -> ApiClient.zaiApi.sendMessage(
                    authorization = "Bearer $apiKey",
                    request = request
                )
            }
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val content = body.choices.firstOrNull()?.message?.content
                    ?: return Result.failure(Exception("Empty response from ${model.displayName}"))
                
                Result.success(
                    AiResponse(
                        content = content,
                        inputTokens = body.usage?.promptTokens ?: 0,
                        outputTokens = body.usage?.completionTokens ?: 0
                    )
                )
            } else {
                Result.failure(Exception("API Error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun isModelConfigured(model: AiModel): Boolean {
        return authManager.isKeyConfigured(model)
    }
}

