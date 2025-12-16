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

    override suspend fun sendMessage(model: AiModel, messages: List<ChatMessageDto>): Result<AiResponse> {
        return try {
            val apiKey = authManager.getApiKey(model)
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API key for ${model.displayName} is not configured"))
            }
            val request = ChatRequest(
                model = model.modelId,
                messages = messages,
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
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Network error: Unable to resolve host for ${model.displayName}", e)
            val hostName = e.message?.substringAfter("Unable to resolve host \"")?.substringBefore("\"") ?: "unknown host"
            Result.failure(
                Exception(
                    "Network error: Unable to connect to $hostName. " +
                            "Please check your internet connection and try again."
                )
            )
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Network timeout for ${model.displayName}", e)
            Result.failure(
                Exception(
                    "Connection timeout. Please check your internet connection and try again."
                )
            )
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network IO error for ${model.displayName}", e)
            Result.failure(
                Exception(
                    "Network error: ${e.message ?: "Unable to connect to the server"}. " +
                            "Please check your internet connection."
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to ${model.displayName}", e)
            Result.failure(e)
        }
    }

    override fun isModelConfigured(model: AiModel): Boolean {
        return authManager.isKeyConfigured(model)
    }
}

