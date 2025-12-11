package com.example.aiagentchat.data.repository

import android.util.Log
import com.example.aiagentchat.data.AuthManager
import com.example.aiagentchat.data.api.ApiClient
import com.example.aiagentchat.data.api.ChatMessageDto
import com.example.aiagentchat.data.api.ChatRequest
import com.example.aiagentchat.data.api.OpenRouterApi
import com.example.aiagentchat.data.toon.ToonConverter
import com.example.aiagentchat.data.toon.ToonEncoder
import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.AiResponse
import com.example.aiagentchat.domain.repository.AiModelRepository

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
            
            // Логируем запрос в TOON формате
            logRequestAsToon(model, prompt, request)
            
            val response = when (model) {
                is AiModel.DeepSeek -> ApiClient.deepSeekApi.sendMessage(
                    authorization = "Bearer $apiKey",
                    request = request
                )
                is AiModel.Claude35Sonnet,
                is AiModel.Gpt4oMini,
                is AiModel.GeminiPro15 -> ApiClient.openRouterApi.sendMessage(
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
                
                // Логируем ответ в TOON формате
                logResponseAsToon(model, body, aiResponse)
                
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
    
    private fun logRequestAsToon(model: AiModel, prompt: String, request: ChatRequest) {
        val toonRequest = ToonEncoder.encode(
            mapOf(
                "model" to model.displayName,
                "modelId" to request.model,
                "temperature" to request.temperature,
                "maxTokens" to request.maxTokens,
                "prompt" to prompt
            )
        )
        Log.d(TAG, "=== REQUEST (TOON) ===\n$toonRequest")
    }
    
    private fun logResponseAsToon(
        model: AiModel,
        rawResponse: com.example.aiagentchat.data.api.ChatResponse,
        aiResponse: AiResponse
    ) {
        val toonResponse = ToonConverter.chatResponseToToon(rawResponse)
        Log.d(TAG, "=== RESPONSE (TOON) from ${model.displayName} ===\n$toonResponse")
        
        val toonAiResponse = ToonConverter.aiResponseToToon(aiResponse)
        Log.d(TAG, "=== PARSED RESPONSE (TOON) ===\n$toonAiResponse")
    }
}

