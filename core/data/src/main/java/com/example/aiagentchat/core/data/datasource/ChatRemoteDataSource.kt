package com.example.aiagentchat.core.data.datasource

import com.example.aiagentchat.core.network.RetrofitClient
import com.example.aiagentchat.core.network.api.DeepSeekApiService
import com.example.aiagentchat.core.network.dto.ChatMessageDto
import com.example.aiagentchat.core.network.dto.DeepSeekChatRequestDto

interface ChatRemoteDataSource {
    suspend fun sendMessage(
        apiKey: String,
        messages: List<ChatMessageDto>
    ): Result<String>
}

class ChatRemoteDataSourceImpl(
    private val apiService: DeepSeekApiService = RetrofitClient.deepSeekService
) : ChatRemoteDataSource {
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

    override suspend fun sendMessage(
        apiKey: String,
        messages: List<ChatMessageDto>
    ): Result<String> {
        return try {
            val requestMessages = mutableListOf<ChatMessageDto>()
            requestMessages.add(ChatMessageDto("system", JSON_SYSTEM_PROMPT))
            requestMessages.addAll(messages)

            val request = DeepSeekChatRequestDto(
                model = "deepseek-chat",
                messages = requestMessages,
                temperature = 0.7,
                maxTokens = 2048
            )

            val response =  apiService.sendMessage(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!.choices.firstOrNull()?.message?.content
                    ?: """{"data": {"title": "Ошибка", "description": "Не удалось получить ответ", "category": "Системная ошибка", "tags": ["ошибка"]}}"""
                Result.success(content)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Ошибка: ${response.code()}"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

