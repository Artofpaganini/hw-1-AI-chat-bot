package com.example.aiagentchat.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.aiagentchat.api.ChatMessage
import com.example.aiagentchat.api.DeepSeekChatRequest
import com.example.aiagentchat.api.RetrofitClient
import com.example.aiagentchat.data.AiResponseData
import java.io.File

data class AiResponse(val rawResponse: String, val parsedData: AiResponseData?)

class ChatRepository(private val apiKey: String? = null, private val context: Context? = null) {
    private val conversationHistory = mutableListOf<ChatMessage>()
    private var userMessageCount = 0
    private var isFirstMessage = true
    private var currentPromptType: PromptType? = null

    private enum class PromptType {
        BEAUTY,
        PRISONER,
        ANALYST
    }

    companion object {
        private const val ANALYTICS_FILE_NAME = "analytic-result.txt"

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

            // Очищаем файл аналитики при первом сообщении новой сессии
            if (isFirstMessage) {
                clearAnalyticsFile()
                isFirstMessage = false
            }

            // Увеличиваем счетчик сообщений пользователя
            userMessageCount++

            // Проверяем, не запрошена ли аналитика
            val messageLower = userMessage.lowercase().trim()
            val isAnalyticsRequest =
                    messageLower.contains("дай мне аналитику") ||
                            messageLower.contains("аналитика") ||
                            messageLower.contains("анализ")

            // Определяем тип промпта
            val newPromptType =
                    when {
                        isAnalyticsRequest -> PromptType.ANALYST
                        userMessageCount < 4 -> PromptType.BEAUTY
                        else -> PromptType.PRISONER
                    }

            // Если промпт изменился, очищаем историю для чистого контекста
            if (currentPromptType != null && currentPromptType != newPromptType) {
                conversationHistory.clear()
            }
            currentPromptType = newPromptType

            // Выбираем systemPrompt
            val systemPrompt =
                    when (newPromptType) {
                        PromptType.ANALYST -> ANALYST_SYSTEM_PROMPT
                        PromptType.BEAUTY -> BEAUTY_SYSTEM_PROMPT
                        PromptType.PRISONER -> PRISONER_SYSTEM_PROMPT
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

                // Если это ответ аналитика, записываем в файл
                if (isAnalyticsRequest) {
                    writeAnalyticsToFile(content)
                }

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
        isFirstMessage = true
        currentPromptType = null
        clearAnalyticsFile()
    }

    fun getUserMessageCount(): Int = userMessageCount

    private fun clearAnalyticsFile() {
        context?.let { ctx ->
            try {
                // Очищаем в external storage
                val externalDir = ctx.getExternalFilesDir(null)
                if (externalDir != null) {
                    val externalFile = File(externalDir, ANALYTICS_FILE_NAME)
                    if (externalFile.exists()) {
                        externalFile.writeText("")
                    }
                }

                // Очищаем в files directory
                val filesDir = ctx.filesDir
                val filesFile = File(filesDir, ANALYTICS_FILE_NAME)
                if (filesFile.exists()) {
                    filesFile.writeText("")
                }
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }
                ?: run {
                    // Если context отсутствует, пробуем очистить в текущей директории
                    try {
                        val file = File(ANALYTICS_FILE_NAME)
                        if (file.exists()) {
                            file.writeText("")
                        }
                    } catch (e: Exception) {
                        // Игнорируем ошибки
                    }
                }
    }

    private fun writeAnalyticsToFile(content: String) {
        // Список путей для попытки записи
        val filePaths = mutableListOf<File>()

        context?.let { ctx ->
            try {
                // 1. Пытаемся записать в корень проекта (если доступен через external storage)
                try {
                    val parentDir =
                            ctx.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile
                    if (parentDir != null && parentDir.exists()) {
                        val projectRoot = File(parentDir, ANALYTICS_FILE_NAME)
                        filePaths.add(projectRoot)
                    }
                } catch (e: Exception) {
                    // Игнорируем
                }

                // 2. Пытаемся записать в корень проекта через абсолютный путь
                try {
                    val currentDirPath = System.getProperty("user.dir")
                    if (currentDirPath != null) {
                        val currentDir = File(currentDirPath)
                        if (currentDir.exists()) {
                            val projectFile = File(currentDir, ANALYTICS_FILE_NAME)
                            filePaths.add(projectFile)
                        }
                    }
                } catch (e: Exception) {
                    // Игнорируем
                }

                // 3. External storage (гарантированно работает)
                val externalDir = ctx.getExternalFilesDir(null)
                if (externalDir != null) {
                    filePaths.add(File(externalDir, ANALYTICS_FILE_NAME))
                }

                // 4. Files directory приложения
                filePaths.add(File(ctx.filesDir, ANALYTICS_FILE_NAME))

                // 5. Downloads (если доступен)
                try {
                    val downloadsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadsDir != null) {
                        filePaths.add(File(downloadsDir, ANALYTICS_FILE_NAME))
                    }
                } catch (e: Exception) {
                    // Игнорируем
                }

                // Записываем во все доступные места
                var successCount = 0
                for (file in filePaths) {
                    try {
                        // Создаем директорию, если не существует
                        file.parentFile?.mkdirs()

                        // Проверяем, существует ли файл
                        if (file.exists()) {
                            // Очищаем существующий файл
                            file.writeText("", Charsets.UTF_8)
                        }

                        // Записываем контент (создаст файл, если не существует)
                        file.writeText(content, Charsets.UTF_8)
                        successCount++
                        Log.d("ChatRepository", "Файл записан: ${file.absolutePath}")
                    } catch (e: Exception) {
                        Log.w(
                                "ChatRepository",
                                "Не удалось записать в ${file.absolutePath}: ${e.message}",
                                e
                        )
                    }
                }

                if (successCount == 0) {
                    Log.e("ChatRepository", "Не удалось записать файл ни в одно место")
                } else {
                    Log.i("ChatRepository", "Файл успешно записан в $successCount место(а)")
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "Критическая ошибка записи аналитики: ${e.message}", e)
            }
        }
                ?: run {
                    // Если context отсутствует, пробуем записать в текущую директорию
                    try {
                        val file = File(ANALYTICS_FILE_NAME)
                        if (file.exists()) {
                            file.writeText("", Charsets.UTF_8)
                        }
                        file.writeText(content, Charsets.UTF_8)
                        Log.d("ChatRepository", "Файл записан без context: ${file.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(
                                "ChatRepository",
                                "Ошибка записи аналитики без context: ${e.message}",
                                e
                        )
                    }
                }
    }
}
