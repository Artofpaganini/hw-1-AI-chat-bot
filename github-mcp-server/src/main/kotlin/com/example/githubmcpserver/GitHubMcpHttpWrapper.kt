package com.example.githubmcpserver

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
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
    val logger = Logger.getLogger("GitHubMcpHttpWrapper")
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8083
    // Приоритет: GITHUB_PERSONAL_ACCESS_TOKEN > GITHUB_PAT
    val githubPat = System.getenv("GITHUB_PERSONAL_ACCESS_TOKEN") 
        ?: System.getenv("GITHUB_PAT") 
        ?: ""
    
    logger.log(Level.INFO, "Starting GitHub MCP HTTP Wrapper on port $port...")
    
    if (githubPat.isEmpty()) {
        logger.log(Level.WARNING, "⚠️  GITHUB_PERSONAL_ACCESS_TOKEN not set - server may not function properly")
        logger.log(Level.WARNING, "Set it in local.properties, environment variable, or .env file")
    } else {
        logger.log(Level.INFO, "✅ GitHub PAT is set (length: ${githubPat.length} characters)")
    }
    
    val json = Json { ignoreUnknownKeys = true }
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    val server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(ContentNegotiation) {
            json(json)
        }
        
        install(CORS) {
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowHeader("Content-Type")
            allowHeader("Authorization")
            anyHost()
        }
        
        routing {
            post("/mcp") {
                try {
                    val request = call.receive<JsonRpcRequest>()
                    logger.log(Level.INFO, "Received request: method=${request.method}, id=${request.id}")
                    
                    // Forward request to GitHub MCP Server via stdio
                    val response = forwardToGitHubMcpServer(request, githubPat, httpClient, json, logger)
                    
                    call.respond(response)
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Error processing request", e)
                    val errorResponse = JsonRpcResponse(
                        id = null,
                        error = JsonRpcError(
                            code = -32603,
                            message = "Internal error: ${e.message}"
                        )
                    )
                    call.respond(errorResponse)
                }
            }
        }
    }
    
    logger.log(Level.INFO, "GitHub MCP HTTP Wrapper started on port $port")
    logger.log(Level.INFO, "Endpoint: http://0.0.0.0:$port/mcp")
    logger.log(Level.INFO, "For Android emulator: http://10.0.2.2:$port/mcp")
    server.start(wait = true)
}

fun forwardToGitHubMcpServer(
    request: JsonRpcRequest,
    githubPat: String,
    httpClient: OkHttpClient,
    json: Json,
    logger: Logger
): JsonRpcResponse {
    return try {
        // For now, we'll use a simple approach: call GitHub API directly
        // In the future, we can wrap the stdio GitHub MCP Server
        
        // This is a placeholder - actual implementation would wrap stdio process
        // For now, return a basic response indicating the server is running
        when (request.method) {
            "initialize" -> {
                JsonRpcResponse(
                    id = request.id,
                    result = buildJsonObject {
                        put("protocolVersion", "2024-11-05")
                        putJsonObject("serverInfo") {
                            put("name", "github-mcp-server")
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
            "tools/call" -> {
                val toolName = (request.params?.get("name") as? JsonPrimitive)?.content
                val arguments = request.params?.get("arguments") as? JsonObject
                
                if (toolName == "pull_request_read" && arguments != null) {
                    val owner = (arguments["owner"] as? JsonPrimitive)?.content ?: ""
                    val repo = (arguments["repo"] as? JsonPrimitive)?.content ?: ""
                    val pullNumber = (arguments["pullNumber"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    val method = (arguments["method"] as? JsonPrimitive)?.content ?: "get"
                    
                    if (method == "get_diff") {
                        // Call GitHub API directly to get PR diff
                        val diff = getPrDiffFromGitHub(owner, repo, pullNumber, githubPat, httpClient, logger)
                        JsonRpcResponse(
                            id = request.id,
                            result = buildJsonObject {
                                put("content", diff)
                            }
                        )
                    } else {
                        JsonRpcResponse(
                            id = request.id,
                            error = JsonRpcError(
                                code = -32601,
                                message = "Method not supported: $method"
                            )
                        )
                    }
                } else if (toolName == "get_file_contents" && arguments != null) {
                    val owner = (arguments["owner"] as? JsonPrimitive)?.content ?: ""
                    val repo = (arguments["repo"] as? JsonPrimitive)?.content ?: ""
                    val path = (arguments["path"] as? JsonPrimitive)?.content ?: ""
                    val ref = (arguments["ref"] as? JsonPrimitive)?.content
                    
                    val fileContent = getFileContentsFromGitHub(owner, repo, path, ref, githubPat, httpClient, logger)
                    JsonRpcResponse(
                        id = request.id,
                        result = buildJsonObject {
                            put("content", fileContent.content)
                            put("encoding", fileContent.encoding)
                        }
                    )
                } else {
                    JsonRpcResponse(
                        id = request.id,
                        error = JsonRpcError(
                            code = -32601,
                            message = "Tool not supported: $toolName"
                        )
                    )
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
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error forwarding request", e)
        JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = -32603,
                message = "Internal error: ${e.message}"
            )
        )
    }
}

data class FileContentResult(
    val content: String,
    val encoding: String
)

fun getPrDiffFromGitHub(
    owner: String,
    repo: String,
    pullNumber: Int,
    githubPat: String,
    httpClient: OkHttpClient,
    logger: Logger
): String {
    if (githubPat.isEmpty()) {
        throw Exception("GITHUB_PERSONAL_ACCESS_TOKEN not set. Set it in local.properties, environment variable, or .env file")
    }
    
    val url = "https://api.github.com/repos/$owner/$repo/pulls/$pullNumber"
    val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $githubPat")
        .header("Accept", "application/vnd.github.v3.diff")
        .get()
        .build()
    
    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) {
        val errorBody = response.body?.string() ?: "Unknown error"
        throw Exception("GitHub API error: ${response.code} - $errorBody")
    }
    
    return response.body?.string() ?: ""
}

fun getFileContentsFromGitHub(
    owner: String,
    repo: String,
    path: String,
    ref: String?,
    githubPat: String,
    httpClient: OkHttpClient,
    logger: Logger
): FileContentResult {
    if (githubPat.isEmpty()) {
        throw Exception("GITHUB_PERSONAL_ACCESS_TOKEN not set. Set it in local.properties, environment variable, or .env file")
    }
    
    val refParam = ref?.let { "?ref=$it" } ?: ""
    val url = "https://api.github.com/repos/$owner/$repo/contents/$path$refParam"
    val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $githubPat")
        .header("Accept", "application/vnd.github.v3+json")
        .get()
        .build()
    
    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) {
        val errorBody = response.body?.string() ?: "Unknown error"
        throw Exception("GitHub API error: ${response.code} - $errorBody")
    }
    
    val responseBody = response.body?.string() ?: "{}"
    val json = Json { ignoreUnknownKeys = true }
    val jsonObject = json.parseToJsonElement(responseBody).jsonObject
    
    val content = jsonObject["content"]?.jsonPrimitive?.content ?: ""
    val encoding = jsonObject["encoding"]?.jsonPrimitive?.content ?: "base64"
    
    // Decode base64 if needed
    val decodedContent = if (encoding == "base64" && content.isNotEmpty()) {
        try {
            val decodedBytes = java.util.Base64.getDecoder().decode(content.replace("\n", ""))
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to decode base64 content", e)
            content
        }
    } else {
        content
    }
    
    return FileContentResult(
        content = decodedContent,
        encoding = encoding
    )
}
