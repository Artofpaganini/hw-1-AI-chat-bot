package com.example.aiagentchat.feature.chat.data.repository

import android.util.Log
import com.example.aiagentchat.feature.chat.data.AuthManager
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.api.ChatRequest
import com.example.aiagentchat.feature.chat.data.api.DeepSeekApi
import com.example.aiagentchat.feature.chat.data.api.OpenRouterApi
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.AiResponse
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository

class AiModelRepositoryImpl(
    private val authManager: AuthManager
) : AiModelRepository {

    companion object {
        private const val TAG = "AiModelRepository"
    }

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
                ),
            )

            val response = when (model) {
                is AiModel.DeepSeek -> DeepSeekApi.create().sendMessage(
                    authorization = "Bearer $apiKey",
                    request = request
                )
                is AiModel.Claude35Sonnet,
                is AiModel.Gpt4oMini,
                is AiModel.GeminiPro15 -> OpenRouterApi.create().sendMessage(
                    authorization = "Bearer $apiKey",
                    httpReferer = OpenRouterApi.APP_URL,
                    xTitle = OpenRouterApi.APP_NAME,
                    request = request
                )
            }
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val content = body.choices.firstOrNull()?.message?.content
                    ?: return Result.failure(Exception("Empty response from ${model.displayName}"))

                val aiResponse = AiResponse(
                    content = content,
                    inputTokens = body.usage?.promptTokens ?: 0,
                    outputTokens = body.usage?.completionTokens ?: 0
                )

                Result.success(aiResponse)
            } else {
                Result.failure(Exception("API Error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to ${model.displayName}", e)
            Result.failure(e)
        }
    }

    override fun isModelConfigured(model: AiModel): Boolean {
        return authManager.isKeyConfigured(model)
    }
}

