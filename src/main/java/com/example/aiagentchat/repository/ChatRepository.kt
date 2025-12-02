package com.example.aiagentchat.repository

import com.example.aiagentchat.api.*
import com.example.aiagentchat.data.AiResponseData

data class AiResponse(
    val rawResponse: String,
    val parsedData: AiResponseData?
)

class ChatRepository(
    private val apiKey: String? = null
) {
    private val conversationHistory = mutableListOf<ChatMessage>()
    
    companion object {
        private const val JSON_SYSTEM_PROMPT = """Ты — JSON генератор. Твоя единственная функция — преобразовывать запросы в JSON объекты строго определённой структуры.

КОМАНДЫ:
1. Отвечай ТОЛЬКО валидным JSON объектом
2. Используй ТОЛЬКО структуру: {"data": {"title": "...", "description": "...", "category": "...", "tags": [...]}}
3. НИКОГДА не добавляй:
   - Текст до или после JSON
   - Markdown разметку (```json или ```)
   - Пояснения, извинения, приветствия
   - Служебные поля (usage, tokens, timing, model_info)
   - Любые поля вне объекта "data"

СТРУКТУРА ОБЯЗАТЕЛЬНЫХ ПОЛЕЙ:
{
  "data": {
    "title": "Заголовок (3-7 слов, ёмко)",
    "description": "Полное описание (30-100 слов, информативно)",
    "category": "Категория (1-3 слова, обобщающе)",
    "tags": ["массив", "из", "3-5", "ключевых", "слов"]
  }
}

ГАРАНТИЯ ЧИСТОТЫ: Если не можешь сгенерировать ответ, верни: {"data": {"title": "Ошибка", "description": "Не удалось обработать запрос", "category": "Системная ошибка", "tags": ["ошибка"]}}"""
    }

    suspend fun sendMessage(userMessage: String): Result<AiResponse> {
        return try {
            if (apiKey == null) {
                return Result.failure(
                    Exception("API ключ не указан. Получите ключ на https://platform.deepseek.com/")
                )
            }

            val messages = mutableListOf<ChatMessage>()
            messages.add(ChatMessage("system", JSON_SYSTEM_PROMPT))
            messages.addAll(conversationHistory)
            messages.add(ChatMessage("user", userMessage))

            val request = DeepSeekChatRequest(
                model = "deepseek-chat",
                messages = messages,
                temperature = 0.7,
                max_tokens = 2048
            )

            val response = RetrofitClient.deepSeekService.sendMessage(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!.choices.firstOrNull()?.message?.content
                    ?: """{"data": {"title": "Ошибка", "description": "Не удалось получить ответ", "category": "Системная ошибка", "tags": ["ошибка"]}}"""

                conversationHistory.add(ChatMessage("user", userMessage))
                conversationHistory.add(ChatMessage("assistant", content))
                
                val parsedData = AiResponseData.fromJson(content)
                
                Result.success(AiResponse(
                    rawResponse = content,
                    parsedData = parsedData
                ))
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Ошибка: ${response.code()}"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }
}
