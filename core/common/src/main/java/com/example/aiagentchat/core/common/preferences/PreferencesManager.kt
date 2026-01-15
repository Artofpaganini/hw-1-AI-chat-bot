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
        private const val KEY_LAST_USER_QUERY = "last_user_query"
        private const val KEY_OLLAMA_ENABLED = "ollama_enabled"
        private const val KEY_RERANKING_ENABLED = "reranking_enabled"
        private const val KEY_PROJECT_REVIEW_MODE_ENABLED = "project_review_mode_enabled"
        private const val KEY_PROJECT_TEAM_ASSISTANT_ENABLED = "project_team_assistant_enabled"
        private const val KEY_LOCAL_MCP_SERVER_ENABLED = "local_mcp_server_enabled"
        private const val KEY_GITHUB_MCP_ENABLED = "github_mcp_enabled"
        private const val KEY_PROJECT_ROOT_PATH = "project_root_path"
    }

    var lastUserQuery: String?
        get() = prefs.getString(KEY_LAST_USER_QUERY, null)
        set(value) = prefs.edit().putString(KEY_LAST_USER_QUERY, value).apply()
    
    var ollamaEnabled: Boolean
        get() = prefs.getBoolean(KEY_OLLAMA_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OLLAMA_ENABLED, value).apply()
    
    var rerankingEnabled: Boolean
        get() = prefs.getBoolean(KEY_RERANKING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_RERANKING_ENABLED, value).apply()
    
    var projectReviewModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROJECT_REVIEW_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROJECT_REVIEW_MODE_ENABLED, value).apply()
    
    var projectTeamAssistantEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROJECT_TEAM_ASSISTANT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROJECT_TEAM_ASSISTANT_ENABLED, value).apply()
    
    var localMcpServerEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCAL_MCP_SERVER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCAL_MCP_SERVER_ENABLED, value).apply()
    
    var githubMcpEnabled: Boolean
        get() = prefs.getBoolean(KEY_GITHUB_MCP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GITHUB_MCP_ENABLED, value).apply()
    
    var projectRootPath: String?
        get() = prefs.getString(KEY_PROJECT_ROOT_PATH, null)
        set(value) = prefs.edit().putString(KEY_PROJECT_ROOT_PATH, value).apply()
    
    fun resetAllToggles() {
        prefs.edit()
            .putBoolean(KEY_OLLAMA_ENABLED, false)
            .putBoolean(KEY_RERANKING_ENABLED, false)
            .putBoolean(KEY_PROJECT_REVIEW_MODE_ENABLED, false)
            .putBoolean(KEY_PROJECT_TEAM_ASSISTANT_ENABLED, false)
            .putBoolean(KEY_LOCAL_MCP_SERVER_ENABLED, false)
            .putBoolean(KEY_GITHUB_MCP_ENABLED, false)
            .apply()
    }
}

