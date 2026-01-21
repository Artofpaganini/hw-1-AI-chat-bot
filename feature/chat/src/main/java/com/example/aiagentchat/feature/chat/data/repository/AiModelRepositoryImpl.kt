package com.example.aiagentchat.feature.chat.data.repository

import android.util.Log
import com.example.aiagentchat.core.common.preferences.PreferencesManager
import com.example.aiagentchat.feature.chat.data.AuthManager
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.api.ChatRequest
import com.example.aiagentchat.feature.chat.data.api.DeepSeekApi
import com.example.aiagentchat.feature.chat.data.api.OpenRouterApi
import com.example.aiagentchat.feature.chat.data.api.VpsOllamaApi
import com.example.aiagentchat.feature.chat.data.api.OllamaChatMessage
import com.example.aiagentchat.feature.chat.data.api.OllamaChatRequest
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.AiResponse
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository

class AiModelRepositoryImpl(
    private val authManager: AuthManager,
    private val preferencesManager: PreferencesManager
) : AiModelRepository {

    companion object {
        private const val TAG = "AiModelRepository"
    }

    override suspend fun sendMessage(
        model: AiModel,
        messages: List<ChatMessageDto>,
        tools: List<com.example.aiagentchat.feature.chat.data.api.ToolDto>?
    ): Result<AiResponse> {
        return try {
            when (model) {
                is AiModel.VpsOllama -> {
                    val vpsUrl = preferencesManager.vpsOllamaUrl
                    if (vpsUrl.isBlank()) {
                        return Result.failure(Exception("VPS Ollama URL for ${model.displayName} is not configured"))
                    }
                    Log.d(TAG, "Sending request to VPS Ollama at: $vpsUrl")
                    val vpsApi = VpsOllamaApi.create(vpsUrl)
                    val ollamaMessages = messages.map { msg ->
                        OllamaChatMessage(
                            role = if (msg.role == "user") "user" else "assistant",
                            content = msg.content ?: ""
                        )
                    }
                    val ollamaRequest = OllamaChatRequest(
                        model = model.modelId,
                        messages = ollamaMessages,
                        stream = false
                    )
                    val response = vpsApi.generateChat(ollamaRequest)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val content = body.message?.content ?: ""
                        Log.d(TAG, "Response from VPS Ollama: contentLength=${content.length}")
                        val aiResponse = AiResponse(
                            content = content,
                            inputTokens = body.promptEvalCount ?: 0,
                            outputTokens = body.evalCount ?: 0,
                            toolCalls = null,
                            finishReason = null
                        )
                        Result.success(aiResponse)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "VPS Ollama API Error ${response.code()}: ${response.message()}, body: $errorBody")
                        Result.failure(Exception("VPS Ollama API Error ${response.code()}: ${response.message()}"))
                    }
                }
                else -> {
                    val apiKey = authManager.getApiKey(model)
                    if (apiKey.isBlank()) {
                        return Result.failure(Exception("API key for ${model.displayName} is not configured"))
                    }
                    val request = ChatRequest(
                        model = model.modelId,
                        messages = messages,
                        tools = tools
                    )
                    
                    if (tools != null && tools.isNotEmpty()) {
                        Log.d(TAG, "Sending request to ${model.displayName} with ${tools.size} tools: ${tools.map { it.function.name }}")
                    } else {
                        Log.d(TAG, "Sending request to ${model.displayName} without tools")
                    }

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
                        else -> return Result.failure(Exception("Unsupported model: ${model.displayName}"))
                    }
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val choice = body.choices.firstOrNull()
                            ?: return Result.failure(Exception("Empty response from ${model.displayName}"))
                        
                        val message = choice.message
                        val content = message.content ?: ""
                        val toolCalls = message.toolCalls
                        val finishReason = choice.finishReason
                        
                        Log.d(TAG, "Response from ${model.displayName}: finishReason=$finishReason, toolCalls=${toolCalls?.size ?: 0}, contentLength=${content.length}")

                        val aiResponse = AiResponse(
                            content = content,
                            inputTokens = body.usage?.promptTokens ?: 0,
                            outputTokens = body.usage?.completionTokens ?: 0,
                            toolCalls = toolCalls,
                            finishReason = finishReason
                        )

                        Result.success(aiResponse)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "API Error ${response.code()}: ${response.message()}, body: $errorBody")
                        Result.failure(Exception("API Error ${response.code()}: ${response.message()}"))
                    }
                }
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

