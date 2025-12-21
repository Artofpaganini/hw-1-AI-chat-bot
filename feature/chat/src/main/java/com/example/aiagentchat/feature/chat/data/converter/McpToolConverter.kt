package com.example.aiagentchat.feature.chat.data.converter

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.ToolDto
import com.example.aiagentchat.feature.chat.data.api.ToolFunctionDto
import com.example.aiagentchat.feature.chat.data.api.ToolParametersDto
import com.example.aiagentchat.feature.chat.data.api.ToolPropertyDto
import com.example.aiagentchat.feature.chat.domain.model.McpTool
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object McpToolConverter {
    private const val TAG = "McpToolConverter"
    private val gson = Gson()

    fun convertToAiTools(mcpTools: List<McpTool>, deviceId: String? = null): List<ToolDto> {
        return mcpTools.mapNotNull { mcpTool ->
            try {
                val inputSchema = mcpTool.inputSchema ?: emptyMap()
                val properties = parseProperties(inputSchema)
                val required = parseRequired(inputSchema)

                // Модифицируем описание для инструментов Remote Control, если deviceId указан
                var description = mcpTool.description ?: "MCP tool: ${mcpTool.name}"
                if (deviceId != null && isRemoteControlTool(mcpTool.name)) {
                    description += " CRITICAL: Device ID '$deviceId' is already configured in app settings and will be automatically used. " +
                            "You MUST NOT ask the user which device to use. " +
                            "You MUST use device ID '$deviceId' automatically for all device operations. " +
                            "Do NOT include 'deviceId' parameter in your tool call arguments - it will be added automatically. " +
                            "Only if the user explicitly requests a different device, you may include a different deviceId in arguments."
                }

                ToolDto(
                    type = "function",
                    function = ToolFunctionDto(
                        name = mcpTool.name,
                        description = description,
                        parameters = ToolParametersDto(
                            type = "object",
                            properties = properties,
                            required = required.takeIf { it.isNotEmpty() }
                        )
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error converting MCP tool ${mcpTool.name}", e)
                null
            }
        }
    }

    private fun isRemoteControlTool(toolName: String): Boolean {
        val remoteControlTools = listOf(
            "list_devices",
            "check_adb_availability",
            "press_home",
            "press_back",
            "open_app",
            "minimize_app",
            "take_screenshot",
            "execute_adb_command"
        )
        return remoteControlTools.contains(toolName)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseProperties(inputSchema: Map<String, Any>): Map<String, ToolPropertyDto> {
        val properties = mutableMapOf<String, ToolPropertyDto>()
        
        try {
            val propertiesMap = inputSchema["properties"] as? Map<String, Any> ?: return properties
            
            propertiesMap.forEach { (key, value) ->
                val propMap = value as? Map<String, Any> ?: return@forEach
                val type = propMap["type"] as? String ?: "string"
                val description = propMap["description"] as? String
                
                properties[key] = ToolPropertyDto(
                    type = type,
                    description = description
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing properties", e)
        }
        
        return properties
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRequired(inputSchema: Map<String, Any>): List<String> {
        return try {
            (inputSchema["required"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing required fields", e)
            emptyList()
        }
    }

    fun parseToolCallArguments(argumentsJson: String): Map<String, Any> {
        return try {
            val jsonObject = JsonParser.parseString(argumentsJson).asJsonObject
            jsonObject.keySet().associateWith { key ->
                val element = jsonObject.get(key)
                when {
                    element.isJsonPrimitive -> {
                        val primitive = element.asJsonPrimitive
                        when {
                            primitive.isString -> primitive.asString
                            primitive.isNumber -> primitive.asNumber
                            primitive.isBoolean -> primitive.asBoolean
                            else -> primitive.asString
                        }
                    }
                    element.isJsonObject -> element.asJsonObject
                    element.isJsonArray -> element.asJsonArray
                    else -> element.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tool call arguments", e)
            emptyMap()
        }
    }
}

