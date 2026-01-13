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
}

