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
        private const val KEY_OLLAMA_SELECTED_FILES = "ollama_selected_files" // JSON array of file paths
        private const val KEY_RERANKING_ENABLED = "reranking_enabled"
        private const val KEY_PROJECT_HELPER_ENABLED = "project_helper_enabled"
        private const val KEY_GITHUB_MCP_ENABLED = "github_mcp_enabled"
        private const val KEY_PROJECT_REVIEW_MODE_ENABLED = "project_review_mode_enabled"
        private const val KEY_PROJECT_ROOT_PATH = "project_root_path"
        private const val KEY_PROJECT_USER_ASSISTANT_ENABLED = "project_user_assistant_enabled"
        private const val KEY_USER_FORMAT_TYPE = "user_format_type"
        private const val KEY_PROJECT_FILES_ENABLED = "project_files_enabled"
        private const val KEY_PROJECT_ANALYTIC_ENABLED = "project_analytic_enabled"
    }

    var lastUserQuery: String?
        get() = prefs.getString(KEY_LAST_USER_QUERY, null)
        set(value) = prefs.edit().putString(KEY_LAST_USER_QUERY, value).apply()
    
    var ollamaEnabled: Boolean
        get() = prefs.getBoolean(KEY_OLLAMA_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OLLAMA_ENABLED, value).apply()
    
    var ollamaSelectedFiles: List<String>
        get() {
            val filesString = prefs.getString(KEY_OLLAMA_SELECTED_FILES, null) ?: return emptyList()
            if (filesString.isBlank()) return emptyList()
            return try {
                com.google.gson.Gson().fromJson(filesString, Array<String>::class.java).toList()
            } catch (e: Exception) {
                // Fallback: если не JSON, пытаемся прочитать как один файл (миграция)
                val singleFile = prefs.getString("ollama_selected_file", null)?.takeIf { it.isNotBlank() }
                if (singleFile != null) {
                    listOf(singleFile)
                } else {
                    emptyList()
                }
            }
        }
        set(value) {
            val filesString = com.google.gson.Gson().toJson(value)
            prefs.edit().putString(KEY_OLLAMA_SELECTED_FILES, filesString).apply()
        }
    
    // Legacy support - для обратной совместимости
    @Deprecated("Use ollamaSelectedFiles instead", ReplaceWith("ollamaSelectedFiles.firstOrNull()"))
    var ollamaSelectedFile: String?
        get() = ollamaSelectedFiles.firstOrNull()
        set(value) {
            ollamaSelectedFiles = if (value != null) listOf(value) else emptyList()
        }
    
    var rerankingEnabled: Boolean
        get() = prefs.getBoolean(KEY_RERANKING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_RERANKING_ENABLED, value).apply()
    
    var projectHelperEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROJECT_HELPER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROJECT_HELPER_ENABLED, value).apply()
    
    var githubMcpEnabled: Boolean
        get() = prefs.getBoolean(KEY_GITHUB_MCP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GITHUB_MCP_ENABLED, value).apply()
    
    var projectReviewModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROJECT_REVIEW_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROJECT_REVIEW_MODE_ENABLED, value).apply()
    
    var projectRootPath: String?
        get() = prefs.getString(KEY_PROJECT_ROOT_PATH, null)
        set(value) = prefs.edit().putString(KEY_PROJECT_ROOT_PATH, value).apply()
    
    var projectUserAssistantEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROJECT_USER_ASSISTANT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROJECT_USER_ASSISTANT_ENABLED, value).apply()
    
    var userFormatType: String
        get() = prefs.getString(KEY_USER_FORMAT_TYPE, "программист") ?: "программист"
        set(value) = prefs.edit().putString(KEY_USER_FORMAT_TYPE, value).apply()
    
    var projectFilesEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROJECT_FILES_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROJECT_FILES_ENABLED, value).apply()
    
    var projectAnalyticEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROJECT_ANALYTIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROJECT_ANALYTIC_ENABLED, value).apply()
}

