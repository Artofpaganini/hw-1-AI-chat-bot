package com.example.aiagentchat.repository

import com.example.aiagentchat.api.*

class ChatRepository(
        private val provider: ApiProvider,
        private val apiKey: String? = null,
        private val ollamaBaseUrl: String = "http://10.0.2.2:11434/"
) {

    private val conversationHistory = mutableListOf<ChatMessage>()
    private val pastUserInputs = mutableListOf<String>()
    private val generatedResponses = mutableListOf<String>()

    suspend fun sendMessage(userMessage: String): Result<String> {
        return when (provider) {
            ApiProvider.QWEN -> sendMessageToQwen(userMessage)
            ApiProvider.DEEPSEEK -> sendMessageToDeepSeek(userMessage)
            ApiProvider.GROQ -> sendMessageToGroq(userMessage)
            ApiProvider.OPENAI -> sendMessageToOpenAI(userMessage)
            ApiProvider.HUGGINGFACE -> sendMessageToHuggingFace(userMessage)
            ApiProvider.OLLAMA -> sendMessageToOllama(userMessage)
        }
    }

    private suspend fun sendMessageToQwen(userMessage: String): Result<String> {
        return try {
            if (apiKey == null) {
                return Result.failure(
                        Exception(
                                "API ключ не указан. Получите бесплатный ключ на https://api.together.xyz/"
                        )
                )
            }

            conversationHistory.add(ChatMessage("user", userMessage))

            val request =
                    QwenChatRequest(
                            model = "Qwen/Qwen2.5-VL-72B-Instruct",
                            messages = conversationHistory.toList(),
                            temperature = 0.7,
                            max_tokens = 2048
                    )

            val response =
                    RetrofitClient.qwenService.sendMessage(
                            authorization = "Bearer $apiKey",
                            request = request
                    )

            if (response.isSuccessful && response.body() != null) {
                val aiResponse =
                        response.body()!!.choices.firstOrNull()?.message?.content
                                ?: "Не удалось получить ответ"

                conversationHistory.add(ChatMessage("assistant", aiResponse))
                Result.success(aiResponse)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Ошибка: ${response.code()}"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendMessageToDeepSeek(userMessage: String): Result<String> {
        return try {
            if (apiKey == null) {
                return Result.failure(
                        Exception(
                                "API ключ не указан. Получите бесплатный ключ на https://platform.deepseek.com/"
                        )
                )
            }

            conversationHistory.add(ChatMessage("user", userMessage))

            val request =
                    DeepSeekChatRequest(
                            model = "deepseek-chat",
                            messages = conversationHistory.toList(),
                            temperature = 0.7,
                            max_tokens = 2048
                    )

            val response =
                    RetrofitClient.deepSeekService.sendMessage(
                            authorization = "Bearer $apiKey",
                            request = request
                    )

            if (response.isSuccessful && response.body() != null) {
                val aiResponse =
                        response.body()!!.choices.firstOrNull()?.message?.content
                                ?: "Не удалось получить ответ"

                conversationHistory.add(ChatMessage("assistant", aiResponse))
                Result.success(aiResponse)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Ошибка: ${response.code()}"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendMessageToGroq(userMessage: String): Result<String> {
        return try {
            if (apiKey == null) {
                return Result.failure(
                        Exception(
                                "API ключ не указан. Получите бесплатный ключ на https://console.groq.com/"
                        )
                )
            }

            conversationHistory.add(ChatMessage("user", userMessage))

            val request =
                    GroqChatRequest(
                            model = "llama-3.1-8b-instant",
                            messages = conversationHistory.toList(),
                            temperature = 0.7,
                            max_tokens = 1024
                    )

            val response =
                    RetrofitClient.groqService.sendMessage(
                            authorization = "Bearer $apiKey",
                            request = request
                    )

            if (response.isSuccessful && response.body() != null) {
                val aiResponse =
                        response.body()!!.choices.firstOrNull()?.message?.content
                                ?: "Не удалось получить ответ"

                conversationHistory.add(ChatMessage("assistant", aiResponse))
                Result.success(aiResponse)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Ошибка: ${response.code()}"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendMessageToOpenAI(userMessage: String): Result<String> {
        return try {
            if (apiKey == null) {
                return Result.failure(Exception("API ключ не указан"))
            }

            conversationHistory.add(ChatMessage("user", userMessage))

            val request =
                    ChatRequest(
                            model = "gpt-3.5-turbo",
                            messages = conversationHistory.toList(),
                            temperature = 0.7
                    )

            val response =
                    RetrofitClient.openAiService.sendMessage(
                            authorization = "Bearer $apiKey",
                            request = request
                    )

            if (response.isSuccessful && response.body() != null) {
                val aiResponse =
                        response.body()!!.choices.firstOrNull()?.message?.content
                                ?: "Не удалось получить ответ"

                conversationHistory.add(ChatMessage("assistant", aiResponse))
                Result.success(aiResponse)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Ошибка: ${response.code()}"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendMessageToHuggingFace(userMessage: String): Result<String> {
        return try {
            pastUserInputs.add(userMessage)

            val request =
                    HuggingFaceRequest(
                            inputs =
                                    HuggingFaceInputs(
                                            past_user_inputs = pastUserInputs.dropLast(1),
                                            generated_responses = generatedResponses,
                                            text = userMessage
                                    )
                    )

            // Hugging Face может работать без ключа для некоторых моделей
            val response =
                    RetrofitClient.huggingFaceService.sendMessage(
                            authorization = null, // Можно добавить ключ если нужен
                            request = request
                    )

            if (response.isSuccessful && response.body() != null) {
                val aiResponse = response.body()!!.generated_text ?: "Не удалось получить ответ"

                generatedResponses.add(aiResponse)
                Result.success(aiResponse)
            } else {
                // Hugging Face может вернуть ошибку, если модель загружается
                if (response.code() == 503) {
                    Result.failure(
                            Exception("Модель загружается, попробуйте через несколько секунд")
                    )
                } else {
                    val errorMessage =
                            response.errorBody()?.string() ?: "Ошибка: ${response.code()}"
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendMessageToOllama(userMessage: String): Result<String> {
        return try {
            // Для Ollama формируем промпт с историей
            val context = buildString {
                conversationHistory.forEach { msg ->
                    append(if (msg.role == "user") "Пользователь: " else "Ассистент: ")
                    append(msg.content)
                    append("\n")
                }
                append("Пользователь: $userMessage\nАссистент: ")
            }

            val request = OllamaRequest(model = "llama2", prompt = context, stream = false)

            val ollamaService = RetrofitClient.createOllamaService(ollamaBaseUrl)
            val response = ollamaService.sendMessage(request)

            if (response.isSuccessful && response.body() != null) {
                val aiResponse = response.body()!!.response
                conversationHistory.add(ChatMessage("user", userMessage))
                conversationHistory.add(ChatMessage("assistant", aiResponse))
                Result.success(aiResponse)
            } else {
                val errorMessage =
                        response.errorBody()?.string()
                                ?: "Ошибка: ${response.code()}. Убедитесь, что Ollama запущен локально"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(
                    Exception(
                            "Не удалось подключиться к Ollama: ${e.message}. Убедитесь, что Ollama запущен на $ollamaBaseUrl"
                    )
            )
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
        pastUserInputs.clear()
        generatedResponses.clear()
    }
}
