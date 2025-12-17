package com.example.aiagentchat.feature.chat.data.repository

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.JsonRpcRequest
import com.example.aiagentchat.feature.chat.data.api.JsonRpcResponse
import com.example.aiagentchat.feature.chat.data.api.McpApi
import com.example.aiagentchat.feature.chat.domain.model.McpTool
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.ResponseBody
import retrofit2.converter.gson.GsonConverterFactory

class McpRepositoryImpl(
    private val mcpApi: McpApi,
    private val context7ApiKey: String?,
    private val gson: Gson = Gson()
) : McpRepository {

    companion object {
        private const val TAG = "McpRepository"
        private var requestId = 1
    }

    private var sessionId: String? = null
    private var isInitialized = false
    private var isLoadingTools = false
    private val _tools = MutableStateFlow<List<McpTool>>(emptyList())
    override fun observeTools(): Flow<List<McpTool>> = _tools.asStateFlow()

    override suspend fun listTools(): Result<List<McpTool>> {
        // Prevent multiple simultaneous calls
        if (isLoadingTools) {
            Log.d(TAG, "listTools already in progress, skipping")
            return Result.success(_tools.value)
        }
        
        return try {
            isLoadingTools = true
            
            // Initialize session if not already done
            if (!isInitialized) {
                val initResult = initializeSession()
                if (initResult.isFailure) {
                    isLoadingTools = false
                    return Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize session"))
                }
                isInitialized = true
            }

            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/list",
                params = emptyMap()
            )

            val authorization = context7ApiKey?.let { "Bearer $it" }
            val response = mcpApi.sendRequest(
                authorization = authorization,
                sessionId = sessionId,
                request = request
            )
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    return Result.failure(Exception("MCP Error: ${body.error.message}"))
                }

                val tools = parseToolsResponse(body.result)
                _tools.update { tools }
                isLoadingTools = false
                Result.success(tools)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "List tools failed: ${response.code()} - $errorBody")
                isLoadingTools = false
                Result.failure(Exception("HTTP Error: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing MCP tools", e)
            isLoadingTools = false
            Result.failure(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseToolsResponse(result: Map<String, Any>?): List<McpTool> {
        if (result == null) return emptyList()

        val toolsJson = result["tools"] as? List<*> ?: return emptyList()
        
        return toolsJson.mapNotNull { toolItem ->
            try {
                val toolMap = toolItem as? Map<String, Any> ?: return@mapNotNull null
                McpTool(
                    name = toolMap["name"] as? String ?: return@mapNotNull null,
                    description = toolMap["description"] as? String,
                    inputSchema = toolMap["inputSchema"] as? Map<String, Any>
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing tool", e)
                null
            }
        }
    }

    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<String> {
        return try {
            if (!isInitialized) {
                val initResult = initializeSession()
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize session"))
                }
                isInitialized = true
            }

            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/call",
                params = mapOf(
                    "name" to toolName,
                    "arguments" to arguments
                )
            )

            Log.d(TAG, "Calling MCP tool: $toolName")
            Log.d(TAG, "Request params: name=$toolName, arguments=$arguments")
            Log.d(TAG, "Full request: id=${request.id}, method=${request.method}, params=${request.params}")

            val authorization = context7ApiKey?.let { "Bearer $it" }
            val response = mcpApi.sendRequest(
                authorization = authorization,
                sessionId = sessionId,
                request = request
            )
            
            Log.d(TAG, "MCP response: code=${response.code()}, isSuccessful=${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    return Result.failure(Exception("MCP Error: ${body.error.message}"))
                }

                val result = parseToolCallResponse(body.result)
                Result.success(result)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Call tool failed: ${response.code()} - $errorBody")
                Result.failure(Exception("HTTP Error: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling MCP tool", e)
            Result.failure(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseToolCallResponse(result: Map<String, Any>?): String {
        if (result == null) return "No result"
        
        val content = result["content"] as? List<*> ?: return "No content"
        if (content.isEmpty()) return "Empty content"
        
        val firstContent = content.firstOrNull() as? Map<String, Any> ?: return "Invalid content format"
        val text = firstContent["text"] as? String 
            ?: firstContent["content"] as? String
            ?: (firstContent["type"] as? String)?.let { 
                if (it == "text") firstContent["text"] as? String else null
            }
        
        return text ?: "No text content"
    }

    private suspend fun initializeSession(): Result<Unit> {
        return try {
            val initRequest = JsonRpcRequest(
                id = requestId++,
                method = "initialize",
                params = mapOf(
                    "protocolVersion" to "2024-11-05",
                    "capabilities" to mapOf<String, Any>(),
                    "clientInfo" to mapOf(
                        "name" to "ai-agent-chat",
                        "version" to "1.0.0"
                    )
                )
            )

            val response = mcpApi.sendRequest(
                authorization = "Bearer $context7ApiKey",
                sessionId = null, // No session ID for initialize
                request = initRequest
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    return Result.failure(Exception("MCP Initialize Error: ${body.error.message}"))
                }

                // Extract session ID from response headers if available
                val sessionIdHeader = response.headers()["mcp-session-id"]
                if (sessionIdHeader != null) {
                    sessionId = sessionIdHeader
                    Log.d(TAG, "Session initialized: $sessionId")
                }

                // Send initialized notification
                val initializedRequest = JsonRpcRequest(
                    id = requestId++,
                    method = "notifications/initialized",
                    params = emptyMap()
                )

                val auth = context7ApiKey?.let { "Bearer $it" }
                mcpApi.sendRequest(
                    authorization = auth,
                    sessionId = sessionId,
                    request = initializedRequest
                )

                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Initialize failed: ${response.code()} - $errorBody")
                Result.failure(Exception("HTTP Error: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MCP session", e)
            Result.failure(e)
        }
    }
}

