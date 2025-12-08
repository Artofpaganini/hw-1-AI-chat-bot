package com.example.aiagentchat.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.aiagentchat.api.ChatMessage
import com.example.aiagentchat.api.DeepSeekChatRequest
import com.example.aiagentchat.api.RetrofitClient
import com.example.aiagentchat.data.AiResponseData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiResponse(val rawResponse: String, val parsedData: AiResponseData?)

data class TemperatureExperimentResult(
    val temperature: Double,
    val temperatureName: String,
    val response: String
)

class ChatRepository(private val apiKey: String? = null, private val context: Context? = null) {
    
    // Хранилище результатов последнего эксперимента с температурами
    private var lastExperimentQuery: String = ""
    private var lastExperimentResults: List<TemperatureExperimentResult> = emptyList()

    companion object {
        private const val ANALYTICS_FILE_NAME = "analytics_result.txt"

        // SystemPrompt для аналитика эксперимента с Temperature
        private const val TEMPERATURE_ANALYST_SYSTEM_PROMPT =
            """Ты — эксперт по анализу поведения языковых моделей. Тебе будут предоставлены результаты эксперимента с разными значениями параметра temperature.

ТВОЯ ЗАДАЧА:
Проанализировать три ответа, сгенерированных с разными температурами (0.0, 0.7, 1.2), и выдать структурированную аналитику.

КРИТЕРИИ ОЦЕНКИ (шкала 1-5):
• ТОЧНОСТЬ — корректность фактов, отсутствие галлюцинаций, логическая связность
• КРЕАТИВНОСТЬ — оригинальность метафор, нестандартность подхода, живость изложения
• РАЗНООБРАЗИЕ — насколько ответ отличается от других вариантов (ребёнок/инженер/поэт)

СТРУКТУРА ТВОЕГО ОТВЕТА:

1. СВОДНАЯ ТАБЛИЦА ОЦЕНОК:
┌─────────────────┬───────────┬──────────────┬─────────────┐
│   TEMPERATURE   │ ТОЧНОСТЬ  │ КРЕАТИВНОСТЬ │ РАЗНООБРАЗИЕ│
├─────────────────┼───────────┼──────────────┼─────────────┤
│ 0.0 (затупок)   │   X/5     │     X/5      │    X/5      │
│ 0.7 (баланс)    │   X/5     │     X/5      │    X/5      │
│ 1.2 (сенсей)    │   X/5     │     X/5      │    X/5      │
└─────────────────┴───────────┴──────────────┴─────────────┘

2. КРАТКИЙ АНАЛИЗ КАЖДОГО ОТВЕТА (2-3 предложения):
   - Характерные особенности и примеры из текста

3. РЕКОМЕНДАЦИИ ПО ВЫБОРУ TEMPERATURE:
   - Высокая точность критична (0.0-0.3): юридика, медицина, техника
   - Требуется креативность (0.8-1.2): копирайтинг, поэзия, сценарии
   - Нужен баланс + разнообразие (0.5-0.8): обучение, чат-боты, генерация идей
   - Когда высокая температура опасна: цитирование, факты, код, безопасные системы

4. ПРЕДУПРЕЖДЕНИЯ О TEMPERATURE > 1.0:
   - В каких случаях стоит избегать и почему

5. КРАТКАЯ ШПАРГАЛКА:
   Одно предложение для каждой настройки — для каких задач лучше подходит.

Отвечай кратко, структурированно, с конкретными примерами из предоставленных ответов."""

    }

