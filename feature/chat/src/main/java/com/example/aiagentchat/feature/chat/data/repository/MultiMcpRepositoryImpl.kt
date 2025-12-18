package com.example.aiagentchat.feature.chat.data.repository

import android.util.Log
import com.example.aiagentchat.core.network.ApiClient
import com.example.aiagentchat.feature.chat.data.api.JsonRpcRequest
import com.example.aiagentchat.feature.chat.data.api.JsonRpcResponse
import com.example.aiagentchat.feature.chat.data.api.McpApi
import com.example.aiagentchat.feature.chat.domain.model.McpServer
import com.example.aiagentchat.feature.chat.domain.model.McpTool
import com.example.aiagentchat.feature.chat.domain.repository.MultiMcpRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.Retrofit

class MultiMcpRepositoryImpl(
    private val gson: Gson = Gson()
) : MultiMcpRepository {

    companion object {
        private const val TAG = "MultiMcpRepository"
        private var requestId = 1
    }

    private val _servers = MutableStateFlow<List<McpServer>>(McpServer.DEFAULT_SERVERS)
    override fun observeServers(): Flow<List<McpServer>> = _servers.asStateFlow()

    private val serverSessions = mutableMapOf<String, String>() // serverId -> sessionId
    private val serverInitialized = mutableMapOf<String, Boolean>() // serverId -> isInitialized

    private fun getMcpApiForServer(server: McpServer): McpApi {
        val baseUrl = if (server.getFullUrl().endsWith("/")) server.getFullUrl() else "${server.getFullUrl()}/"
        val retrofit = ApiClient.createRetrofit(baseUrl)
        return retrofit.create(McpApi::class.java)
    }

    override suspend fun listServers(): List<McpServer> {
        return _servers.value
    }

    override suspend fun listToolsForServer(serverId: String): Result<List<McpTool>> {
        val server = _servers.value.find { it.id == serverId }
            ?: return Result.failure(Exception("Server not found: $serverId"))

        return try {
            // Initialize session if not already done
            if (serverInitialized[serverId] != true) {
                val initResult = initializeSession(server)
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize session"))
                }
                serverInitialized[serverId] = true
            }

            val mcpApi = getMcpApiForServer(server)
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/list",
                params = emptyMap()
            )

            val response = mcpApi.sendRequest(
                authorization = null, // Local servers don't need auth
                sessionId = serverSessions[serverId],
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    return Result.failure(Exception("MCP Error: ${body.error.message}"))
                }

                val tools = parseToolsResponse(body.result)
                
                // Update server with tools
                _servers.update { servers ->
                    servers.map { s ->
                        if (s.id == serverId) {
                            s.copy(tools = tools)
                        } else {
                            s
                        }
                    }
                }

                Result.success(tools)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "List tools failed for server $serverId: ${response.code()} - $errorBody")
                Result.failure(Exception("HTTP Error: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing tools for server $serverId", e)
            Result.failure(e)
        }
    }

    override suspend fun callTool(serverId: String, toolName: String, arguments: Map<String, Any>): Result<String> {
        val server = _servers.value.find { it.id == serverId }
            ?: return Result.failure(Exception("Server not found: $serverId"))

        return try {
            if (serverInitialized[serverId] != true) {
                val initResult = initializeSession(server)
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize session"))
                }
                serverInitialized[serverId] = true
            }

            val mcpApi = getMcpApiForServer(server)
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/call",
                params = mapOf(
                    "name" to toolName,
                    "arguments" to arguments
                )
            )

            Log.d(TAG, "Calling tool $toolName on server $serverId")
            val response = mcpApi.sendRequest(
                authorization = null,
                sessionId = serverSessions[serverId],
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    return Result.failure(Exception("MCP Error: ${body.error.message}"))
                }

                val result = parseToolCallResponse(body.result)
                Result.success(result)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Call tool failed for server $serverId: ${response.code()} - $errorBody")
                Result.failure(Exception("HTTP Error: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool $toolName on server $serverId", e)
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

    private suspend fun initializeSession(server: McpServer): Result<Unit> {
        return try {
            val mcpApi = getMcpApiForServer(server)
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
                authorization = null,
                sessionId = null,
                request = initRequest
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    return Result.failure(Exception("MCP Initialize Error: ${body.error.message}"))
                }

                val sessionIdHeader = response.headers()["mcp-session-id"]
                if (sessionIdHeader != null) {
                    serverSessions[server.id] = sessionIdHeader
                    Log.d(TAG, "Session initialized for server ${server.id}: $sessionIdHeader")
                }

                // Send initialized notification
                val initializedRequest = JsonRpcRequest(
                    id = requestId++,
                    method = "notifications/initialized",
                    params = emptyMap()
                )

                mcpApi.sendRequest(
                    authorization = null,
                    sessionId = serverSessions[server.id],
                    request = initializedRequest
                )

                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Initialize failed for server ${server.id}: ${response.code()} - $errorBody")
                Result.failure(Exception("HTTP Error: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing session for server ${server.id}", e)
            Result.failure(e)
        }
    }
}

