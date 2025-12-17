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
}

