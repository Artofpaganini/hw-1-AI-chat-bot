package com.example.aiagentchat.feature.chat.domain.usecase

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.api.ToolCallDto
import com.example.aiagentchat.feature.chat.data.converter.McpToolConverter
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.core.common.preferences.PreferencesManager
import com.example.aiagentchat.feature.chat.domain.model.McpServer
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MultiMcpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository
import com.google.gson.Gson

class SendMessageUseCase(
    private val aiModelRepository: AiModelRepository,
    private val metricsRepository: MetricsRepository,
    private val mcpRepository: McpRepository,
    private val multiMcpRepository: MultiMcpRepository,
    private val preferencesManager: PreferencesManager,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "SendMessageUseCase"
        private const val MAX_TOOL_CALL_ITERATIONS = 5
    }

    suspend operator fun invoke(
        model: AiModel,
        messages: List<ChatMessageDto>,
        enabledMcpTools: Set<String> = emptySet(),
        enabledMcpServerTools: Map<String, Set<String>> = emptyMap(), // serverId -> Set<toolName>
        remoteControlDeviceId: String? = null
    ): Result<Message> {
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.d(TAG, "SendMessageUseCase invoked with enabledMcpTools: $enabledMcpTools, enabledMcpServerTools: $enabledMcpServerTools")
            
            // Load tools from all enabled servers
            val allTools = mutableListOf<com.example.aiagentchat.feature.chat.data.api.ToolDto>()
            
            // Legacy: Load from old single MCP repository
            if (enabledMcpTools.isNotEmpty()) {
                Log.d(TAG, "Loading MCP tools for: $enabledMcpTools")
                val loadedTools = loadMcpTools(enabledMcpTools)
                allTools.addAll(loadedTools)
            }
            
            // Load from multiple MCP servers
            if (enabledMcpServerTools.isNotEmpty()) {
                Log.d(TAG, "Loading tools from ${enabledMcpServerTools.size} servers")
                val servers = multiMcpRepository.listServers()
                servers.forEach { server ->
                    val serverTools = enabledMcpServerTools[server.id] ?: return@forEach
                    if (serverTools.isNotEmpty()) {
                        val toolsResult = multiMcpRepository.listToolsForServer(server.id)
                        if (toolsResult.isSuccess) {
                            val mcpTools = toolsResult.getOrNull() ?: emptyList()
                            val filteredTools = mcpTools.filter { serverTools.contains(it.name) }
                            // Передаем deviceId для инструментов Remote Control
                            val deviceIdForTools = if (server.id == McpServer.REMOTE_CONTROL_SERVER_ID) {
                                remoteControlDeviceId
                            } else {
                                null
                            }
                            val convertedTools = McpToolConverter.convertToAiTools(filteredTools, deviceIdForTools)
                            allTools.addAll(convertedTools)
                            Log.d(TAG, "Loaded ${convertedTools.size} tools from server ${server.id}${if (deviceIdForTools != null) " with deviceId: $deviceIdForTools" else ""}")
                        }
                    }
                }
            }
            
            Log.d(TAG, "Total loaded ${allTools.size} tools: ${allTools.map { it.function.name }}")
            
            val result = processWithToolCalls(model, messages, allTools, enabledMcpServerTools, remoteControlDeviceId, startTime, 0)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in SendMessageUseCase", e)
            Result.failure(e)
        }
    }
    
    private suspend fun loadMcpTools(enabledTools: Set<String>): List<com.example.aiagentchat.feature.chat.data.api.ToolDto> {
        return try {
            Log.d(TAG, "Loading MCP tools from repository...")
            val toolsResult = mcpRepository.listTools()
            if (toolsResult.isSuccess) {
                val mcpTools = toolsResult.getOrNull() ?: emptyList()
                Log.d(TAG, "Retrieved ${mcpTools.size} MCP tools: ${mcpTools.map { it.name }}")
                val filteredTools = mcpTools.filter { enabledTools.contains(it.name) }
                Log.d(TAG, "Filtered to ${filteredTools.size} enabled tools: ${filteredTools.map { it.name }}")
                val convertedTools = McpToolConverter.convertToAiTools(filteredTools, null)
                Log.d(TAG, "Converted to ${convertedTools.size} AI tools")
                convertedTools
            } else {
                val error = toolsResult.exceptionOrNull()
                Log.e(TAG, "Failed to load MCP tools", error)
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MCP tools", e)
            emptyList()
        }
    }
    
    private suspend fun processWithToolCalls(
        model: AiModel,
        messages: List<ChatMessageDto>,
        tools: List<com.example.aiagentchat.feature.chat.data.api.ToolDto>,
        enabledMcpServerTools: Map<String, Set<String>>,
        remoteControlDeviceId: String?,
        startTime: Long,
        iteration: Int
    ): Result<Message> {
        if (iteration >= MAX_TOOL_CALL_ITERATIONS) {
            Log.w(TAG, "Maximum tool call iterations ($MAX_TOOL_CALL_ITERATIONS) reached")
            return Result.failure(Exception("Maximum tool call iterations reached"))
        }
        
        Log.d(TAG, "Processing with ${tools.size} tools, iteration: $iteration")
        if (tools.isNotEmpty()) {
            Log.d(TAG, "Tools being sent to AI: ${tools.map { it.function.name }}")
        }
        
        // Добавляем системное сообщение о Device ID, если он указан и есть инструменты Remote Control
        val messagesWithSystemContext = if (remoteControlDeviceId != null && iteration == 0 && hasRemoteControlTools(tools)) {
            val systemMessage = ChatMessageDto(
                role = "system",
                content = "CRITICAL INSTRUCTION: The user has configured Device ID '$remoteControlDeviceId' in the app settings for Remote Control. " +
                        "When the user asks to perform any action on a device (e.g., 'open YouTube', 'press Home', 'take screenshot'), " +
                        "you MUST use Device ID '$remoteControlDeviceId' automatically WITHOUT asking the user. " +
                        "The deviceId parameter will be automatically added to tool calls - you should NOT include it in your tool call arguments. " +
                        "Do NOT ask the user which device to use. " +
                        "Only if the user explicitly mentions a different device ID (e.g., 'on emulator-5554'), then you may use that device ID instead."
            )
            listOf(systemMessage) + messages
        } else {
            messages
        }
        
        val response = aiModelRepository.sendMessage(model, messagesWithSystemContext, tools.takeIf { it.isNotEmpty() })
        
        return when {
            response.isSuccess -> {
                val aiResponse = response.getOrNull() ?: return Result.failure(Exception("Empty response"))
                Log.d(TAG, "AI response received. Finish reason: ${aiResponse.finishReason}, Tool calls: ${aiResponse.toolCalls?.size ?: 0}")
                
                val hasToolCalls = aiResponse.toolCalls != null && aiResponse.toolCalls.isNotEmpty()
                val finishReasonIndicatesToolCalls = aiResponse.finishReason == "tool_calls" || 
                                                      aiResponse.finishReason == "function_call" ||
                                                      aiResponse.finishReason == "tool_use"
                
                if (hasToolCalls || finishReasonIndicatesToolCalls) {
                    val toolCallsToProcess = aiResponse.toolCalls ?: emptyList()
                    if (toolCallsToProcess.isNotEmpty()) {
                        Log.d(TAG, "AI model requested ${toolCallsToProcess.size} tool calls: ${toolCallsToProcess.map { it.function.name }}")
                        handleToolCalls(model, messages, toolCallsToProcess, tools, enabledMcpServerTools, remoteControlDeviceId, startTime, iteration)
                    } else {
                        Log.w(TAG, "Finish reason indicates tool calls but no tool_calls in response. Finish reason: ${aiResponse.finishReason}")
                        val responseTimeMs = System.currentTimeMillis() - startTime
                        val metrics = metricsRepository.calculateMetrics(
                            model = model,
                            responseTimeMs = responseTimeMs,
                            inputTokens = aiResponse.inputTokens,
                            outputTokens = aiResponse.outputTokens
                        )
                        Result.success(
                            Message(
                                content = aiResponse.content.ifEmpty { "No response content" },
                                isUser = false,
                                model = model,
                                metrics = metrics
                            )
                        )
                    }
                } else {
                    Log.d(TAG, "AI model returned direct response (no tool calls). Content length: ${aiResponse.content.length}")
                    val responseTimeMs = System.currentTimeMillis() - startTime
                    val metrics = metricsRepository.calculateMetrics(
                        model = model,
                        responseTimeMs = responseTimeMs,
                        inputTokens = aiResponse.inputTokens,
                        outputTokens = aiResponse.outputTokens
                    )
                    Result.success(
                        Message(
                            content = aiResponse.content.ifEmpty { "No response content" },
                            isUser = false,
                            model = model,
                            metrics = metrics
                        )
                    )
                }
            }
            else -> {
                val error = response.exceptionOrNull()
                Log.e(TAG, "Error from AI model", error)
                Result.failure(error ?: Exception("Unknown error"))
            }
        }
    }
    
    private suspend fun handleToolCalls(
        model: AiModel,
        currentMessages: List<ChatMessageDto>,
        toolCalls: List<ToolCallDto>,
        tools: List<com.example.aiagentchat.feature.chat.data.api.ToolDto>,
        enabledMcpServerTools: Map<String, Set<String>>,
        remoteControlDeviceId: String?,
        startTime: Long,
        iteration: Int
    ): Result<Message> {
        val updatedMessages = currentMessages.toMutableList()
        val weatherDataList = mutableListOf<String>()

        toolCalls.forEach { toolCall ->
            val toolName = toolCall.function.name
            val argumentsJson = toolCall.function.arguments
            var arguments = McpToolConverter.parseToolCallArguments(argumentsJson)

            Log.d(TAG, "Calling MCP tool: $toolName with arguments: $arguments")

            // Determine which server to use for this tool
            val serverId = findServerForTool(toolName, enabledMcpServerTools)
            
            // Если это инструмент Remote Control MCP Server и deviceId указан, добавляем его в аргументы
            if (serverId == McpServer.REMOTE_CONTROL_SERVER_ID && remoteControlDeviceId != null) {
                val updatedArguments = arguments.toMutableMap()
                // Добавляем deviceId только если его еще нет в аргументах
                if (!updatedArguments.containsKey("deviceId")) {
                    updatedArguments["deviceId"] = remoteControlDeviceId
                    arguments = updatedArguments
                    Log.d(TAG, "Added deviceId to arguments: $remoteControlDeviceId")
                }
            }
            
            val toolResult = if (serverId != null) {
                // Use MultiMcpRepository for server-specific tools
                multiMcpRepository.callTool(serverId, toolName, arguments)
            } else {
                // Fallback to legacy McpRepository
                mcpRepository.callTool(toolName, arguments)
            }

            val toolResultContent = if (toolResult.isSuccess) {
                val result = toolResult.getOrNull() ?: "Tool execution completed"
                // Store weather data if this is get_weather tool
                if (toolName == "get_weather") {
                    weatherDataList.add(result)
                }
                result
            } else {
                val error = toolResult.exceptionOrNull()
                Log.e(TAG, "Tool call failed: $toolName", error)
                "Error: ${error?.message ?: "Unknown error"}"
            }

            updatedMessages.add(
                ChatMessageDto(
                    role = "assistant",
                    content = null,
                    toolCalls = listOf(toolCall)
                )
            )

            updatedMessages.add(
                ChatMessageDto(
                    role = "tool",
                    content = toolResultContent,
                    toolCallId = toolCall.id
                )
            )
        }

        // After processing tool calls, if we have weather data and save_to_drive is enabled, save it
        val weatherDataToSave = weatherDataList.lastOrNull()
        if (weatherDataToSave != null) {
            val googleStorageServerId = McpServer.GOOGLE_STORAGE_SERVER_ID
            val googleStorageTools = enabledMcpServerTools[googleStorageServerId] ?: emptySet()
            if (googleStorageTools.contains("save_to_drive")) {
                Log.d(TAG, "Weather data received, attempting to save to Google Drive")
                saveWeatherDataToDrive(weatherDataToSave)
            }
        }
        
        return processWithToolCalls(model, updatedMessages, tools, enabledMcpServerTools, remoteControlDeviceId, startTime, iteration + 1)
    }
    
            private fun findServerForTool(toolName: String, enabledMcpServerTools: Map<String, Set<String>>): String? {
                return enabledMcpServerTools.entries.find { (_, tools) -> tools.contains(toolName) }?.key
            }

            private fun hasRemoteControlTools(tools: List<com.example.aiagentchat.feature.chat.data.api.ToolDto>): Boolean {
                val remoteControlToolNames = listOf(
                    "list_devices",
                    "check_adb_availability",
                    "press_home",
                    "press_back",
                    "open_app",
                    "minimize_app",
                    "take_screenshot",
                    "execute_adb_command"
                )
                return tools.any { remoteControlToolNames.contains(it.function.name) }
            }
    
    private suspend fun saveWeatherDataToDrive(weatherData: String) {
        try {
            // Get access token from PreferencesManager (initialized from BuildConfig in Application class)
            val accessToken = preferencesManager.googleDriveAccessToken
            if (accessToken.isNullOrBlank()) {
                Log.w(TAG, "Google Drive access token not available. Skipping save to drive.")
                return
            }
            
            // Compress weather data to JSON format
            val compressedData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "weather" to weatherData,
                "source" to "weather-mcp-server"
            )
            val jsonData = gson.toJson(compressedData)
            
            // Find Google Storage server
            val servers = multiMcpRepository.listServers()
            val googleStorageServer = servers.find { it.id == McpServer.GOOGLE_STORAGE_SERVER_ID }
            
            if (googleStorageServer != null) {
                // Strategy: Delete existing file, then create new one with updated data
                // This ensures file is always overwritten with latest data
                Log.d(TAG, "Deleting existing file 'ai-chat-results' from Google Drive (if exists)")
                val deleteResult = multiMcpRepository.callTool(
                    serverId = McpServer.GOOGLE_STORAGE_SERVER_ID,
                    toolName = "delete_file_from_drive",
                    arguments = mapOf(
                        "accessToken" to accessToken,
                        "fileName" to "ai-chat-results"
                    )
                )
                
                if (deleteResult.isSuccess) {
                    Log.d(TAG, "File deleted successfully (or did not exist)")
                } else {
                    Log.w(TAG, "Failed to delete file (may not exist): ${deleteResult.exceptionOrNull()?.message}")
                }
                
                // Create new file with updated data
                Log.d(TAG, "Creating new file 'ai-chat-results' with updated weather data")
                val saveResult = multiMcpRepository.callTool(
                    serverId = McpServer.GOOGLE_STORAGE_SERVER_ID,
                    toolName = "save_to_drive",
                    arguments = mapOf(
                        "accessToken" to accessToken,
                        "fileName" to "ai-chat-results",
                        "data" to jsonData
                    )
                )
                
                if (saveResult.isSuccess) {
                    Log.d(TAG, "Successfully saved weather data to Google Drive")
                } else {
                    val error = saveResult.exceptionOrNull()
                    Log.e(TAG, "Failed to save weather data to Google Drive", error)
                }
            } else {
                Log.w(TAG, "Google Storage MCP Server not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving weather data to Google Drive", e)
        }
    }
}

