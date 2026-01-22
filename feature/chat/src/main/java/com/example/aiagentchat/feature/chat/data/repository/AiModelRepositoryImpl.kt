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
                    val vpsUrl = preferencesManager.vpsOllamaUrl.trim()
                    if (vpsUrl.isBlank()) {
                        return Result.failure(Exception("VPS Ollama URL for ${model.displayName} is not configured"))
                    }
                    val normalizedUrl = if (vpsUrl.endsWith("/")) vpsUrl else "$vpsUrl/"
                    Log.d(TAG, "Sending request to VPS Ollama")
                    Log.d(TAG, "  Base URL: $normalizedUrl")
                    Log.d(TAG, "  Full request URL: ${normalizedUrl}api/chat")
                    val vpsApi = VpsOllamaApi.create(vpsUrl)
                    val temperature = preferencesManager.vpsOllamaTemperature.toDouble()
                    var numCtx = preferencesManager.vpsOllamaNumCtx
                    var numPredict = preferencesManager.vpsOllamaNumPredict
                    val useAndroidPrompt = preferencesManager.vpsOllamaUseAndroidPrompt
                    val ollamaMessages = messages.map { msg ->
                        OllamaChatMessage(
                            role = if (msg.role == "user") "user" else if (msg.role == "system") "system" else "assistant",
                            content = msg.content ?: ""
                        )
                    }
                    val systemPrompt = if (useAndroidPrompt) {
                        generateAndroidKotlinComposePrompt()
                    } else {
                        null
                    }
                    if (numCtx > 8192) {
                        Log.w(TAG, "numCtx ($numCtx) is too large for llama3.2:3b, limiting to 8192")
                        numCtx = 8192
                    }
                    if (numPredict > 4096) {
                        Log.w(TAG, "numPredict ($numPredict) is too large, limiting to 4096")
                        numPredict = 4096
                    }
                    val ollamaRequest = OllamaChatRequest(
                        model = model.modelId,
                        messages = ollamaMessages,
                        stream = false,
                        temperature = if (temperature > 0 && temperature <= 2.0) temperature else null,
                        numCtx = if (numCtx > 0 && numCtx <= 8192) numCtx else null,
                        numPredict = if (numPredict > 0 && numPredict <= 4096) numPredict else null,
                        system = systemPrompt
                    )
                    Log.d(TAG, "VPS Ollama request params:")
                    Log.d(TAG, "  - model: ${model.modelId}")
                    Log.d(TAG, "  - temperature: $temperature")
                    Log.d(TAG, "  - numCtx: $numCtx")
                    Log.d(TAG, "  - numPredict: $numPredict")
                    Log.d(TAG, "  - useAndroidPrompt: $useAndroidPrompt")
                    Log.d(TAG, "  - systemPrompt length: ${systemPrompt?.length ?: 0}")
                    Log.d(TAG, "  - messages count: ${ollamaMessages.size}")
                    val messagesPreview = ollamaMessages.take(3).joinToString("\n") { "${it.role}: ${it.content.take(50)}..." }
                    Log.d(TAG, "  - messages preview:\n$messagesPreview")
                    val response = vpsApi.generateChat(ollamaRequest)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val content = body.message?.content ?: ""
                        Log.d(TAG, "Response from VPS Ollama: contentLength=${content.length}")
                        val paramsInfo = buildString {
                            append("\n\n---\n")
                            append("С VPS Ollama")
                            append("\nПараметры модели:")
                            append("\n- Модель: ${model.modelId}")
                            append("\n- Temperature: $temperature")
                            append("\n- Context Window: $numCtx")
                            append("\n- Max Tokens: $numPredict")
                            if (useAndroidPrompt) {
                                append("\n- Android/Kotlin/Compose Prompt: включен")
                            }
                        }
                        val contentWithParams = content + paramsInfo
                        val aiResponse = AiResponse(
                            content = contentWithParams,
                            inputTokens = body.promptEvalCount ?: 0,
                            outputTokens = body.evalCount ?: 0,
                            toolCalls = null,
                            finishReason = null
                        )
                        Result.success(aiResponse)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "VPS Ollama API Error ${response.code()}: ${response.message()}")
                        Log.e(TAG, "Error body: $errorBody")
                        Log.e(TAG, "Request was sent to: ${normalizedUrl}api/chat")
                        Log.e(TAG, "Request params: temperature=$temperature, numCtx=$numCtx, numPredict=$numPredict")
                        val errorMessage = if (errorBody != null && errorBody.isNotBlank()) {
                            "VPS Ollama API Error ${response.code()}: $errorBody"
                        } else {
                            "VPS Ollama API Error ${response.code()}: ${response.message()}"
                        }
                        Result.failure(Exception(errorMessage))
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
            Log.e(TAG, "Timeout details: ${e.message}")
            if (model is AiModel.VpsOllama) {
                Log.e(TAG, "VPS Ollama timeout - this may indicate:")
                Log.e(TAG, "  1. Server is processing request but taking too long")
                Log.e(TAG, "  2. Network connection is slow")
                Log.e(TAG, "  3. Server may be overloaded")
                Result.failure(
                    Exception(
                        "VPS Ollama timeout. The server may be processing your request but it's taking too long. " +
                        "Try reducing numPredict (max tokens) or numCtx (context window) in settings."
                    )
                )
            } else {
                Result.failure(
                    Exception(
                        "Connection timeout. Please check your internet connection and try again."
                    )
                )
            }
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
    
    private fun generateAndroidKotlinComposePrompt(): String {
        return """
            Ты — senior Android-разработчик с опытом работы с Kotlin, Jetpack Compose, и современными Android практиками.
            
            Принципы:
            - Kotlin: используй современные idioms (data classes, sealed classes, coroutines), следуй SOLID, избегай null-unsafe кода
            - Compose: декларативность, композиция, remember/LaunchedEffect правильно, оптимизируй рекомпозиции
            - Архитектура: Clean Architecture, Repository pattern, MVI/MVVM, DI (Dagger/Koin), Coroutines/Flow
            - Performance: избегай memory leaks, lazy loading, кэширование, не блокируй main thread
            - Code Quality: короткие функции (< 20 строк), понятные имена, следуй Kotlin Coding Conventions
            
            Формат: конкретные примеры кода, альтернативные подходы, предупреждения о проблемах, полные решения с учетом архитектуры.
            
            Отвечай на русском, если вопрос на русском, и на английском, если на английском.
        """.trimIndent()
    }
}

