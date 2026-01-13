package com.example.gitmcpserver

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
import java.io.File
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
    val logger = Logger.getLogger("GitMcpHttpWrapper")
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8084
    val projectRoot = args.getOrNull(1) ?: System.getProperty("user.dir") ?: "/"
    
    logger.log(Level.INFO, "Starting Git MCP HTTP Wrapper on port $port...")
    logger.log(Level.INFO, "Project root: $projectRoot")
    
    val json = Json { ignoreUnknownKeys = true }
    
    val server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
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
                    val request = call.receive<JsonRpcRequest>()
                    logger.log(Level.INFO, "Received request: method=${request.method}, id=${request.id}")
                    
                    val response = handleRequest(request, projectRoot, logger, json)
                    
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
    
    logger.log(Level.INFO, "Git MCP HTTP Wrapper started on port $port")
    logger.log(Level.INFO, "Endpoint: http://0.0.0.0:$port/mcp")
    logger.log(Level.INFO, "For Android emulator: http://10.0.2.2:$port/mcp")
    server.start(wait = true)
}

fun handleRequest(
    request: JsonRpcRequest,
    projectRoot: String,
    logger: Logger,
    json: Json
): JsonRpcResponse {
    return try {
        when (request.method) {
            "initialize" -> {
                JsonRpcResponse(
                    id = request.id,
                    result = buildJsonObject {
                        put("protocolVersion", "2024-11-05")
                        putJsonObject("serverInfo") {
                            put("name", "git-mcp-server")
                            put("version", "1.0.0")
                        }
                        putJsonObject("capabilities") {
                            putJsonObject("tools") {
                                put("getChangedFiles", true)
                                put("readFileContent", true)
                            }
                        }
                    }
                )
            }
            "tools/call" -> {
                val toolName = (request.params?.get("name") as? JsonPrimitive)?.content
                val arguments = request.params?.get("arguments") as? JsonObject
                
                when (toolName) {
                    "get_changed_files" -> {
                        val root = (arguments?.get("projectRoot") as? JsonPrimitive)?.content ?: projectRoot
                        val result = getChangedFiles(root, logger)
                        if (result.isSuccess) {
                            JsonRpcResponse(
                                id = request.id,
                                result = buildJsonObject {
                                    putJsonArray("files") {
                                        result.getOrNull()?.forEach { file ->
                                            add(file)
                                        }
                                    }
                                }
                            )
                        } else {
                            JsonRpcResponse(
                                id = request.id,
                                error = JsonRpcError(
                                    code = -32603,
                                    message = result.exceptionOrNull()?.message ?: "Unknown error"
                                )
                            )
                        }
                    }
                    "read_file_content" -> {
                        val filePath = (arguments?.get("filePath") as? JsonPrimitive)?.content
                        val root = (arguments?.get("projectRoot") as? JsonPrimitive)?.content ?: projectRoot
                        
                        if (filePath == null) {
                            JsonRpcResponse(
                                id = request.id,
                                error = JsonRpcError(
                                    code = -32602,
                                    message = "filePath parameter is required"
                                )
                            )
                        } else {
                            val result = readFileContent(filePath, root, logger)
                            if (result.isSuccess) {
                                JsonRpcResponse(
                                    id = request.id,
                                    result = buildJsonObject {
                                        put("content", result.getOrNull() ?: "")
                                    }
                                )
                            } else {
                                JsonRpcResponse(
                                    id = request.id,
                                    error = JsonRpcError(
                                        code = -32603,
                                        message = result.exceptionOrNull()?.message ?: "Unknown error"
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
                                message = "Tool not supported: $toolName"
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
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error handling request", e)
        JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = -32603,
                message = "Internal error: ${e.message}"
            )
        )
    }
}

fun getChangedFiles(projectRoot: String, logger: Logger): Result<List<String>> {
    return try {
        val root = File(projectRoot)
        val gitDir = File(root, ".git")
        
        if (!gitDir.exists() || !gitDir.isDirectory) {
            return Result.failure(Exception("Not a git repository. Project root: ${root.absolutePath}"))
        }
        
        val process = ProcessBuilder()
            .command("git", "diff", "--name-only", "HEAD")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            val error = process.errorStream.bufferedReader().readText()
            logger.log(Level.WARNING, "Git command failed: $error")
            return Result.failure(Exception("Git command failed: $error"))
        }
        
        val supportedExtensions = setOf(".kt", ".xml", ".java", ".kts", ".sh")
        val changedFiles = output.lines()
            .filter { it.isNotBlank() }
            .filter { filePath ->
                val extension = File(filePath).extension
                supportedExtensions.contains(".$extension")
            }
            .map { filePath ->
                val fullPath = if (File(filePath).isAbsolute) {
                    filePath
                } else {
                    File(root, filePath).absolutePath
                }
                fullPath
            }
            .filter { File(it).exists() }
        
        logger.log(Level.INFO, "Found ${changedFiles.size} changed files")
        Result.success(changedFiles)
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error getting changed files", e)
        Result.failure(e)
    }
}

fun readFileContent(filePath: String, projectRoot: String, logger: Logger): Result<String> {
    return try {
        val root = File(projectRoot)
        val file = if (File(filePath).isAbsolute) {
            File(filePath)
        } else {
            File(root, filePath)
        }
        
        if (!file.exists()) {
            return Result.failure(Exception("File not found: $filePath"))
        }
        
        if (!file.canRead()) {
            return Result.failure(Exception("Cannot read file: $filePath"))
        }
        
        val content = file.readText(Charsets.UTF_8)
        Result.success(content)
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error reading file: $filePath", e)
        Result.failure(e)
    }
}
