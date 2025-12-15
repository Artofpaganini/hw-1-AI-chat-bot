package com.example.aiagentchat.feature.chat.domain.model

data class McpTool(
    val name: String,
    val description: String?,
    val inputSchema: Map<String, Any>? = null
)

data class McpToolsResponse(
    val tools: List<McpTool>
)