    suspend fun sendMessage(userMessage: String): Result<AiResponse> {
        return try {
            if (apiKey == null) {
                return Result.failure(
                    Exception("API ключ не указан. Получите ключ на https://platform.deepseek.com/")
                )
            }

            val messageLower = userMessage.lowercase().trim()
            
            // Проверяем, запрошена ли аналитика
            val isAnalyticsRequest = messageLower.contains("аналитика") ||
                    messageLower.contains("анализ") ||
                    messageLower.contains("дай мне аналитику")

            if (isAnalyticsRequest) {
                // Запрос аналитики — 4-й запрос для анализа предыдущих 3
                return generateAnalytics()
            }

            // Обычный запрос — выполняем 3 запроса с разными температурами
            return runTripleTemperatureQuery(userMessage)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Выполняет 3 идентичных запроса с разными температурами (0.0, 0.7, 1.2)
     * и показывает все 3 ответа пользователю
     */
    private suspend fun runTripleTemperatureQuery(userQuery: String): Result<AiResponse> {
        val temperatures = listOf(
            Triple(0.0, "ЗАТУПОК", "🥶 Temperature 0.0 (Детерминированный)"),
            Triple(0.7, "БАЛАНС", "⚖️ Temperature 0.7 (Сбалансированный)"),
            Triple(1.2, "СЕНСЕЙ", "🔥 Temperature 1.2 (Креативный)")
        )

        val results = mutableListOf<TemperatureExperimentResult>()
        val responseBuilder = StringBuilder()

        responseBuilder.append("📨 **ЗАПРОС:** «$userQuery»\n\n")
        responseBuilder.append("═".repeat(50))
        responseBuilder.append("\n\n")

        // Выполняем 3 запроса последовательно
        for ((temp, name, header) in temperatures) {
            try {
                val request = DeepSeekChatRequest(
                    model = "deepseek-chat",
                    messages = listOf(ChatMessage("user", userQuery)),
                    temperature = temp,
                    max_tokens = 2048
                )

                val response = RetrofitClient.deepSeekService.sendMessage(
                    authorization = "Bearer $apiKey",
                    request = request
                )

                val content = if (response.isSuccessful && response.body() != null) {
                    response.body()!!.choices.firstOrNull()?.message?.content
                        ?: "Ошибка: не удалось получить ответ"
                } else {
                    "Ошибка API: ${response.code()}"
                }

                results.add(TemperatureExperimentResult(
                    temperature = temp,
                    temperatureName = name,
                    response = content
                ))

                // Форматируем ответ для отображения
                responseBuilder.append("**$header**\n")
                responseBuilder.append("─".repeat(40))
                responseBuilder.append("\n")
                responseBuilder.append(content)
                responseBuilder.append("\n\n")

            } catch (e: Exception) {
                Log.e("ChatRepository", "Ошибка при temperature $temp: ${e.message}", e)
                results.add(TemperatureExperimentResult(
                    temperature = temp,
                    temperatureName = name,
                    response = "Ошибка: ${e.message}"
                ))
                
                responseBuilder.append("**$header**\n")
                responseBuilder.append("❌ Ошибка: ${e.message}\n\n")
            }
        }

        // Сохраняем результаты для последующей аналитики
        lastExperimentQuery = userQuery
        lastExperimentResults = results

        responseBuilder.append("═".repeat(50))
        responseBuilder.append("\n")
        responseBuilder.append("💡 Напишите **«аналитика»** для получения сравнительного анализа и рекомендаций")

        return Result.success(AiResponse(rawResponse = responseBuilder.toString(), parsedData = null))
    }

    /**
     * Генерирует аналитику на основе сохранённых 3 ответов (4-й запрос к AI)
     */
    private suspend fun generateAnalytics(): Result<AiResponse> {
        if (lastExperimentResults.isEmpty()) {
            return Result.failure(
                Exception("Нет данных для анализа. Сначала отправьте запрос, чтобы получить ответы с разными температурами.")
            )
        }

        // Формируем промпт для аналитика
        val analysisPrompt = buildAnalysisPrompt(lastExperimentQuery, lastExperimentResults)

        val request = DeepSeekChatRequest(
            model = "deepseek-chat",
            messages = listOf(
                ChatMessage("system", TEMPERATURE_ANALYST_SYSTEM_PROMPT),
                ChatMessage("user", analysisPrompt)
            ),
            temperature = 0.3,
            max_tokens = 4096
        )

        val response = RetrofitClient.deepSeekService.sendMessage(
            authorization = "Bearer $apiKey",
            request = request
        )

        val analysisContent = if (response.isSuccessful && response.body() != null) {
            response.body()!!.choices.firstOrNull()?.message?.content
                ?: "Ошибка: не удалось получить анализ"
        } else {
            return Result.failure(Exception("Ошибка API: ${response.code()}"))
        }

        // Формируем полный отчёт для файла
        val fullReport = buildFullReport(lastExperimentQuery, lastExperimentResults, analysisContent)

        // Записываем в файл
        writeAnalyticsToFile(fullReport)

        // Формируем ответ для чата
        val chatResponse = buildString {
            append("📊 **АНАЛИТИКА ЭКСПЕРИМЕНТА С TEMPERATURE**\n\n")
            append("📌 Исходный запрос: «$lastExperimentQuery»\n\n")
            append("═".repeat(50))
            append("\n\n")
            append(analysisContent)
            append("\n\n")
            append("═".repeat(50))
            append("\n")
            append("📁 Полный отчёт сохранён в: $ANALYTICS_FILE_NAME")
        }

        return Result.success(AiResponse(rawResponse = chatResponse, parsedData = null))
    }

    private fun buildAnalysisPrompt(query: String, results: List<TemperatureExperimentResult>): String {
        return buildString {
            append("Проанализируй результаты эксперимента с параметром temperature.\n\n")
            append("ИСХОДНЫЙ ЗАПРОС: «$query»\n\n")
            append("═".repeat(60))
            append("\n\n")

            for (result in results) {
                append("### TEMPERATURE ${result.temperature} (${result.temperatureName}):\n")
                append(result.response)
                append("\n\n")
                append("─".repeat(60))
                append("\n\n")
            }

            append("Оцени каждый ответ по критериям (1-5):\n")
            append("• Точность — корректность фактов, отсутствие галлюцинаций\n")
            append("• Креативность — оригинальность метафор, нестандартность подхода\n")
            append("• Разнообразие — различие между тремя ответами (ребёнок/инженер/поэт)\n\n")
            append("Представь результаты в виде таблицы.\n")
            append("Дай рекомендации по выбору temperature для разных типов задач.\n")
            append("Укажи, когда стоит избегать temperature > 1.0 и почему.")
        }
    }

    private fun buildFullReport(
        query: String,
        results: List<TemperatureExperimentResult>,
        analysis: String
    ): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        
        return buildString {
            append("═".repeat(80))
            append("\n")
            append("           ЭКСПЕРИМЕНТ: ВЛИЯНИЕ ПАРАМЕТРА TEMPERATURE НА ПОВЕДЕНИЕ LLM\n")
            append("═".repeat(80))
            append("\n\n")
            append("📅 Дата: ${dateFormat.format(Date())}\n")
            append("🤖 Модель: DeepSeek Chat\n\n")
            append("📌 ЗАПРОС: «$query»\n\n")
            
            append("═".repeat(80))
            append("\n")
            append("                              РЕЗУЛЬТАТЫ ЭКСПЕРИМЕНТА\n")
            append("═".repeat(80))
            append("\n\n")

            for (result in results) {
                val emoji = when (result.temperature) {
                    0.0 -> "🥶"
                    0.7 -> "⚖️"
                    else -> "🔥"
                }
                append("$emoji TEMPERATURE ${result.temperature} (${result.temperatureName})\n")
                append("─".repeat(78))
                append("\n")
                append(result.response)
                append("\n\n")
            }

            append("═".repeat(80))
            append("\n")
            append("                              АНАЛИЗ И РЕКОМЕНДАЦИИ\n")
            append("═".repeat(80))
            append("\n\n")
            append(analysis)
            append("\n\n")
            append("═".repeat(80))
            append("\n")
            append("                              КОНЕЦ ОТЧЁТА\n")
            append("═".repeat(80))
        }
    }

    fun clearHistory() {
        lastExperimentQuery = ""
        lastExperimentResults = emptyList()
    }

    private fun writeAnalyticsToFile(content: String) {
        val filePaths = mutableListOf<File>()

        context?.let { ctx ->
            try {
                // External storage (гарантированно работает)
                val externalDir = ctx.getExternalFilesDir(null)
                if (externalDir != null) {
                    filePaths.add(File(externalDir, ANALYTICS_FILE_NAME))
                }

                // Files directory приложения
                filePaths.add(File(ctx.filesDir, ANALYTICS_FILE_NAME))

                // Downloads
                try {
                    val downloadsDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadsDir != null) {
                        filePaths.add(File(downloadsDir, ANALYTICS_FILE_NAME))
                    }
                } catch (e: Exception) {
                    // Игнорируем
                }

                var successCount = 0
                for (file in filePaths) {
                    try {
                        file.parentFile?.mkdirs()
                        file.writeText(content, Charsets.UTF_8)
                        successCount++
                        Log.d("ChatRepository", "Файл записан: ${file.absolutePath}")
                    } catch (e: Exception) {
                        Log.w("ChatRepository", "Не удалось записать в ${file.absolutePath}: ${e.message}", e)
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
        } ?: run {
            try {
                val file = File(ANALYTICS_FILE_NAME)
                file.writeText(content, Charsets.UTF_8)
                Log.d("ChatRepository", "Файл записан без context: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("ChatRepository", "Ошибка записи аналитики без context: ${e.message}", e)
            }
        }
    }
}
