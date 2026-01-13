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
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
        val supportedExtensions = setOf(".kt", ".xml", ".java", ".kts", ".md", ".sh")
        
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
        val reranked = candidateChunks.map { chunk ->
            val prompt = buildString {
                appendLine("Оцени релевантность текста запросу по шкале от 0.0 до 1.0.")
                appendLine("Запрос: \"$query\"")
                val source = chunk.filePath.let { File(it).name }.takeIf { it.isNotBlank() } ?: "Неизвестный источник"
                appendLine("Источник: \"$source\"")
                appendLine("Текст: \"${chunk.text.take(800)}\"")
                appendLine("Ответь Название источника + текст + релевантность текста в виде \"Релевантность число\". Никаких пояснений.")
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
                        jsonMatch?.value?.let { json.parseToJsonElement(it).jsonObject } ?: return@map IndexedChunk(chunk, 0.5f)
                    }
                    
                    val content = jsonResponse["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
                    
                    // Парсим релевантность
                    val relevanceRegex = Regex("Релевантность\\s*([0-9.]+)|([0-9.]+)")
                    val match = relevanceRegex.find(content)
                    val score = match?.let {
                        (it.groupValues[1].takeIf { it.isNotBlank() } ?: it.groupValues[2]).toFloatOrNull()?.coerceIn(0f, 1f)
                    } ?: 0.5f
                    
                    score
                } else {
                    0.5f
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error in reranking: ${e.message}")
                0.5f
            }
            
            IndexedChunk(chunk, relevance)
        }
        
        return reranked.sortedByDescending { it.relevance }
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
                                            put("description", "Index all project files (.kt, .xml, .java, .kts, .md, .sh) in the project")
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
                                        
                                        logger.log(Level.INFO, "Found ${projectFiles.size} project files (.kt, .xml, .java, .kts, .md, .sh)")
                                        
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
                                                        put("text", "Successfully indexed ${projectFiles.size} files (.kt, .xml, .java, .kts, .md, .sh) with ${indexedChunks.size} chunks")
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
                                                    val candidates = indexedChunks
                                                        .filter { it.embedding != null }
                                                        .map { chunk ->
                                                            val similarity = cosineSimilarity(queryEmbedding, chunk.embedding!!)
                                                            IndexedChunk(chunk, similarity)
                                                        }
                                                        .sortedByDescending { it.relevance }
                                                        .take(10)
                                                    
                                                    val finalChunks = if (rerankingEnabled && candidates.isNotEmpty()) {
                                                        logger.log(Level.INFO, "Performing reranking for ${candidates.size} candidates...")
                                                        performReranking(query, candidates.map { it.chunk }, ollamaUrl)
                                                    } else {
                                                        candidates
                                                    }
                                                    
                                                    val topChunk = finalChunks.firstOrNull()
                                                    
                                                    if (topChunk != null) {
                                                        val source = File(topChunk.chunk.filePath).name
                                                        val relevance = topChunk.relevance
                                                        
                                                        logger.log(Level.INFO, "✅ Top chunk selected: $source (relevance: $relevance)")
                                                        
                                                        // Ограничиваем текст до 5 предложений (но сохраняем информацию об источнике)
                                                        val textSentences = topChunk.chunk.text.split(Regex("[.!?]+")).filter { it.trim().isNotBlank() }
                                                        val limitedText = textSentences.take(5).joinToString(". ") + if (textSentences.size > 5) "..." else ""
                                                        
                                                        val result = buildString {
                                                            appendLine("Источник: $source")
                                                            appendLine("Релевантность: $relevance")
                                                            appendLine()
                                                            appendLine(limitedText)
                                                        }
                                                        
                                                        val limitedResult = result
                                                        
                                                        JsonRpcResponse(
                                                            id = request.id,
                                                            result = buildJsonObject {
                                                                putJsonArray("content") {
                                                                    addJsonObject {
                                                                        put("type", "text")
                                                                        put("text", limitedResult)
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
