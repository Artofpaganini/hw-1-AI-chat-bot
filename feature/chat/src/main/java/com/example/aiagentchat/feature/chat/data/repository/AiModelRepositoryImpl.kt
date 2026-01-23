package com.example.aiagentchat.feature.chat.data.repository

import android.util.Log
import com.example.aiagentchat.core.network.ApiClient
import com.example.aiagentchat.feature.chat.data.AuthManager
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.api.ChatRequest
import com.example.aiagentchat.feature.chat.data.api.DeepSeekApi
import com.example.aiagentchat.feature.chat.data.api.OpenRouterApi
import com.example.aiagentchat.feature.chat.data.api.OllamaMcpApi
import com.example.aiagentchat.feature.chat.data.api.JsonRpcRequest
import com.example.aiagentchat.feature.chat.data.api.JsonRpcResponse
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.AiResponse
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.google.gson.Gson
import com.google.gson.JsonObject

class AiModelRepositoryImpl(
    private val authManager: AuthManager
) : AiModelRepository {
    
    private val ollamaMcpApi: OllamaMcpApi by lazy {
        val baseUrl = "http://10.0.2.2:8086/"
        val retrofit = ApiClient.createRetrofit(baseUrl)
        retrofit.create(OllamaMcpApi::class.java)
    }
    
    private val gson = Gson()
    private var requestId = 1

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
                is AiModel.OllamaLlama32b -> {
                    val userMessage = messages.lastOrNull()?.content ?: ""
                    Log.d(TAG, "📤 Sending request to ${model.displayName}: ${userMessage.take(100)}...")
                    
                    // Для модели OllamaLlama32b всегда используем контекст проекта
                    // Эта модель используется только для Project Analytic, где контекст проекта обязателен
                    val isProjectQuery = true
                    
                    Log.d(TAG, "🔍 Using project context for OllamaLlama32b: $isProjectQuery")
                    
                    val mcpRequest = JsonRpcRequest(
                        id = requestId++,
                        method = "tools/call",
                        params = mapOf(
                            "name" to "chat_with_llama",
                            "arguments" to mapOf(
                                "query" to userMessage,
                                "use_project_context" to isProjectQuery
                            )
                        )
                    )
                    
                    Log.d(TAG, "📡 Sending MCP request with use_project_context=$isProjectQuery")
                    Log.d(TAG, "📡 Full MCP request: ${gson.toJson(mcpRequest)}")
                    val mcpResponse = ollamaMcpApi.sendRequest(mcpRequest)
                    
                    if (mcpResponse.isSuccessful && mcpResponse.body() != null) {
                        val body = mcpResponse.body()!!
                        
                        if (body.error != null) {
                            val errorMsg = "MCP Error: ${body.error.message}"
                            Log.e(TAG, "❌ $errorMsg")
                            return Result.failure(Exception(errorMsg))
                        }
                        
                        val result = body.result
                        if (result == null) {
                            Log.e(TAG, "❌ MCP response has no result")
                            return Result.failure(Exception("MCP response has no result"))
                        }
                        
                        // Парсим ответ в формате MCP
                        val contentArray = result["content"] as? List<*>
                        val contentObj = contentArray?.firstOrNull() as? Map<*, *>
                        val content = contentObj?.get("text") as? String ?: ""
                        
                        if (content.isBlank()) {
                            Log.e(TAG, "❌ Empty response from ${model.displayName}")
                            return Result.failure(Exception("Empty response from ${model.displayName}"))
                        }
                        
                        Log.d(TAG, "✅ Response from ${model.displayName}: contentLength=${content.length}")
                        
                        val aiResponse = AiResponse(
                            content = content,
                            inputTokens = 0,
                            outputTokens = 0,
                            toolCalls = null,
                            finishReason = "stop"
                        )
                        
                        return Result.success(aiResponse)
                    } else {
                        val errorBody = mcpResponse.errorBody()?.string()
                        val errorMsg = "MCP Error ${mcpResponse.code()}: ${mcpResponse.message()}"
                        Log.e(TAG, "❌ $errorMsg, body: ${errorBody?.take(500)}")
                        return Result.failure(Exception("$errorMsg${if (errorBody != null) "\n$errorBody" else ""}"))
                    }
                }
                is AiModel.DeepSeek -> {
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
                    
                    val response = DeepSeekApi.create().sendMessage(
                        authorization = "Bearer $apiKey",
                        request = request
                    )
                    
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

                        return Result.success(aiResponse)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "API Error ${response.code()}: ${response.message()}, body: $errorBody")
                        return Result.failure(Exception("API Error ${response.code()}: ${response.message()}"))
                    }
                }
                is AiModel.Claude35Sonnet,
                is AiModel.Gpt4oMini,
                is AiModel.GeminiPro15 -> {
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
                    
                    val response = OpenRouterApi.create().sendMessage(
                        authorization = "Bearer $apiKey",
                        httpReferer = OpenRouterApi.APP_URL,
                        xTitle = OpenRouterApi.APP_NAME,
                        request = request
                    )
                    
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

                        return Result.success(aiResponse)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "API Error ${response.code()}: ${response.message()}, body: $errorBody")
                        return Result.failure(Exception("API Error ${response.code()}: ${response.message()}"))
                    }
                }
            }
            
            // This should never be reached as all branches return
            Result.failure(Exception("Unsupported model: $model"))
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

