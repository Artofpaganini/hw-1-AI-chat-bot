package com.example.userformatmcpserver

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
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
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8085
    val ollamaUrl = args.getOrNull(1) ?: "http://localhost:11434"
    
    val logger = Logger.getLogger("UserFormatMcpServer")
    logger.log(Level.INFO, "Starting User Format MCP Server...")
    logger.log(Level.INFO, "Port: $port")
    logger.log(Level.INFO, "Ollama URL: $ollamaUrl")
    
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    val server = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) {
            json(json)
        }
        
        install(CORS) {
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowHeader("Content-Type")
            anyHost()
        }
        
        routing {
            post("/mcp") {
                try {
                    val rawBody = call.receiveText()
                    logger.log(Level.INFO, "Received raw request: $rawBody")
                    
                    val request = try {
                        json.decodeFromString<JsonRpcRequest>(rawBody)
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Failed to parse JSON, trying alternative parsing: ${e.message}")
                        // Fallback parsing для совместимости с Android Gson
                        val jsonElement = json.parseToJsonElement(rawBody)
                        val jsonObj = jsonElement.jsonObject
                        JsonRpcRequest(
                            jsonrpc = jsonObj["jsonrpc"]?.jsonPrimitive?.content ?: "2.0",
                            id = jsonObj["id"]?.jsonPrimitive?.intOrNull,
                            method = jsonObj["method"]?.jsonPrimitive?.content ?: "",
                            params = jsonObj["params"]?.jsonObject
                        )
                    }
                    
                    logger.log(Level.INFO, "Received request: method=${request.method}, id=${request.id}")
                    
                    val response = when (request.method) {
                        "initialize" -> {
                            JsonRpcResponse(
                                id = request.id,
                                result = buildJsonObject {
                                    put("protocolVersion", "2024-11-05")
                                    putJsonObject("serverInfo") {
                                        put("name", "user-format-mcp-server")
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
                                            put("name", "format_response")
                                            put("description", "Format AI response based on user type (child, programmer, housewife, etc.)")
                                            putJsonObject("inputSchema") {
                                                put("type", "object")
                                                putJsonObject("properties") {
                                                    putJsonObject("response") {
                                                        put("type", "string")
                                                        put("description", "Original AI response to format")
                                                    }
                                                    putJsonObject("userFormatType") {
                                                        put("type", "string")
                                                        put("description", "User format type (e.g., 'Ребенок 8 лет', 'программист', 'домохозяйка')")
                                                    }
                                                    putJsonObject("context") {
                                                        put("type", "string")
                                                        put("description", "Additional context about the question")
                                                    }
                                                }
                                                putJsonArray("required") {
                                                    add("response")
                                                    add("userFormatType")
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        "tools/call" -> {
                            val paramsObj = request.params
                            val toolName = paramsObj?.get("name")?.jsonPrimitive?.content
                            
                            val argumentsObj = paramsObj?.get("arguments")?.jsonObject
                            
                            when (toolName) {
                                "format_response" -> {
                                    val responseText = argumentsObj?.get("response")?.jsonPrimitive?.content ?: ""
                                    val userFormatType = argumentsObj?.get("userFormatType")?.jsonPrimitive?.content ?: "программист"
                                    val context = argumentsObj?.get("context")?.jsonPrimitive?.content ?: ""
                                    
                                    logger.log(Level.INFO, "Formatting response for user type: $userFormatType")
                                    
                                    try {
                                        val formattedResponse = formatResponseForUser(
                                            responseText = responseText,
                                            userFormatType = userFormatType,
                                            context = context,
                                            ollamaUrl = ollamaUrl,
                                            logger = logger,
                                            json = json
                                        )
                                        
                                        JsonRpcResponse(
                                            id = request.id,
                                            result = buildJsonObject {
                                                put("formattedResponse", formattedResponse)
                                            }
                                        )
                                    } catch (e: Exception) {
                                        logger.log(Level.SEVERE, "Error formatting response", e)
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32603,
                                                message = "Error formatting response: ${e.message}"
                                            )
                                        )
                                    }
                                }
                                else -> {
                                    JsonRpcResponse(
                                        id = request.id,
                                        error = JsonRpcError(
                                            code = -32601,
                                            message = "Method not found: $toolName"
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
        logger.log(Level.INFO, "User Format MCP Server starting on port $port...")
        val serverInstance = server.start(wait = false)
        logger.log(Level.INFO, "User Format MCP Server started successfully on port $port")
        logger.log(Level.INFO, "MCP endpoint available at: http://0.0.0.0:$port/mcp")
        logger.log(Level.INFO, "For Android emulator use: http://10.0.2.2:$port/mcp")
        
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.log(Level.INFO, "Shutting down User Format MCP Server...")
            serverInstance.stop(1000, 2000)
        })
        
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Failed to start server", e)
        System.exit(1)
    }
}

fun formatResponseForUser(
    responseText: String,
    userFormatType: String,
    context: String,
    ollamaUrl: String,
    logger: Logger,
    json: Json
): String {
    val systemPrompt = buildString {
        appendLine("Ты помощник, который адаптирует технические ответы под конкретный тип пользователя.")
        appendLine()
        appendLine("Тип пользователя: $userFormatType")
        appendLine()
        when {
            userFormatType.contains("ребенок", ignoreCase = true) || 
            userFormatType.contains("child", ignoreCase = true) -> {
                appendLine("Правила адаптации для ребенка:")
                appendLine("- Используй простые слова и короткие предложения")
                appendLine("- Избегай технических терминов, объясняй простыми словами")
                appendLine("- Используй примеры и аналогии")
                appendLine("- Будь дружелюбным и понятным")
                appendLine("- Ответ должен быть не более 10 предложений")
            }
            userFormatType.contains("домохозяйка", ignoreCase = true) || 
            userFormatType.contains("housewife", ignoreCase = true) -> {
                appendLine("Правила адаптации для домохозяйки:")
                appendLine("- Используй простой и понятный язык")
                appendLine("- Избегай сложных технических терминов")
                appendLine("- Объясняй через бытовые аналогии")
                appendLine("- Будь вежливой и понятной")
                appendLine("- Ответ должен быть не более 10 предложений")
            }
            userFormatType.contains("программист", ignoreCase = true) || 
            userFormatType.contains("programmer", ignoreCase = true) -> {
                appendLine("Правила адаптации для программиста:")
                appendLine("- Можешь использовать технические термины")
                appendLine("- Будь точным и конкретным")
                appendLine("- Можешь упоминать конкретные технологии и подходы")
                appendLine("- Ответ должен быть не более 10 предложений")
            }
            else -> {
                appendLine("Правила адаптации:")
                appendLine("- Адаптируй ответ под указанный тип пользователя: $userFormatType")
                appendLine("- Используй язык, понятный для этого типа пользователя")
                appendLine("- Избегай излишней техничности, если пользователь не технический специалист")
                appendLine("- Ответ должен быть не более 10 предложений")
            }
        }
        appendLine()
        appendLine("Контекст вопроса: $context")
        appendLine()
        appendLine("Оригинальный ответ AI:")
        appendLine(responseText)
        appendLine()
        appendLine("Задача: Адаптируй этот ответ под указанный тип пользователя, сохраняя основную информацию, но изменив стиль и сложность изложения. Ответ должен быть сжатым (не более 10 предложений).")
    }
    
    val requestBody = buildJsonObject {
        put("model", "phi3:medium")
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            }
            addJsonObject {
                put("role", "user")
                put("content", "Адаптируй ответ под тип пользователя: $userFormatType. Ответ должен быть не более 10 предложений.")
            }
        }
        put("stream", false)
    }.toString()
    
    return try {
        val httpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val request = okhttp3.Request.Builder()
            .url("$ollamaUrl/api/chat")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val httpResponse = httpClient.newCall(request).execute()
        if (httpResponse.isSuccessful) {
            val body = httpResponse.body?.string() ?: ""
            val lines = body.lines().filter { it.startsWith("{") }
            val lastLine = lines.lastOrNull() ?: body
            
            val jsonResponse = try {
                json.parseToJsonElement(lastLine).jsonObject
            } catch (e: Exception) {
                val jsonMatch = Regex("\\{[^}]*\"message\"[^}]*\\}").find(lastLine)
                jsonMatch?.value?.let { json.parseToJsonElement(it).jsonObject } 
                    ?: return responseText.substring(0, minOf(500, responseText.length)) // Fallback: возвращаем первые 500 символов
            }
            
            val content = jsonResponse["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content 
                ?: responseText.substring(0, minOf(500, responseText.length))
            
            // Ограничиваем ответ до 10 предложений
            val sentences = content.split(Regex("[.!?]+")).filter { it.trim().isNotEmpty() }
            val limitedSentences = sentences.take(10)
            limitedSentences.joinToString(". ") + if (limitedSentences.size < sentences.size) "." else ""
        } else {
            logger.log(Level.WARNING, "Ollama API error: ${httpResponse.code}")
            responseText.substring(0, minOf(500, responseText.length)) // Fallback
        }
    } catch (e: Exception) {
        logger.log(Level.WARNING, "Error calling Ollama API: ${e.message}")
        responseText.substring(0, minOf(500, responseText.length)) // Fallback: возвращаем первые 500 символов оригинального ответа
    }
}

