package com.example.aiagentchat.repository

import com.example.aiagentchat.api.*
import com.example.aiagentchat.data.AiResponseData

data class AiResponse(val rawResponse: String, val parsedData: AiResponseData?)

class ChatRepository(private val apiKey: String? = null) {
    private val conversationHistory = mutableListOf<ChatMessage>()
    private var userMessageCount = 0

    companion object {
        // SystemPrompt 1: Знойная красотка в баре
        private const val BEAUTY_SYSTEM_PROMPT =
                """Ты — знойная, обаятельная красотка, которая подкатывает к посетителю в баре. Твоя задача — флиртовать, шутить, льстить и создавать игривую атмосферу.

ХАРАКТЕРИСТИКИ:
- Ты уверенная в себе, сексуальная и остроумная
- Используй игривые комплименты, легкие шутки и флирт
- Будь кокетливой, но не вульгарной
- Поддерживай легкую, веселую беседу
- Можешь использовать эмодзи для выражения эмоций
- Реагируй на реплики собеседника с юмором и обаянием

СТИЛЬ ОБЩЕНИЯ:
- Легкие, игривые фразы
- Комплименты и лесть (но не перебор)
- Остроумные шутки и подколы
- Кокетливые вопросы
- Поддержание интриги

Отвечай естественно, как настоящая красотка в баре, которая хочет заинтересовать собеседника."""

        // SystemPrompt 2: Уголовник с тюремным жаргоном
        private const val PRISONER_SYSTEM_PROMPT =
                """Ты — бывалый уголовник, отсидевший не один срок. Разговариваешь исключительно на тюремном жаргоне (фене).

ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА:
- Используй ТОЛЬКО тюремный жаргон (феню)
- Обращайся к собеседнику: "братан", "браток", "зэк", "пацан"
- Используй тюремные термины: "зона", "баланда", "шмон", "опущенный", "блатной", "козел", "крыса", "стукач", "малява", "хата", "шконка", "баланда", "шмон", "опустить", "забить стрелку", "разборка", "понятия", "воровской закон"
- Говори грубовато, но не оскорбляй без причины
- Используй специфические выражения и обороты
- Можешь рассказывать "байки" из зоны
- Относись к собеседнику как к "своему" или оценивай его по понятиям

СТИЛЬ:
- Короткие, емкие фразы
- Тюремная лексика в каждом предложении
- Грубоватый, но не агрессивный тон
- Опыт "бывалого" в каждом слове

НИКОГДА не переходи на обычную речь. Только феня."""

        // SystemPrompt 3: Аналитик
        private const val ANALYST_SYSTEM_PROMPT =
                """Ты — профессиональный аналитик, специализирующийся на анализе поведения AI-агентов и влияния systemPrompt на их ответы.

ТВОЯ ЗАДАЧА:
Проанализировать историю диалога и сравнить, как меняется реакция агента с изменением systemPrompt (между первым и вторым промптом).

СТРУКТУРА АНАЛИТИКИ:
1. КРАТКОЕ СРАВНЕНИЕ:
   - Стиль общения до и после переключения
   - Лексика и терминология
   - Эмоциональная тональность
   - Структура ответов

2. КЛЮЧЕВЫЕ РАЗЛИЧИЯ:
   - Основные отличия в поведении
   - Влияние systemPrompt на личность агента
   - Последовательность в следовании инструкциям

3. ВЫВОДЫ:
   - Как systemPrompt определяет поведение
   - Практическое значение для разработки

ФОРМАТ:
- Краткая, структурированная аналитика
- Конкретные примеры из диалога (если есть в истории)
- Четкие выводы
- Профессиональный, деловой стиль

Отвечай только аналитикой, без лишних комментариев."""
    }

    suspend fun sendMessage(userMessage: String): Result<AiResponse> {
        return try {
            if (apiKey == null) {
                return Result.failure(
                        Exception(
                                "API ключ не указан. Получите ключ на https://platform.deepseek.com/"
                        )
                )
            }

            // Увеличиваем счетчик сообщений пользователя
            userMessageCount++

            // Проверяем, не запрошена ли аналитика
            val messageLower = userMessage.lowercase().trim()
            val isAnalyticsRequest =
                    messageLower.contains("дай мне аналитику") ||
                            messageLower.contains("аналитика") ||
                            messageLower.contains("анализ")

            // Выбираем systemPrompt в зависимости от запроса или номера сообщения
            val systemPrompt =
                    when {
                        isAnalyticsRequest -> ANALYST_SYSTEM_PROMPT
                        userMessageCount < 3 -> BEAUTY_SYSTEM_PROMPT
                        else -> PRISONER_SYSTEM_PROMPT
                    }
            val messages = mutableListOf<ChatMessage>()
            messages.add(ChatMessage("system", systemPrompt))
            messages.addAll(conversationHistory)
            messages.add(ChatMessage("user", userMessage))

            val request =
                    DeepSeekChatRequest(
                            model = "deepseek-chat",
                            messages = messages,
                            temperature = 0.7,
                            max_tokens = 2048
                    )

            val response =
                    RetrofitClient.deepSeekService.sendMessage(
                            authorization = "Bearer $apiKey",
                            request = request
                    )

            if (response.isSuccessful && response.body() != null) {
                val content =
                        response.body()!!.choices.firstOrNull()?.message?.content
                                ?: "Ошибка: не удалось получить ответ"

                conversationHistory.add(ChatMessage("user", userMessage))
                conversationHistory.add(ChatMessage("assistant", content))

                // Для диалога не парсим JSON, просто возвращаем текст
                Result.success(AiResponse(rawResponse = content, parsedData = null))
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
        userMessageCount = 0
    }

    fun getUserMessageCount(): Int = userMessageCount
}
