package com.example.ollamamcpserver

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
import java.util.logging.Logger
import java.util.logging.Level
import java.util.concurrent.TimeUnit

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

fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8086
    val ollamaUrl = args.getOrNull(1) ?: "http://localhost:11434"
    val projectHelperUrl = args.getOrNull(2) ?: "http://localhost:8081"
    
    val logger = Logger.getLogger("OllamaMcpServer")
    logger.log(Level.INFO, "Starting Ollama MCP Server...")
    logger.log(Level.INFO, "Port: $port")
    logger.log(Level.INFO, "Ollama URL: $ollamaUrl")
    logger.log(Level.INFO, "Project Helper URL: $projectHelperUrl")
    
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
        logger.log(Level.WARNING, "⚠️ Ollama MCP Server will not work without Ollama. Please start Ollama server.")
    }
    
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    suspend fun callProjectHelper(method: String, params: JsonObject? = null): JsonObject? {
        return try {
            val requestBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", method)
                if (params != null) {
                    put("params", params)
                }
            }
            
            logger.log(Level.INFO, "Calling Project Helper: method=$method, params=${params?.toString()?.take(200)}")
            
            val request = Request.Builder()
                .url("$projectHelperUrl/mcp")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val jsonResponse = json.parseToJsonElement(body).jsonObject
                
                // Проверяем наличие ошибки
                val error = jsonResponse["error"]?.jsonObject
                if (error != null) {
                    val errorMessage = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    logger.log(Level.WARNING, "Project Helper returned error: $errorMessage")
                    return null
                }
                
                val result = jsonResponse["result"]?.jsonObject
                logger.log(Level.INFO, "✅ Project Helper response received: ${result?.keys?.joinToString()}")
                result
            } else {
                logger.log(Level.WARNING, "Project Helper request failed: ${response.code}, body: ${body.take(500)}")
                null
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error calling Project Helper: ${e.message}", e)
            null
        }
    }
    
    suspend fun isProjectRelatedQuery(query: String): Boolean {
        val projectKeywords = listOf(
            "проект", "код", "файл", "класс", "метод", "функция", "компонент",
            "project", "code", "file", "class", "method", "function", "component",
            "ошибка", "проблема", "баг", "error", "bug", "issue",
            "как работает", "что делает", "где находится", "how does", "what does", "where is"
        )
        val lowerQuery = query.lowercase()
        return projectKeywords.any { keyword -> lowerQuery.contains(keyword) }
    }
    
    suspend fun getProjectContext(query: String): String? {
        return try {
            logger.log(Level.INFO, "🔍 Getting project context for query: ${query.take(100)}...")
            
            // Пытаемся проиндексировать файлы, если они еще не проиндексированы
            // Project Helper вернет ошибку, если файлы уже проиндексированы, но это нормально
            logger.log(Level.INFO, "📚 Checking/Indexing project files...")
            val indexParams = buildJsonObject {
                put("name", "index_project_files")
                putJsonObject("arguments") {
                    // Пустые аргументы
                }
            }
            val indexResponse = callProjectHelper("tools/call", indexParams)
            if (indexResponse != null) {
                logger.log(Level.INFO, "✅ Project files indexed successfully")
            } else {
                logger.log(Level.INFO, "ℹ️ Project files may already be indexed or indexing failed (will try search anyway)")
            }
            
            // Теперь выполняем поиск
            val searchParams = buildJsonObject {
                put("name", "search_project_files")
                putJsonObject("arguments") {
                    put("query", query)
                    put("reranking_enabled", true)
                }
            }
            
            val result = callProjectHelper("tools/call", searchParams)
            if (result != null) {
                val content = result["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                if (content != null && content.isNotBlank()) {
                    logger.log(Level.INFO, "✅ Got project context (${content.length} chars)")
                    return content
                } else {
                    logger.log(Level.WARNING, "⚠️ Project context is empty")
                    return null
                }
            } else {
                logger.log(Level.WARNING, "❌ No project context found - Project Helper returned null")
                return null
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "❌ Error getting project context: ${e.message}", e)
            null
        }
    }
    
    suspend fun callOllamaChat(messages: List<JsonObject>, model: String = "llama3.2:1b"): String? {
        return try {
            val requestBody = buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    messages.forEach { message ->
                        add(message)
                    }
                }
                put("stream", false)
            }
            
            val request = Request.Builder()
                .url("$ollamaUrl/api/chat")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val lines = body.lines().filter { it.startsWith("{") }
                val lastLine = lines.lastOrNull() ?: body
                
                val jsonResponse = try {
                    json.parseToJsonElement(lastLine).jsonObject
                } catch (e: Exception) {
                    val jsonMatch = Regex("\\{[^}]*\"message\"[^}]*\\}").find(lastLine)
                    jsonMatch?.value?.let { json.parseToJsonElement(it).jsonObject } ?: return null
                }
                
                val content = jsonResponse["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                logger.log(Level.INFO, "✅ Got response from Ollama (${content?.length ?: 0} chars)")
                content
            } else {
                logger.log(Level.WARNING, "Ollama chat request failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error calling Ollama chat: ${e.message}")
            null
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
                                        put("name", "ollama-mcp-server")
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
                                            put("name", "chat_with_llama")
                                            put("description", "Chat with Ollama Llama 3.2b model. If query is about project, automatically uses Project Helper and Vector Search for context.")
                                            putJsonObject("inputSchema") {
                                                put("type", "object")
                                                putJsonObject("properties") {
                                                    putJsonObject("query") {
                                                        put("type", "string")
                                                        put("description", "User query or question")
                                                    }
                                                    putJsonObject("use_project_context") {
                                                        put("type", "boolean")
                                                        put("description", "Force use project context (auto-detected if query is project-related)")
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
                                "chat_with_llama" -> {
                                    val query = arguments?.get("query")?.jsonPrimitive?.content
                                    val useProjectContext = arguments?.get("use_project_context")?.jsonPrimitive?.booleanOrNull
                                    
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
                                            logger.log(Level.INFO, "Processing chat request: ${query.take(100)}...")
                                            logger.log(Level.INFO, "📋 use_project_context parameter: $useProjectContext")
                                            
                                            val shouldUseProjectContext = useProjectContext ?: isProjectRelatedQuery(query)
                                            logger.log(Level.INFO, "✅ Final decision - shouldUseProjectContext: $shouldUseProjectContext")
                                            var projectContext: String? = null
                                            
                                            if (shouldUseProjectContext) {
                                                logger.log(Level.INFO, "🔍 Query is project-related, fetching context from Project Helper...")
                                                projectContext = getProjectContext(query)
                                                
                                                if (projectContext == null) {
                                                    logger.log(Level.WARNING, "⚠️ Failed to get project context. Will proceed without context.")
                                                } else {
                                                    logger.log(Level.INFO, "✅ Project context retrieved successfully (${projectContext.length} chars)")
                                                }
                                            } else {
                                                logger.log(Level.INFO, "ℹ️ Query is not project-related, skipping context fetch")
                                            }
                                            
                                            val messages = buildList {
                                                if (projectContext != null && projectContext.isNotBlank()) {
                                                    logger.log(Level.INFO, "📝 Building system prompt with project context...")
                                                    add(buildJsonObject {
                                                        put("role", "system")
                                                        put("content", buildString {
                                                            appendLine("Ты - аналитик кодовой базы проекта. Твоя задача - анализировать код проекта и отвечать на вопросы пользователя.")
                                                            appendLine()
                                                            appendLine("Ты получил релевантную информацию из проекта через Vector Search и Project Helper:")
                                                            appendLine()
                                                            appendLine("=== КОНТЕКСТ ИЗ ПРОЕКТА ===")
                                                            appendLine(projectContext)
                                                            appendLine("=== КОНЕЦ КОНТЕКСТА ===")
                                                            appendLine()
                                                            appendLine("ВАЖНО:")
                                                            appendLine("- Используй ТОЛЬКО информацию из предоставленного контекста для ответа")
                                                            appendLine("- Если в контексте нет полной информации, честно укажи это")
                                                            appendLine("- Проверь, встречаются ли подобные проблемы или вопросы еще где-то в проекте (на основе контекста)")
                                                            appendLine("- Отвечай подробно и структурированно")
                                                            appendLine("- Указывай источники информации (файлы) из контекста")
                                                        })
                                                    })
                                                } else {
                                                    logger.log(Level.INFO, "⚠️ No project context available, using general mode")
                                                    add(buildJsonObject {
                                                        put("role", "system")
                                                        put("content", "Ты - помощник. Отвечай на вопросы пользователя. Если вопрос о проекте, но у тебя нет информации, честно скажи об этом.")
                                                    })
                                                }
                                                add(buildJsonObject {
                                                    put("role", "user")
                                                    put("content", query)
                                                })
                                            }
                                            
                                            logger.log(Level.INFO, "🤖 Sending request to Ollama Llama 3.2b...")
                                            val responseText = callOllamaChat(messages, "llama3.2:1b")
                                            
                                            if (responseText != null) {
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    result = buildJsonObject {
                                                        putJsonArray("content") {
                                                            addJsonObject {
                                                                put("type", "text")
                                                                put("text", responseText)
                                                            }
                                                        }
                                                    }
                                                )
                                            } else {
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    error = JsonRpcError(
                                                        code = -32603,
                                                        message = "Failed to get response from Ollama"
                                                    )
                                                )
                                            }
                                        } catch (e: Exception) {
                                            logger.log(Level.SEVERE, "Error processing chat request", e)
                                            JsonRpcResponse(
                                                id = request.id,
                                                error = JsonRpcError(
                                                    code = -32603,
                                                    message = "Error processing chat: ${e.message}"
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
        logger.log(Level.INFO, "MCP Ollama Server starting on port $port...")
        val serverInstance = server.start(wait = false)
        logger.log(Level.INFO, "MCP Ollama Server started successfully on port $port")
        logger.log(Level.INFO, "MCP endpoint available at: http://0.0.0.0:$port/mcp")
        logger.log(Level.INFO, "For Android emulator use: http://10.0.2.2:$port/mcp")
        
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.log(Level.INFO, "Shutting down MCP Ollama Server...")
            serverInstance.stop(1000, 2000)
        })
        
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Failed to start server", e)
        e.printStackTrace()
        System.exit(1)
    }
}
