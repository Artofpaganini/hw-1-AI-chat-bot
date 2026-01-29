package com.example.releaseautomation.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultrequest.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.util.logging.Logger

class DeepSeekClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.deepseek.com"
) {
    private val logger = Logger.getLogger(DeepSeekClient::class.java.name)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
        defaultRequest {
            url(baseUrl)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
        engine {
            requestTimeout = 120_000
        }
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
        
        val userPrompt = """
            Контекст проекта из RAG:
            $ragContext
            
            Список коммитов:
            ${commits.joinToString("\n") { "- $it" }}
            
            Git diff изменений:
            $gitDiff
            
            Проанализируй изменения и верни JSON с результатами анализа.
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
            
            val response = client.post("/v1/chat/completions") {
                setBody(request)
            }.body<ChatResponse>()
            
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
        
        return retryWithBackoff(maxRetries = 3) { attempt ->
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
            
            val response = client.post("/v1/chat/completions") {
                setBody(request)
            }.body<ChatResponse>()
            
            val content = response.choices?.firstOrNull()?.message?.content
                ?: return CodeReviewResult(issues = emptyList())
            
            val jsonContent = extractJsonFromResponse(content)
            
            try {
                Json.decodeFromString<CodeReviewResult>(jsonContent)
            } catch (e: Exception) {
                logger.warning("Failed to parse code review JSON: ${e.message}")
                CodeReviewResult(issues = emptyList())
            }
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
