package com.example.aiagentchat.feature.chat.domain.model

data class McpServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val port: Int,
    val tools: List<McpTool> = emptyList()
) {
    companion object {
        val DEFAULT_SERVERS = emptyList<McpServer>()
    }
    
    fun getFullUrl(): String {
        return "$baseUrl:$port"
    }
}

