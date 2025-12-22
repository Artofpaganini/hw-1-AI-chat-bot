package com.example.aiagentchat.core.common.preferences

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "ai_agent_chat_prefs"
        private const val KEY_WEATHER_NOTIFICATIONS_ENABLED = "weather_notifications_enabled"
        private const val KEY_LAST_USER_QUERY = "last_user_query"
        private const val KEY_ENABLED_MCP_TOOLS = "enabled_mcp_tools"
        private const val KEY_TEST_MODE_ENABLED = "test_mode_enabled"
        private const val KEY_ENABLED_MCP_SERVER_TOOLS = "enabled_mcp_server_tools" // Format: "serverId:tool1,tool2|serverId2:tool1"
        private const val KEY_GOOGLE_DRIVE_ACCESS_TOKEN = "google_drive_access_token"
        private const val KEY_REMOTE_CONTROL_ENABLED = "remote_control_enabled"
        private const val KEY_REMOTE_CONTROL_DEVICE_ID = "remote_control_device_id"
        private const val KEY_OLLAMA_ENABLED = "ollama_enabled"
    }

    var weatherNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEATHER_NOTIFICATIONS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER_NOTIFICATIONS_ENABLED, value).apply()

    var lastUserQuery: String?
        get() = prefs.getString(KEY_LAST_USER_QUERY, null)
        set(value) = prefs.edit().putString(KEY_LAST_USER_QUERY, value).apply()

    fun getEnabledMcpTools(): Set<String> {
        val toolsString = prefs.getString(KEY_ENABLED_MCP_TOOLS, null) ?: return emptySet()
        return if (toolsString.isEmpty()) {
            emptySet()
        } else {
            toolsString.split(",").toSet()
        }
    }

    fun setEnabledMcpTools(tools: Set<String>) {
        val toolsString = tools.joinToString(",")
        prefs.edit().putString(KEY_ENABLED_MCP_TOOLS, toolsString).apply()
    }

    var testModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_TEST_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TEST_MODE_ENABLED, value).apply()
    
    // Format: "serverId:tool1,tool2|serverId2:tool1"
    fun getEnabledMcpServerTools(): Map<String, Set<String>> {
        val toolsString = prefs.getString(KEY_ENABLED_MCP_SERVER_TOOLS, null) ?: return emptyMap()
        if (toolsString.isEmpty()) return emptyMap()
        
        val result = mutableMapOf<String, Set<String>>()
        var needsMigration = false
        toolsString.split("|").forEach { serverEntry ->
            val parts = serverEntry.split(":", limit = 2)
            if (parts.size == 2) {
                var serverId = parts[0]
                // Миграция: заменяем старое имя сервера на новое
                if (serverId == "remote-docker-mcp-server") {
                    serverId = "remote-control-mcp-server"
                    needsMigration = true
                }
                val tools = if (parts[1].isEmpty()) {
                    emptySet()
                } else {
                    parts[1].split(",").toSet()
                }
                result[serverId] = tools
            }
        }
        // Сохраняем мигрированные данные, если была миграция
        if (needsMigration) {
            setEnabledMcpServerTools(result)
        }
        return result
    }
    
    fun setEnabledMcpServerTools(serverTools: Map<String, Set<String>>) {
        val toolsString = serverTools.entries.joinToString("|") { (serverId, tools) ->
            "$serverId:${tools.joinToString(",")}"
        }
        prefs.edit().putString(KEY_ENABLED_MCP_SERVER_TOOLS, toolsString).apply()
    }
    
    fun setEnabledToolsForServer(serverId: String, tools: Set<String>) {
        val current = getEnabledMcpServerTools().toMutableMap()
        current[serverId] = tools
        setEnabledMcpServerTools(current)
    }
    
    fun getEnabledToolsForServer(serverId: String): Set<String> {
        return getEnabledMcpServerTools()[serverId] ?: emptySet()
    }
    
    var googleDriveAccessToken: String?
        get() = prefs.getString(KEY_GOOGLE_DRIVE_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_DRIVE_ACCESS_TOKEN, value).apply()
    
    var remoteControlEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_CONTROL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_REMOTE_CONTROL_ENABLED, value).apply()
    
    var remoteControlDeviceId: String?
        get() = prefs.getString(KEY_REMOTE_CONTROL_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_REMOTE_CONTROL_DEVICE_ID, value ?: "").apply()
    
    var ollamaEnabled: Boolean
        get() = prefs.getBoolean(KEY_OLLAMA_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OLLAMA_ENABLED, value).apply()
}

