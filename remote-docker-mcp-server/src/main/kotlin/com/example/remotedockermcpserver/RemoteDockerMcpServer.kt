package com.example.remotedockermcpserver

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
import java.util.logging.Logger
import java.util.logging.Level
import java.io.BufferedReader
import java.io.InputStreamReader
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

class DockerAdbExecutor {
    private val logger = Logger.getLogger("DockerAdbExecutor")
    
    /**
     * Выполняет команду через Docker или напрямую через ADB
     */
    fun executeAdbCommand(command: String, timeoutSeconds: Int = 30): Result<String> {
        return try {
            logger.log(Level.INFO, "Executing ADB command: $command")
            
            // Проверяем, доступен ли ADB напрямую
            val adbPath = findAdbPath()
            val fullCommand = if (adbPath != null) {
                "$adbPath $command"
            } else {
                // Пытаемся через Docker
                executeViaDocker(command)
            }
            
            val process = Runtime.getRuntime().exec(fullCommand)
            val output = StringBuilder()
            val errorOutput = StringBuilder()
            
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val outputThread = Thread {
                outputReader.useLines { lines ->
                    lines.forEach { line ->
                        output.appendLine(line)
                    }
                }
            }
            
            val errorThread = Thread {
                errorReader.useLines { lines ->
                    lines.forEach { line ->
                        errorOutput.appendLine(line)
                    }
                }
            }
            
            outputThread.start()
            errorThread.start()
            
            val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                return Result.failure(Exception("Command timeout after $timeoutSeconds seconds"))
            }
            
            outputThread.join(1000)
            errorThread.join(1000)
            
            val exitValue = process.exitValue()
            val outputText = output.toString().trim()
            val errorText = errorOutput.toString().trim()
            
            if (exitValue == 0) {
                logger.log(Level.INFO, "Command succeeded: $outputText")
                Result.success(outputText.ifEmpty { "Command executed successfully" })
            } else {
                logger.log(Level.WARNING, "Command failed with exit code $exitValue: $errorText")
                Result.failure(Exception("ADB command failed: $errorText"))
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error executing ADB command", e)
            Result.failure(e)
        }
    }
    
    private fun findAdbPath(): String? {
        val possiblePaths = listOf(
            "adb",
            "/usr/local/bin/adb",
            "/usr/bin/adb",
            System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb" },
            System.getenv("ANDROID_SDK_ROOT")?.let { "$it/platform-tools/adb" }
        )
        
        for (path in possiblePaths) {
            if (path != null && isCommandAvailable(path)) {
                return path
            }
        }
        
        return null
    }
    
    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which $command")
            val finished = process.waitFor(2, TimeUnit.SECONDS)
            finished && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun executeViaDocker(command: String): String {
        // Пытаемся найти Docker контейнер с Android эмулятором
        val containerId = findAndroidEmulatorContainer()
        return if (containerId != null) {
            "docker exec $containerId adb $command"
        } else {
            // Если контейнер не найден, пытаемся использовать ADB напрямую
            "adb $command"
        }
    }
    
    private fun findAndroidEmulatorContainer(): String? {
        return try {
            val process = Runtime.getRuntime().exec("docker ps --format '{{.ID}} {{.Names}}'")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val containers = reader.readLines()
            
            containers.firstOrNull { line ->
                line.contains("android", ignoreCase = true) || 
                line.contains("emulator", ignoreCase = true)
            }?.split(" ")?.firstOrNull()
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to find Docker container", e)
            null
        }
    }
    
    /**
     * Проверяет доступность ADB и подключенных устройств
     */
    fun checkAdbAvailability(): Result<String> {
        return executeAdbCommand("devices")
    }
    
    /**
     * Выполняет нажатие кнопки Home
     */
    fun pressHome(): Result<String> {
        return executeAdbCommand("shell input keyevent KEYCODE_HOME")
    }
    
    /**
     * Выполняет нажатие кнопки Back
     */
    fun pressBack(): Result<String> {
        return executeAdbCommand("shell input keyevent KEYCODE_BACK")
    }
    
