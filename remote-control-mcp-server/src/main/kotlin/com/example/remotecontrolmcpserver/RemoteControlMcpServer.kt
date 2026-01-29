package com.example.remotecontrolmcpserver

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
import java.io.File
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

class ControlAdbExecutor {
    private val logger = Logger.getLogger("ControlAdbExecutor")
    
    /**
     * Выполняет команду через ADB с опциональным указанием устройства
     */
    fun executeAdbCommand(command: String, deviceId: String? = null, timeoutSeconds: Int = 30): Result<String> {
        return try {
            logger.log(Level.INFO, "Executing ADB command: $command${deviceId?.let { " on device: $it" } ?: ""}")
            
            val adbPath = findAdbPath()
            if (adbPath == null) {
                return Result.failure(Exception("ADB not found. Please install Android SDK Platform Tools."))
            }
            
            val deviceFlag = if (deviceId != null) "-s $deviceId" else ""
            val fullCommand = "$adbPath $deviceFlag $command"
            
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
    
    /**
     * Получает список подключенных устройств
     */
    fun getConnectedDevices(): Result<List<DeviceInfo>> {
        return try {
            val result = executeAdbCommand("devices -l")
            if (result.isFailure) {
                return result.map { emptyList() }
            }
            
            val output = result.getOrNull() ?: ""
            val devices = mutableListOf<DeviceInfo>()
            
            output.lines().forEach { line ->
                if (line.isNotBlank() && !line.startsWith("List of devices")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2 && parts[1] == "device") {
                        val deviceId = parts[0]
                        val model = parts.find { it.startsWith("model:") }?.substringAfter("model:") ?: "Unknown"
                        val device = parts.find { it.startsWith("device:") }?.substringAfter("device:") ?: "Unknown"
                        devices.add(DeviceInfo(deviceId, model, device))
                    }
                }
            }
            
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Проверяет доступность ADB и подключенных устройств
     */
    fun checkAdbAvailability(deviceId: String? = null): Result<String> {
        return if (deviceId != null) {
            executeAdbCommand("devices", deviceId)
        } else {
            executeAdbCommand("devices")
        }
    }
    
    /**
     * Выполняет нажатие кнопки Home
     */
    fun pressHome(deviceId: String? = null): Result<String> {
        return executeAdbCommand("shell input keyevent KEYCODE_HOME", deviceId)
    }
    
    /**
     * Выполняет нажатие кнопки Back
     */
    fun pressBack(deviceId: String? = null): Result<String> {
        return executeAdbCommand("shell input keyevent KEYCODE_BACK", deviceId)
    }
    
    /**
     * Открывает приложение по package name
     */
    fun openApp(packageName: String, deviceId: String? = null): Result<String> {
        return executeAdbCommand("shell monkey -p $packageName -c android.intent.category.LAUNCHER 1", deviceId)
    }
    
    /**
     * Сворачивает текущее приложение (переход на Home)
     */
    fun minimizeApp(deviceId: String? = null): Result<String> {
        return pressHome(deviceId)
    }
    
    /**
     * Делает скриншот устройства
     */
    fun takeScreenshot(deviceId: String? = null): Result<String> {
        return try {
            val timestamp = System.currentTimeMillis()
            val screenshotPath = "/sdcard/screenshot_$timestamp.png"
            val localPath = "/tmp/screenshot_$timestamp.png"
            
            // Делаем скриншот на устройстве
            val screenshotResult = executeAdbCommand("shell screencap -p $screenshotPath", deviceId)
            if (screenshotResult.isFailure) {
                return screenshotResult
            }
            
            // Копируем скриншот на локальную машину
            val pullResult = executeAdbCommand("pull $screenshotPath $localPath", deviceId)
            if (pullResult.isFailure) {
                return pullResult
            }
            
            // Удаляем скриншот с устройства
            executeAdbCommand("shell rm $screenshotPath", deviceId)
            
            val file = File(localPath)
            if (file.exists()) {
                Result.success("Screenshot saved to: $localPath")
            } else {
                Result.failure(Exception("Screenshot file not found after pull"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Выполняет произвольную ADB команду
     */
    fun executeCustomCommand(command: String, deviceId: String? = null): Result<String> {
        return executeAdbCommand("shell $command", deviceId)
    }
}

data class DeviceInfo(
    val deviceId: String,
    val model: String,
    val device: String
)

fun main(args: Array<String>) {
    val logger = Logger.getLogger("RemoteControlMcpServer")
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8082
    logger.log(Level.INFO, "Starting Remote Control MCP Server on port $port...")
    
    val json = Json { ignoreUnknownKeys = true }
    val controlAdbExecutor = ControlAdbExecutor()
    
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
                                            put("name", "remote-control-mcp-server")
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
                                            // Получение списка устройств
                                            addJsonObject {
                                                put("name", "list_devices")
                                                put("description", "List all connected Android devices/emulators")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") { }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Проверка доступности ADB
                                            addJsonObject {
                                                put("name", "check_adb_availability")
                                                put("description", "Check if ADB is available and list connected Android devices/emulators. Optionally specify deviceId to check specific device.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("deviceId") {
                                                            put("type", "string")
                                                            put("description", "Optional device ID to check. If not specified, checks all devices.")
                                                        }
                                                    }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Нажатие кнопки Home
                                            addJsonObject {
                                                put("name", "press_home")
                                                put("description", "Press the Home button on Android device/emulator. Optionally specify deviceId for connected real device.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("deviceId") {
                                                            put("type", "string")
                                                            put("description", "Optional device ID for connected real device. If not specified, uses default device.")
                                                        }
                                                    }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Нажатие кнопки Back
                                            addJsonObject {
                                                put("name", "press_back")
                                                put("description", "Press the Back button on Android device/emulator. Optionally specify deviceId for connected real device.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("deviceId") {
                                                            put("type", "string")
                                                            put("description", "Optional device ID for connected real device. If not specified, uses default device.")
                                                        }
                                                    }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Открытие приложения
                                            addJsonObject {
                                                put("name", "open_app")
                                                put("description", "Open an Android app by package name (e.g., com.android.chrome for Chrome, com.google.android.youtube for YouTube). Optionally specify deviceId for connected real device.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("packageName") {
                                                            put("type", "string")
                                                            put("description", "Android package name (e.g., com.android.chrome, com.google.android.youtube)")
                                                        }
                                                        putJsonObject("deviceId") {
                                                            put("type", "string")
                                                            put("description", "Optional device ID for connected real device. If not specified, uses default device.")
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
                                                put("description", "Minimize the current app by pressing Home button. Optionally specify deviceId for connected real device.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("deviceId") {
                                                            put("type", "string")
                                                            put("description", "Optional device ID for connected real device. If not specified, uses default device.")
                                                        }
                                                    }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Скриншот
                                            addJsonObject {
                                                put("name", "take_screenshot")
                                                put("description", "Take a screenshot of the Android device/emulator screen. Screenshot is saved to /tmp/screenshot_[timestamp].png on the host machine. Optionally specify deviceId for connected real device.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("deviceId") {
                                                            put("type", "string")
                                                            put("description", "Optional device ID for connected real device. If not specified, uses default device.")
                                                        }
                                                    }
                                                    putJsonArray("required") { }
                                                }
                                            }
                                            // Произвольная ADB команда
                                            addJsonObject {
                                                put("name", "execute_adb_command")
                                                put("description", "Execute a custom ADB shell command on Android device/emulator. Optionally specify deviceId for connected real device.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("command") {
                                                            put("type", "string")
                                                            put("description", "ADB shell command to execute (e.g., 'input tap 500 500', 'am start -n com.android.settings/.Settings')")
                                                        }
                                                        putJsonObject("deviceId") {
                                                            put("type", "string")
                                                            put("description", "Optional device ID for connected real device. If not specified, uses default device.")
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
                                
                                val deviceId = arguments?.get("deviceId")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                
                                val result = when (toolName) {
                                    "list_devices" -> {
                                        val devicesResult = controlAdbExecutor.getConnectedDevices()
                                        if (devicesResult.isSuccess) {
                                            val devices = devicesResult.getOrNull() ?: emptyList()
                                            val devicesInfo = devices.joinToString("\n") { 
                                                "Device ID: ${it.deviceId}, Model: ${it.model}, Device: ${it.device}" 
                                            }
                                            Result.success(if (devicesInfo.isBlank()) "No devices connected" else devicesInfo)
                                        } else {
                                            devicesResult
                                        }
                                    }
                                    "check_adb_availability" -> {
                                        controlAdbExecutor.checkAdbAvailability(deviceId)
                                    }
                                    "press_home" -> {
                                        controlAdbExecutor.pressHome(deviceId)
                                    }
                                    "press_back" -> {
                                        controlAdbExecutor.pressBack(deviceId)
                                    }
                                    "open_app" -> {
                                        val packageName = arguments?.get("packageName")?.jsonPrimitive?.content
                                        if (packageName.isNullOrBlank()) {
                                            Result.failure(Exception("packageName parameter is required"))
                                        } else {
                                            controlAdbExecutor.openApp(packageName, deviceId)
                                        }
                                    }
                                    "minimize_app" -> {
                                        controlAdbExecutor.minimizeApp(deviceId)
                                    }
                                    "take_screenshot" -> {
                                        controlAdbExecutor.takeScreenshot(deviceId)
                                    }
                                    "execute_adb_command" -> {
                                        val command = arguments?.get("command")?.jsonPrimitive?.content
                                        if (command.isNullOrBlank()) {
                                            Result.failure(Exception("command parameter is required"))
                                        } else {
                                            controlAdbExecutor.executeCustomCommand(command, deviceId)
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
                                                        put("text", resultText as String)
                                                    }
                                                }
                                                put("isError", JsonPrimitive(false))
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
                                                        put("text", "Error: ${error?.message ?: "Unknown error occurred"}" as String)
                                                    }
                                                }
                                                put("isError", JsonPrimitive(true))
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
        logger.log(Level.INFO, "Remote Control MCP Server starting on port $port...")
        val serverInstance = server.start(wait = false)
        logger.log(Level.INFO, "Remote Control MCP Server started successfully on port $port")
        logger.log(Level.INFO, "MCP endpoint available at: http://0.0.0.0:$port/mcp")
        logger.log(Level.INFO, "For Android emulator use: http://10.0.2.2:$port/mcp")
        
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.log(Level.INFO, "Shutting down Remote Control MCP Server...")
            serverInstance.stop(1000, 2000)
        })
        
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Failed to start server", e)
        e.printStackTrace()
        System.exit(1)
    }
}
