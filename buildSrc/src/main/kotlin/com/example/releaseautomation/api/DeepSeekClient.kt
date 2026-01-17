package com.example.releaseautomation.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.logging.Logger

class DeepSeekClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.deepseek.com"
) {
    private val logger = Logger.getLogger(DeepSeekClient::class.java.name)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Включаем дефолтные значения, чтобы model всегда был в JSON
        prettyPrint = false
    }
    
    private fun sanitizeForJson(text: String): String {
        // Убираем проблемные символы, которые могут сломать JSON
        return text
            .replace("\u0000", "") // Null bytes
            .replace("\r", "") // Carriage return
            .take(7000) // Дополнительное ограничение
    }
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            requestTimeout = 120_000
        }
    }
    
    private fun HttpRequestBuilder.setupRequest() {
        url("$baseUrl/v1/chat/completions")
        header(HttpHeaders.Authorization, "Bearer $apiKey")
    }
    
    suspend fun analyzeCommitsForRelease(
        commits: List<String>,
        gitDiff: String,
        ragContext: String
    ): ReleaseAnalysis {
        val systemPrompt = """
            Ты senior Android/Kotlin разработчик с 8+ летним опытом.
            Твоя задача - проанализировать изменения в Android приложении с AI чатом.
            
            Проанализируй предоставленные изменения и верни JSON с результатами анализа.
            Формат ответа должен быть валидным JSON объектом со следующими полями:
            - versionBump: "major" | "minor" | "patch" (по Semantic Versioning)
            - releaseNotesRu: подробные release notes на русском языке
            - releaseNotesEn: подробные release notes на английском языке
            - whatsNewRu: краткое описание "Что нового" для Google Play (максимум 500 символов) на русском
            - whatsNewEn: краткое описание "What's New" для Google Play (максимум 500 символов) на английском
            - breakingChanges: массив строк с описанием breaking changes (может быть null)
            - changelog: markdown форматированный changelog
            - userImpact: "low" | "medium" | "high"
            - summary: краткое резюме изменений
            
            ВАЖНО: Верни ТОЛЬКО валидный JSON, без markdown форматирования, без дополнительного текста.
        """.trimIndent()
        
        // Ограничиваем размер промпта
        val commitsText = if (commits.size > 15) {
            commits.take(15).joinToString("\n") { "- $it" } + "\n... (and ${commits.size - 15} more commits)"
        } else {
            commits.joinToString("\n") { "- $it" }
        }
        
        // Сильно ограничиваем размер данных для безопасности
        // Используем только summary без полного diff
        val limitedRagContext = ragContext.take(500)
        val limitedCommitsText = if (commits.size > 10) {
            commits.take(10).joinToString("\n") { "- $it" } + "\n... (and ${commits.size - 10} more)"
        } else {
            commits.joinToString("\n") { "- $it" }
        }
        
        // Вместо полного diff используем только summary изменений
        val diffSummary = if (gitDiff.length > 3000) {
            "Large diff detected (${gitDiff.length} chars). Summary: ${gitDiff.lines().take(50).joinToString("\n")} ... (truncated)"
        } else {
            gitDiff.take(3000)
        }
        
        val userPrompt = """
            Контекст: $limitedRagContext
            
            Коммиты:
            $limitedCommitsText
            
            Изменения:
            $diffSummary
            
            Проанализируй и верни JSON.
        """.trimIndent()
        
        return retryWithBackoff(maxRetries = 3) { attempt ->
            logger.info("Analyzing commits (attempt $attempt)...")
            
            val request = ChatRequest(
                model = "deepseek-chat",
                messages = listOf(
                    Message(role = "system", content = systemPrompt),
                    Message(role = "user", content = userPrompt)
                ),
                temperature = 0.7,
                max_tokens = 4000
            )
            
            // Валидируем и ограничиваем размер промптов перед созданием запроса
            // Ограничиваем до минимума для избежания проблем с JSON
            val safeSystemPrompt = sanitizeForJson(systemPrompt.take(1200))
            val safeUserPrompt = sanitizeForJson(userPrompt.take(4000)) // Еще больше ограничиваем
            
            val safeRequest = ChatRequest(
                model = request.model,
                messages = listOf(
                    Message(role = "system", content = safeSystemPrompt),
                    Message(role = "user", content = safeUserPrompt)
                ),
                temperature = request.temperature,
                max_tokens = request.max_tokens
            )
            
            logger.info("Sending request to ${baseUrl}/v1/chat/completions")
            logger.info("Request messages count: ${safeRequest.messages.size}")
            logger.info("System prompt size: ${safeSystemPrompt.length} chars")
            logger.info("User prompt size: ${safeUserPrompt.length} chars")
            
            // Используем явную сериализацию для контроля
            val requestJson = try {
                val serialized = json.encodeToString(ChatRequest.serializer(), safeRequest)
                logger.info("Serialized request size: ${serialized.length} chars")
                logger.info("JSON starts with: ${serialized.take(100)}")
                logger.info("JSON ends with: ${serialized.takeLast(100)}")
                serialized
            } catch (e: Exception) {
                logger.severe("Failed to serialize request: ${e.message}")
                logger.severe("Request model: ${safeRequest.model}")
                logger.severe("Request messages count: ${safeRequest.messages.size}")
                throw IllegalStateException("Failed to serialize request", e)
            }
            
            // Проверяем что JSON содержит все необходимые поля (порядок не важен в JSON)
            val hasModel = requestJson.contains("\"model\"")
            val hasMessages = requestJson.contains("\"messages\"")
            
            if (!hasModel || !hasMessages) {
                logger.severe("JSON is missing required fields! hasModel=$hasModel, hasMessages=$hasMessages")
                logger.severe("JSON full length: ${requestJson.length}")
                logger.severe("JSON preview (first 1000): ${requestJson.take(1000)}")
                logger.severe("JSON preview (last 500): ${requestJson.takeLast(500)}")
                throw IllegalStateException("Invalid JSON structure - missing fields")
            }
            
            // Проверяем валидность JSON
            try {
                val test = json.decodeFromString<ChatRequest>(requestJson)
                logger.info("JSON validation passed, model: ${test.model}")
            } catch (e: Exception) {
                logger.severe("Serialized JSON is invalid: ${e.message}")
                logger.severe("JSON preview (first 500): ${requestJson.take(500)}")
                logger.severe("JSON preview (around error): ${requestJson.drop(4500).take(200)}")
                throw IllegalStateException("Invalid JSON after serialization", e)
            }
            
            val httpResponse = try {
                client.post {
                    setupRequest()
                    contentType(ContentType.Application.Json)
                    setBody(requestJson)
                }
            } catch (e: Exception) {
                logger.severe("Failed to send request: ${e.message}")
                throw e
            }
            
            val statusCode = httpResponse.status.value
            logger.info("Response status: $statusCode")
            
            if (statusCode !in 200..299) {
                val errorBody = try {
                    httpResponse.body<String>()
                } catch (e: Exception) {
                    "Could not read error body: ${e.message}"
                }
                logger.severe("API error ($statusCode): $errorBody")
                throw IllegalStateException("DeepSeek API returned error $statusCode: $errorBody")
            }
            
            val responseString = try {
                httpResponse.body<String>()
            } catch (e: Exception) {
                logger.severe("Failed to read response body: ${e.message}")
                throw IllegalStateException("Failed to read response: ${e.message}", e)
            }
            
            if (responseString.isBlank()) {
                throw IllegalStateException("Empty response from DeepSeek API")
            }
            
            logger.info("Response body length: ${responseString.length}")
            val response = try {
                json.decodeFromString<ChatResponse>(responseString)
            } catch (e: Exception) {
                logger.severe("Failed to parse response JSON: ${e.message}")
                logger.severe("Response preview: ${responseString.take(500)}")
                throw IllegalStateException("Failed to parse response: ${e.message}", e)
            }
            
            val content = response.choices?.firstOrNull()?.message?.content
                ?: throw IllegalStateException("Empty response from DeepSeek API")
            
            logger.info("Received response: ${content.take(200)}...")
            
            // Парсим JSON из ответа (может быть обернут в markdown code block)
            val jsonContent = extractJsonFromResponse(content)
            
            try {
                Json.decodeFromString<ReleaseAnalysis>(jsonContent)
            } catch (e: Exception) {
                logger.warning("Failed to parse JSON, trying to extract: ${e.message}")
                // Fallback: создаем базовый анализ
                createFallbackAnalysis(commits, gitDiff)
            }
        }
    }
    
    suspend fun reviewCode(
        filePath: String,
        gitDiff: String,
        ragContext: String
    ): CodeReviewResult {
        val systemPrompt = """
            Ты senior Android/Kotlin разработчик с 8+ летним опытом.
            Твоя задача - провести code review изменений.
            
            Проверь код на:
            - Android best practices
            - Kotlin idioms и стиль кода
            - Потенциальные memory leaks
            - Security issues
            - Performance проблемы
            
            Верни JSON массив issues с полями:
            - severity: "info" | "warning" | "error"
            - filePath: путь к файлу
            - lineNumber: номер строки (может быть null)
            - message: описание проблемы
            - suggestion: предложение по исправлению (может быть null)
            
            ВАЖНО: Верни ТОЛЬКО валидный JSON массив, без markdown форматирования.
        """.trimIndent()
        
        val userPrompt = """
            Контекст файла из RAG:
            $ragContext
            
            Изменения в файле $filePath:
            $gitDiff
            
            Проведи code review и верни JSON с найденными проблемами.
        """.trimIndent()
        
        return try {
            retryWithBackoff(maxRetries = 3) { attempt ->
                logger.info("Reviewing code (attempt $attempt)...")
                
                val request = ChatRequest(
                    model = "deepseek-chat",
                    messages = listOf(
                        Message(role = "system", content = systemPrompt),
                        Message(role = "user", content = userPrompt)
                    ),
                    temperature = 0.3,
                    max_tokens = 2000
                )
                
                logger.info("Sending code review request to ${baseUrl}/v1/chat/completions")
                
                val requestJson = json.encodeToString(ChatRequest.serializer(), request)
                val httpResponse = client.post {
                    setupRequest()
                    contentType(ContentType.Application.Json)
                    setBody(requestJson)
                }
                
                val statusCode = httpResponse.status.value
                if (statusCode !in 200..299) {
                    val errorBody = httpResponse.body<String>()
                    logger.severe("API error ($statusCode): $errorBody")
                    throw IllegalStateException("DeepSeek API returned error $statusCode: $errorBody")
                }
                
                val responseString = httpResponse.body<String>()
                if (responseString.isBlank()) {
                    throw IllegalStateException("Empty response from DeepSeek API")
                }
                
                val response = json.decodeFromString<ChatResponse>(responseString)
                
                val content = response.choices?.firstOrNull()?.message?.content
                
                if (content == null || content.isBlank()) {
                    throw IllegalStateException("Empty response from DeepSeek API")
                }
                
                val jsonContent = extractJsonFromResponse(content)
                
                try {
                    Json.decodeFromString<CodeReviewResult>(jsonContent)
                } catch (e: Exception) {
                    logger.warning("Failed to parse code review JSON: ${e.message}")
                    CodeReviewResult(issues = emptyList())
                }
            }
        } catch (e: Exception) {
            logger.warning("Code review failed after retries: ${e.message}")
            CodeReviewResult(issues = emptyList())
        }
    }
    
    private fun extractJsonFromResponse(content: String): String {
        // Убираем markdown code blocks если есть
        var json = content.trim()
        if (json.startsWith("```json")) {
            json = json.removePrefix("```json").trim()
        }
        if (json.startsWith("```")) {
            json = json.removePrefix("```").trim()
        }
        if (json.endsWith("```")) {
            json = json.removeSuffix("```").trim()
        }
        return json
    }
    
    private fun createFallbackAnalysis(
        commits: List<String>,
        gitDiff: String
    ): ReleaseAnalysis {
        val hasBreakingChanges = gitDiff.contains("BREAKING", ignoreCase = true) ||
                commits.any { it.contains("BREAKING", ignoreCase = true) }
        
        val versionBump = when {
            hasBreakingChanges -> "major"
            gitDiff.contains("feat", ignoreCase = true) -> "minor"
            else -> "patch"
        }
        
        return ReleaseAnalysis(
            versionBump = versionBump,
            releaseNotesRu = "Обновление приложения с улучшениями и исправлениями.",
            releaseNotesEn = "App update with improvements and bug fixes.",
            whatsNewRu = "Обновление приложения с улучшениями и исправлениями.",
            whatsNewEn = "App update with improvements and bug fixes.",
            breakingChanges = if (hasBreakingChanges) listOf("Обнаружены breaking changes") else null,
            changelog = commits.joinToString("\n") { "- $it" },
            userImpact = "medium",
            summary = "Обновление включает ${commits.size} коммитов"
        )
    }
    
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 1000,
        block: suspend (Int) -> T
    ): T {
        var lastException: Exception? = null
        var delay = initialDelay
        
        repeat(maxRetries) { attempt ->
            try {
                return block(attempt + 1)
            } catch (e: Exception) {
                lastException = e
                logger.warning("Attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < maxRetries - 1) {
                    delay(delay)
                    delay *= 2 // exponential backoff
                }
            }
        }
        
        throw lastException ?: IllegalStateException("All retry attempts failed")
    }
    
    fun close() {
        client.close()
    }
}