    /**
     * Открывает приложение по package name
     */
    fun openApp(packageName: String): Result<String> {
        return executeAdbCommand("shell monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }
    
    /**
     * Сворачивает текущее приложение (переход на Home)
     */
    fun minimizeApp(): Result<String> {
        return pressHome()
    }
    
    /**
     * Выполняет произвольную ADB команду
     */
    fun executeCustomCommand(command: String): Result<String> {
        return executeAdbCommand("shell $command")
    }
}

fun main(args: Array<String>) {
    val logger = Logger.getLogger("RemoteDockerMcpServer")
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8082
    logger.log(Level.INFO, "Starting Remote Docker MCP Server on port $port...")
    
    val json = Json { ignoreUnknownKeys = true }
    val dockerAdbExecutor = DockerAdbExecutor()
    
    val server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            anyHost()
        }
        
        install(ContentNegotiation) {
            json(json)
        }
        
        routing {
            route("mcp") {
                post {
                    try {
                        val rawBody = call.receiveText()
                        logger.log(Level.INFO, "Received raw request body: $rawBody")
                        
                        val request = json.decodeFromString<JsonRpcRequest>(rawBody)
                        logger.log(Level.INFO, "Parsed request: method=${request.method}, id=${request.id}, params=${request.params}")
                        
                        val response = when (request.method) {
                            "initialize" -> {
                                JsonRpcResponse(
                                    id = request.id,
                                    result = buildJsonObject {
                                        put("protocolVersion", "2024-11-05")
                                        putJsonObject("capabilities") {
                                            putJsonObject("tools") {
                                                put("listChanged", JsonNull)
                                            }
                                        }
                                        putJsonObject("serverInfo") {
                                            put("name", "remote-docker-mcp-server")
                                            put("version", "1.0.0")
                                        }
                                    }
                                )
                            }
                            "tools/list" -> {
                                JsonRpcResponse(
                                    id = request.id,
                                    result = buildJsonObject {
                                        putJsonArray("tools") {
                                            // Проверка доступности ADB
                                            addJsonObject {
                                                put("name", "check_adb_availability")
                                                put("description", "Check if ADB is available and list connected Android devices/emulators")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") { }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Нажатие кнопки Home
                                            addJsonObject {
                                                put("name", "press_home")
                                                put("description", "Press the Home button on Android emulator/device")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") { }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Нажатие кнопки Back
                                            addJsonObject {
                                                put("name", "press_back")
                                                put("description", "Press the Back button on Android emulator/device")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") { }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Открытие приложения
                                            addJsonObject {
                                                put("name", "open_app")
                                                put("description", "Open an Android app by package name (e.g., com.android.chrome for Chrome, com.google.android.youtube for YouTube)")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("packageName") {
                                                            put("type", "string")
                                                            put("description", "Android package name (e.g., com.android.chrome, com.google.android.youtube)")
                                                        }
                                                    }
                                                    putJsonArray("required") {
                                                        add("packageName")
                                                    }
                                                }
                                            }
                                            // Сворачивание приложения
                                            addJsonObject {
                                                put("name", "minimize_app")
                                                put("description", "Minimize the current app by pressing Home button")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") { }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Произвольная ADB команда
                                            addJsonObject {
                                                put("name", "execute_adb_command")
                                                put("description", "Execute a custom ADB shell command on Android emulator/device")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("command") {
                                                            put("type", "string")
                                                            put("description", "ADB shell command to execute (e.g., 'input tap 500 500', 'am start -n com.android.settings/.Settings')")
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
                                logger.log(Level.INFO, "Processing tools/call request. Params: ${request.params}")
                                
                                val toolName = request.params?.get("name")?.jsonPrimitive?.content
                                val argumentsElement = request.params?.get("arguments")
                                
                                logger.log(Level.INFO, "Tool name: $toolName, Arguments element: $argumentsElement")
                                
                                val arguments = when {
                                    argumentsElement is JsonObject -> argumentsElement
                                    argumentsElement is JsonElement -> {
                                        logger.log(Level.INFO, "Arguments is JsonElement, converting to JsonObject")
                                        argumentsElement.jsonObject
                                    }
                                    else -> {
                                        logger.log(Level.WARNING, "Arguments is not JsonObject or JsonElement")
                                        null
                                    }
                                }
                                
                                logger.log(Level.INFO, "Parsed arguments: $arguments")
                                
                                val result = when (toolName) {
                                    "check_adb_availability" -> {
                                        dockerAdbExecutor.checkAdbAvailability()
                                    }
                                    "press_home" -> {
                                        dockerAdbExecutor.pressHome()
                                    }
                                    "press_back" -> {
                                        dockerAdbExecutor.pressBack()
                                    }
                                    "open_app" -> {
                                        val packageName = arguments?.get("packageName")?.jsonPrimitive?.content
                                        if (packageName.isNullOrBlank()) {
                                            Result.failure(Exception("packageName parameter is required"))
                                        } else {
                                            dockerAdbExecutor.openApp(packageName)
                                        }
                                    }
                                    "minimize_app" -> {
                                        dockerAdbExecutor.minimizeApp()
                                    }
                                    "execute_adb_command" -> {
                                        val command = arguments?.get("command")?.jsonPrimitive?.content
                                        if (command.isNullOrBlank()) {
                                            Result.failure(Exception("command parameter is required"))
                                        } else {
                                            dockerAdbExecutor.executeCustomCommand(command)
                                        }
                                    }
                                    else -> {
                                        Result.failure(Exception("Unknown tool: $toolName"))
                                    }
                                }
                                
                                when {
                                    result.isSuccess -> {
                                        val resultText = result.getOrNull() ?: "Command executed successfully"
                                        JsonRpcResponse(
                                            id = request.id,
                                            result = buildJsonObject {
                                                putJsonArray("content") {
                                                    addJsonObject {
                                                        put("type", "text")
                                                        put("text", resultText)
                                                    }
                                                }
                                                put("isError", false)
                                            }
                                        )
                                    }
                                    else -> {
                                        val error = result.exceptionOrNull()
                                        logger.log(Level.WARNING, "Tool execution failed: $toolName", error)
                                        JsonRpcResponse(
                                            id = request.id,
                                            result = buildJsonObject {
                                                putJsonArray("content") {
                                                    addJsonObject {
                                                        put("type", "text")
                                                        put("text", "Error: ${error?.message ?: "Unknown error occurred"}")
                                                    }
                                                }
                                                put("isError", true)
                                            }
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
    }
    
    try {
        logger.log(Level.INFO, "Remote Docker MCP Server starting on port $port...")
        val serverInstance = server.start(wait = false)
        logger.log(Level.INFO, "Remote Docker MCP Server started successfully on port $port")
        logger.log(Level.INFO, "MCP endpoint available at: http://0.0.0.0:$port/mcp")
        logger.log(Level.INFO, "For Android emulator use: http://10.0.2.2:$port/mcp")
        
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.log(Level.INFO, "Shutting down Remote Docker MCP Server...")
            serverInstance.stop(1000, 2000)
        })
        
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Failed to start server", e)
        e.printStackTrace()
        System.exit(1)
    }
}

