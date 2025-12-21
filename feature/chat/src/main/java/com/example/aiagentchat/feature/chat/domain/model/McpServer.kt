package com.example.aiagentchat.feature.chat.domain.model

data class McpServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val port: Int,
    val tools: List<McpTool> = emptyList()
) {
    companion object {
        const val WEATHER_SERVER_ID = "weather-mcp-server"
        const val GOOGLE_STORAGE_SERVER_ID = "google-storage-mcp-server"
        const val REMOTE_CONTROL_SERVER_ID = "remote-control-mcp-server"
        
        val DEFAULT_SERVERS = listOf(
            McpServer(
                id = WEATHER_SERVER_ID,
                name = "Weather MCP Server",
                baseUrl = "http://10.0.2.2",
                port = 8080,
                tools = emptyList()
            ),
            McpServer(
                id = GOOGLE_STORAGE_SERVER_ID,
                name = "Google Storage MCP Server",
                baseUrl = "http://10.0.2.2",
                port = 8081,
                tools = emptyList()
            ),
            McpServer(
                id = REMOTE_CONTROL_SERVER_ID,
                name = "Remote Control MCP Server",
                baseUrl = "http://10.0.2.2",
                port = 8082,
                tools = emptyList()
            )
        )
    }
    
    fun getFullUrl(): String {
        return "$baseUrl:$port"
    }
}

