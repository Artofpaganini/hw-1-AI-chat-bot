package com.example.projecthelpermcpserver

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.util.logging.Logger
import java.util.logging.Level
import kotlin.math.min
import kotlin.math.max

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int?,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int?,
    val result: JsonObject? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

data class Chunk(
    val text: String,
    val filePath: String,
    val chunkIndex: Int,
    val embedding: List<Float>? = null
)

data class IndexedChunk(
    val chunk: Chunk,
    val relevance: Float = 0f
)

fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8081
    val projectRoot = args.getOrNull(1) ?: System.getProperty("user.dir")
    val ollamaUrl = args.getOrNull(2) ?: "http://localhost:11434"
    
    val logger = Logger.getLogger("ProjectHelperMcpServer")
    logger.log(Level.INFO, "Starting Project Helper MCP Server...")
    logger.log(Level.INFO, "Port: $port")
    logger.log(Level.INFO, "Project root: $projectRoot")
    logger.log(Level.INFO, "Ollama URL: $ollamaUrl")
    
    // Проверка доступности Ollama
    // Увеличенные таймауты для работы с Ollama:
    // - connectTimeout: 60 секунд (для медленных подключений)
    // - readTimeout: 300 секунд (5 минут) для reranking, который может обрабатывать до 20 кандидатов
    //   Каждый кандидат требует запрос к LLM, который может занимать 5-10 секунд
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS) // 5 минут для reranking
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    try {
        val testRequest = Request.Builder()
            .url("$ollamaUrl/api/tags")
            .build()
        val testResponse = httpClient.newCall(testRequest).execute()
        if (testResponse.isSuccessful) {
            logger.log(Level.INFO, "✅ Ollama is available at $ollamaUrl")
        } else {
            logger.log(Level.WARNING, "⚠️ Ollama responded with error: ${testResponse.code}")
        }
    } catch (e: Exception) {
        logger.log(Level.WARNING, "❌ Cannot connect to Ollama at $ollamaUrl: ${e.message}")
        logger.log(Level.WARNING, "⚠️ Project Helper will not work without Ollama. Please start Ollama server.")
    }
    
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    // Кэш для индексированных чанков
    val indexedChunks = mutableListOf<Chunk>()
    var isIndexed = false
    
    fun findProjectFiles(root: File): List<File> {
        val files = mutableListOf<File>()
        val ignoredDirs = setOf("build", ".git", "node_modules", ".gradle", ".idea", ".cursormcp", ".kotlin")
        val supportedExtensions = setOf(".kt", ".xml", ".java", ".kts", ".sh")
        
        fun walkDir(dir: File) {
            if (!dir.exists() || !dir.isDirectory) return
            
            if (ignoredDirs.contains(dir.name)) return
            
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    walkDir(file)
                } else {
                    val extension = file.extension.lowercase()
                    if (supportedExtensions.any { ext -> extension == ext.removePrefix(".") }) {
                        files.add(file)
                    }
                }
            }
        }
        
        walkDir(root)
        return files
    }
    
    fun chunkText(text: String, chunkSize: Int = 300, overlap: Int = 30): List<String> {
        val words = text.split(Regex("\\s+"))
        val chunks = mutableListOf<String>()
        var i = 0
        
        while (i < words.size) {
            val chunkWords = words.subList(i, min(i + chunkSize, words.size))
            chunks.add(chunkWords.joinToString(" "))
            i += chunkSize - overlap
        }
        
        return chunks
    }
    
    suspend fun generateEmbedding(text: String, ollamaUrl: String): List<Float>? {
        val limitedText = text.take(2000) // Ограничиваем до 2000 символов
        
        val requestBody = buildJsonObject {
            put("model", "nomic-embed-text")
            put("prompt", limitedText)
        }.toString()
        
        val request = Request.Builder()
            .url("$ollamaUrl/api/embeddings")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val jsonResponse = json.parseToJsonElement(body).jsonObject
                val embedding = jsonResponse["embedding"]?.jsonArray?.mapNotNull { 
                    it.jsonPrimitive.floatOrNull 
                } ?: return null
                embedding
            } else {
                null
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error generating embedding: ${e.message}")
            null
        }
    }
    
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        return if (normA > 0f && normB > 0f) {
            dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
        } else {
            0f
        }
    }
    
    suspend fun performReranking(
        query: String,
        candidateChunks: List<Chunk>,
        ollamaUrl: String
    ): List<IndexedChunk> {
        logger.log(Level.INFO, "Starting reranking for ${candidateChunks.size} candidates...")
        val reranked = candidateChunks.mapIndexed { index, chunk ->
            if ((index + 1) % 5 == 0 || index == 0) {
                logger.log(Level.INFO, "Reranking progress: ${index + 1}/${candidateChunks.size} candidates processed")
            }
            // Определяем источник: если файл существует, берем имя, иначе "Неизвестный источник"
            val source = try {
                val file = File(chunk.filePath)
                if (file.exists() && file.name.isNotBlank()) {
                    file.name
                } else {
                    "Неизвестный источник"
                }
            } catch (e: Exception) {
                "Неизвестный источник"
            }
            
            val prompt = buildString {
                appendLine("Оцени релевантность текста запросу по шкале от 0.0 до 1.0.")
                appendLine("Запрос: \"$query\"")
                appendLine("Источник: \"$source\"")
                appendLine("Текст: \"${chunk.text.take(800)}\"")
                appendLine("Ответь Название источника(файла) + текст + релевантность текста в виде \"Релевантность число\". Никаких пояснений.")
            }
            
            val requestBody = buildJsonObject {
                put("model", "phi3:medium")
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    }
                }
                put("stream", false)
            }.toString()
            
            val request = Request.Builder()
                .url("$ollamaUrl/api/chat")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val relevance = try {
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    // Парсим streaming JSON ответы
                    val lines = body.lines().filter { it.startsWith("{") }
                    val lastLine = lines.lastOrNull() ?: body
                    
                    val jsonResponse = try {
                        json.parseToJsonElement(lastLine).jsonObject
                    } catch (e: Exception) {
                        // Fallback: пытаемся найти JSON объект в тексте
                        val jsonMatch = Regex("\\{[^}]*\"message\"[^}]*\\}").find(lastLine)
                        jsonMatch?.value?.let { 
                            try {
                                json.parseToJsonElement(it).jsonObject
                            } catch (e2: Exception) {
                                logger.log(Level.WARNING, "Failed to parse JSON response for chunk ${index + 1}, using default relevance 0.5")
                                null
                            }
                        } ?: run {
                            logger.log(Level.WARNING, "Failed to parse JSON response for chunk ${index + 1}, using default relevance 0.5")
                            null
                        }
                    }
                    
                    if (jsonResponse == null) {
                        0.5f
                    } else {
                        val content = jsonResponse["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
                    
                        // Парсим релевантность
                        val relevanceRegex = Regex("Релевантность\\s*([0-9.]+)|([0-9.]+)")
                        val match = relevanceRegex.find(content)
                        val score = match?.let {
                            (it.groupValues[1].takeIf { it.isNotBlank() } ?: it.groupValues[2]).toFloatOrNull()?.coerceIn(0f, 1f)
                        } ?: 0.5f
                        
                        score
                    }
                } else {
                    0.5f
                }
            } catch (e: java.net.SocketTimeoutException) {
                logger.log(Level.WARNING, "Timeout in reranking for chunk ${index + 1}/${candidateChunks.size}: ${e.message}")
                logger.log(Level.WARNING, "Consider increasing readTimeout or reducing number of candidates")
                0.5f // Возвращаем среднюю релевантность при таймауте
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error in reranking for chunk ${index + 1}/${candidateChunks.size}: ${e.message}")
                0.5f
            }
            
            IndexedChunk(chunk, relevance)
        }
        
        logger.log(Level.INFO, "Reranking completed. Sorting results by relevance...")
        val sorted = reranked.sortedByDescending { it.relevance }
        logger.log(Level.INFO, "Top 3 relevance scores: ${sorted.take(3).map { String.format("%.2f", it.relevance) }.joinToString(", ")}")
        return sorted
    }
    
    suspend fun generateSummaryFromChunks(
        query: String,
        topChunks: List<IndexedChunk>,
        ollamaUrl: String
    ): String {
        // Формируем контекст из топ-10 чанков
        val context = topChunks.mapIndexed { index, indexedChunk ->
            val source = File(indexedChunk.chunk.filePath).name
            val relevance = String.format("%.2f", indexedChunk.relevance)
            val text = indexedChunk.chunk.text.take(500) // Ограничиваем длину каждого чанка
            "[$index] Источник: $source, Релевантность: $relevance\nТекст: $text"
        }.joinToString("\n\n")
        
        val prompt = buildString {
            appendLine("На основании следующих релевантных фрагментов кода/документации ответь на вопрос пользователя.")
            appendLine("Вопрос: \"$query\"")
            appendLine()
            appendLine("Релевантные фрагменты (отсортированы по релевантности, от наиболее релевантных к менее релевантным):")
            appendLine(context)
            appendLine()
            appendLine("Требования к ответу:")
            appendLine("- Ответ должен быть КРАТКИМ и структурированным")
            appendLine("- Изложи ответ ПО ПУНКТАМ (используй нумерацию или маркеры)")
            appendLine("- Используй только информацию из предоставленных фрагментов")
            appendLine("- Не добавляй пояснений, только факты из фрагментов")
            appendLine("- В конце ответа укажи источники (имена файлов) и их релевантность в формате: \"Источник: [имя файла], Релевантность: [число]\"")
        }
        
        val requestBody = buildJsonObject {
            put("model", "phi3:medium")
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            put("stream", false)
        }.toString()
        
        val request = Request.Builder()
            .url("$ollamaUrl/api/chat")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                // Парсим streaming JSON ответы
                val lines = body.lines().filter { it.startsWith("{") }
                val lastLine = lines.lastOrNull() ?: body
                
                val jsonResponse = try {
                    json.parseToJsonElement(lastLine).jsonObject
                } catch (e: Exception) {
                    // Fallback: пытаемся найти JSON объект в тексте
                    val jsonMatch = Regex("\\{[^}]*\"message\"[^}]*\\}").find(lastLine)
                    jsonMatch?.value?.let { json.parseToJsonElement(it).jsonObject } ?: return "Ошибка парсинга ответа от LLM"
                }
                
                val content = jsonResponse["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content ?: "Не удалось получить ответ"
                
                // Добавляем информацию об источниках в конец ответа
                val sourcesInfo = buildString {
                    appendLine()
                    appendLine("Источники:")
                    topChunks.take(10).forEach { indexedChunk ->
                        val source = File(indexedChunk.chunk.filePath).name
                        val relevance = String.format("%.2f", indexedChunk.relevance)
                        appendLine("- $source (Релевантность: $relevance)")
                    }
                }
                
                content + sourcesInfo
            } else {
                "Ошибка при генерации ответа: ${response.code}"
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error generating summary: ${e.message}")
            "Ошибка при генерации ответа: ${e.message}"
        }
    }
    
    val server = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) {
            json(json)
        }
        
        install(CORS) {
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowHeader("Content-Type")
            allowHeader("Authorization")
            allowHeader("mcp-session-id")
            anyHost()
        }
        
        routing {
            post("/mcp") {
                try {
                    val request = call.receive<JsonRpcRequest>()
                    
                    logger.log(Level.INFO, "Received request: method=${request.method}, id=${request.id}")
                    
                    val response = when (request.method) {
                        "initialize" -> {
                            JsonRpcResponse(
                                id = request.id,
                                result = buildJsonObject {
                                    put("protocolVersion", "2024-11-05")
                                    putJsonObject("serverInfo") {
                                        put("name", "project-helper-mcp-server")
                                        put("version", "1.0.0")
                                    }
                                    putJsonObject("capabilities") {
                                        putJsonObject("tools") {
                                            put("listChanged", true)
                                        }
                                    }
                                }
                            )
                        }
                        "tools/list" -> {
                            JsonRpcResponse(
                                id = request.id,
                                result = buildJsonObject {
                                    putJsonArray("tools") {
                                        addJsonObject {
                                            put("name", "index_project_files")
                                            put("description", "Index all project files (.kt, .xml, .java, .kts, .sh) in the project")
                                        }
                                        addJsonObject {
                                            put("name", "search_project_files")
                                            put("description", "Search for relevant information in project files")
                                            putJsonObject("inputSchema") {
                                                put("type", "object")
                                                putJsonObject("properties") {
                                                    putJsonObject("query") {
                                                        put("type", "string")
                                                        put("description", "Search query")
                                                    }
                                                    putJsonObject("reranking_enabled") {
                                                        put("type", "boolean")
                                                        put("description", "Enable reranking")
                                                    }
                                                }
                                                putJsonArray("required") {
                                                    add("query")
                                                }
                                            }
                                        }
                                        addJsonObject {
                                            put("name", "execute_shell_command")
                                            put("description", "Execute shell command on host machine (for project operations)")
                                            putJsonObject("inputSchema") {
                                                put("type", "object")
                                                putJsonObject("properties") {
                                                    putJsonObject("command") {
                                                        put("type", "string")
                                                        put("description", "Shell command to execute")
                                                    }
                                                    putJsonObject("working_directory") {
                                                        put("type", "string")
                                                        put("description", "Working directory for command execution")
                                                    }
                                                }
                                                putJsonArray("required") {
                                                    add("command")
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        "tools/call" -> {
                            val toolName = request.params?.get("name")?.jsonPrimitive?.content
                            val arguments = request.params?.get("arguments")?.jsonObject
                            
                            when (toolName) {
                                "index_project_files" -> {
                                    logger.log(Level.INFO, "Starting project files indexing...")
                                    
                                    try {
                                        val projectDir = File(projectRoot)
                                        val projectFiles = findProjectFiles(projectDir)
                                        
                                        logger.log(Level.INFO, "Found ${projectFiles.size} project files (.kt, .xml, .java, .kts, .sh)")
                                        
                                        indexedChunks.clear()
                                        
                                        projectFiles.forEachIndexed { fileIndex, file ->
                                            try {
                                                val content = file.readText(Charsets.UTF_8)
                                                val chunks = chunkText(content)
                                                
                                                chunks.forEachIndexed { chunkIndex, chunkText ->
                                                    val chunk = Chunk(
                                                        text = chunkText,
                                                        filePath = file.absolutePath,
                                                        chunkIndex = chunkIndex
                                                    )
                                                    indexedChunks.add(chunk)
                                                }
                                                
                                                logger.log(Level.INFO, "Indexed ${chunks.size} chunks from ${file.name}")
                                            } catch (e: Exception) {
                                                logger.log(Level.WARNING, "Error reading file ${file.name}: ${e.message}")
                                            }
                                        }
                                        
                                        // Генерируем embeddings для всех чанков
                                        logger.log(Level.INFO, "Generating embeddings for ${indexedChunks.size} chunks...")
                                        
                                        indexedChunks.forEachIndexed { index, chunk ->
                                            val embedding = generateEmbedding(chunk.text, ollamaUrl)
                                            if (embedding != null) {
                                                indexedChunks[index] = chunk.copy(embedding = embedding)
                                            }
                                            if ((index + 1) % 10 == 0) {
                                                logger.log(Level.INFO, "Generated embeddings for ${index + 1}/${indexedChunks.size} chunks")
                                            }
                                        }
                                        
                                        isIndexed = true
                                        
                                        logger.log(Level.INFO, "✅ Indexing completed: ${indexedChunks.size} chunks indexed")
                                        
                                        JsonRpcResponse(
                                            id = request.id,
                                            result = buildJsonObject {
                                                putJsonArray("content") {
                                                    addJsonObject {
                                                        put("type", "text")
                                                        put("text", "Successfully indexed ${projectFiles.size} files (.kt, .xml, .java, .kts, .sh) with ${indexedChunks.size} chunks")
                                                    }
                                                }
                                            }
                                        )
                                    } catch (e: Exception) {
                                        logger.log(Level.SEVERE, "Error indexing files", e)
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32603,
                                                message = "Error indexing files: ${e.message}"
                                            )
                                        )
                                    }
                                }
                                "search_project_files" -> {
                                    if (!isIndexed || indexedChunks.isEmpty()) {
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32602,
                                                message = "Project files not indexed. Please call index_project_files first."
                                            )
                                        )
                                    } else {
                                        val query = arguments?.get("query")?.jsonPrimitive?.content
                                        val rerankingEnabled = arguments?.get("reranking_enabled")?.jsonPrimitive?.booleanOrNull ?: false
                                        
                                        if (query.isNullOrBlank()) {
                                            JsonRpcResponse(
                                                id = request.id,
                                                error = JsonRpcError(
                                                    code = -32602,
                                                    message = "query parameter is required"
                                                )
                                            )
                                        } else {
                                            try {
                                                // Генерируем embedding для запроса
                                                val queryEmbedding = generateEmbedding(query, ollamaUrl)
                                                
                                                if (queryEmbedding == null) {
                                                    JsonRpcResponse(
                                                        id = request.id,
                                                        error = JsonRpcError(
                                                            code = -32603,
                                                            message = "Failed to generate query embedding"
                                                        )
                                                    )
                                                } else {
                                                    // Находим релевантные чанки по косинусному сходству
                                                    // Берем больше кандидатов для reranking (до 20), чтобы после reranking выбрать топ-10
                                                    val initialCandidates = indexedChunks
                                                        .filter { it.embedding != null }
                                                        .map { chunk ->
                                                            val similarity = cosineSimilarity(queryEmbedding, chunk.embedding!!)
                                                            IndexedChunk(chunk, similarity)
                                                        }
                                                        .sortedByDescending { it.relevance }
                                                        .take(if (rerankingEnabled) 20 else 10) // Для reranking берем больше кандидатов
                                                    
                                                    val finalChunks = if (rerankingEnabled && initialCandidates.isNotEmpty()) {
                                                        logger.log(Level.INFO, "Performing reranking for ${initialCandidates.size} candidates...")
                                                        // Выполняем reranking для всех кандидатов
                                                        val reranked = performReranking(query, initialCandidates.map { it.chunk }, ollamaUrl)
                                                        // После reranking берем топ-10 самых релевантных (отсортированы по убыванию, где 1.0 - максимальная релевантность)
                                                        reranked.take(10)
                                                    } else {
                                                        // Без reranking используем топ-10 по косинусному сходству
                                                        initialCandidates.take(10)
                                                    }
                                                    
                                                    // Топ-10 самых релевантных чанков (отсортированы по убыванию, где 1.0 - максимальная релевантность)
                                                    val topChunks = finalChunks
                                                    
                                                    if (topChunks.isNotEmpty()) {
                                                        logger.log(Level.INFO, "✅ Selected ${topChunks.size} top chunks for response generation")
                                                        
                                                        // Формируем краткий ответ на основе топ-10 чанков
                                                        val result = if (rerankingEnabled) {
                                                            // При reranking используем LLM для генерации краткого ответа по пунктам
                                                            generateSummaryFromChunks(query, topChunks, ollamaUrl)
                                                        } else {
                                                            // Без reranking просто объединяем информацию из чанков
                                                            buildString {
                                                                topChunks.forEachIndexed { index, indexedChunk ->
                                                                    val source = File(indexedChunk.chunk.filePath).name
                                                                    val relevance = indexedChunk.relevance
                                                                    appendLine("${index + 1}. [Источник: $source, Релевантность: ${String.format("%.2f", relevance)}]")
                                                                    val textSentences = indexedChunk.chunk.text.split(Regex("[.!?]+")).filter { it.trim().isNotBlank() }
                                                                    val limitedText = textSentences.take(3).joinToString(". ")
                                                                    appendLine("   $limitedText")
                                                                    appendLine()
                                                                }
                                                            }
                                                        }
                                                        
                                                        JsonRpcResponse(
                                                            id = request.id,
                                                            result = buildJsonObject {
                                                                putJsonArray("content") {
                                                                    addJsonObject {
                                                                        put("type", "text")
                                                                        put("text", result)
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    } else {
                                                        JsonRpcResponse(
                                                            id = request.id,
                                                            result = buildJsonObject {
                                                                putJsonArray("content") {
                                                                    addJsonObject {
                                                                        put("type", "text")
                                                                        put("text", "No relevant information found")
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                logger.log(Level.SEVERE, "Error searching files", e)
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    error = JsonRpcError(
                                                        code = -32603,
                                                        message = "Error searching files: ${e.message}"
                                                    )
                                                )
                                            }
                                        }
                                        "execute_shell_command" -> {
                                            val command = arguments?.get("command")?.jsonPrimitive?.content
                                            val workingDir = arguments?.get("working_directory")?.jsonPrimitive?.content ?: projectRoot
                                            
                                            if (command.isNullOrBlank()) {
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    error = JsonRpcError(
                                                        code = -32602,
                                                        message = "command parameter is required"
                                                    )
                                                )
                                            } else {
                                                try {
                                                    logger.log(Level.INFO, "Executing shell command: $command in directory: $workingDir")
                                                    
                                                    val processBuilder = ProcessBuilder("sh", "-c", command)
                                                    processBuilder.directory(File(workingDir))
                                                    processBuilder.redirectErrorStream(true)
                                                    
                                                    val process = processBuilder.start()
                                                    val output = process.inputStream.bufferedReader().readText()
                                                    val exitCode = process.waitFor()
                                                    
                                                    if (exitCode == 0) {
                                                        logger.log(Level.INFO, "Command executed successfully")
                                                        JsonRpcResponse(
                                                            id = request.id,
                                                            result = buildJsonObject {
                                                                put("output", output)
                                                                put("exitCode", exitCode)
                                                            }
                                                        )
                                                    } else {
                                                        logger.log(Level.WARNING, "Command failed with exit code: $exitCode")
                                                        JsonRpcResponse(
                                                            id = request.id,
                                                            result = buildJsonObject {
                                                                put("output", output)
                                                                put("exitCode", exitCode)
                                                                put("error", "Command failed with exit code $exitCode")
                                                            }
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    logger.log(Level.SEVERE, "Error executing command", e)
                                                    JsonRpcResponse(
                                                        id = request.id,
                                                        error = JsonRpcError(
                                                            code = -32603,
                                                            message = "Error executing command: ${e.message}"
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    JsonRpcResponse(
                                        id = request.id,
                                        error = JsonRpcError(
                                            code = -32601,
                                            message = "Tool not found: $toolName"
                                        )
                                    )
                                }
                            }
                        }
                        else -> {
                            JsonRpcResponse(
                                id = request.id,
                                error = JsonRpcError(
                                    code = -32601,
                                    message = "Method not found: ${request.method}"
                                )
                            )
                        }
                    }
                    
                    call.respond(response)
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Error processing request", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        JsonRpcResponse(
                            id = null,
                            error = JsonRpcError(
                                code = -32603,
                                message = "Internal error: ${e.message}"
                            )
                        )
                    )
                }
            }
        }
    }
    
    try {
        logger.log(Level.INFO, "MCP Project Helper Server starting on port $port...")
        val serverInstance = server.start(wait = false)
        logger.log(Level.INFO, "MCP Project Helper Server started successfully on port $port")
        logger.log(Level.INFO, "MCP endpoint available at: http://0.0.0.0:$port/mcp")
        logger.log(Level.INFO, "For Android emulator use: http://10.0.2.2:$port/mcp")
        
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.log(Level.INFO, "Shutting down MCP Project Helper Server...")
            serverInstance.stop(1000, 2000)
        })
        
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Failed to start server", e)
        e.printStackTrace()
        System.exit(1)
    }
}
