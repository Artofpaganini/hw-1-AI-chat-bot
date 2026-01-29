package com.example.aiagentchat.feature.chat.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.aiagentchat.core.common.preferences.PreferencesManager
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreferencesRepositoryImpl(
    private val context: Context
) : PreferencesRepository {

    companion object {
        private const val PREFS_NAME = "ai_chat_preferences"
        private const val KEY_SELECTED_MODEL = "selected_model"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }

    override suspend fun saveSelectedModel(model: AiModel) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_SELECTED_MODEL, model.displayName)
                .apply()
        }
    }

    override suspend fun getSelectedModel(): AiModel? {
        return withContext(Dispatchers.IO) {
            val modelName = prefs.getString(KEY_SELECTED_MODEL, null)
            modelName?.let { AiModel.fromDisplayName(it) }
        }
    }
    
    override suspend fun getOllamaEnabled(): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.ollamaEnabled
    }
    
    override suspend fun setOllamaEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.ollamaEnabled = enabled
    }
    
    override suspend fun getOllamaSelectedFiles(): List<String> = withContext(Dispatchers.IO) {
        preferencesManager.ollamaSelectedFiles
    }
    
    override suspend fun setOllamaSelectedFiles(files: List<String>) = withContext(Dispatchers.IO) {
        preferencesManager.ollamaSelectedFiles = files
    }
    
    override suspend fun getRerankingEnabled(): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.rerankingEnabled
    }
    
    override suspend fun setRerankingEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.rerankingEnabled = enabled
    }
    
    override suspend fun getProjectHelperEnabled(): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.projectHelperEnabled
    }
    
    override suspend fun setProjectHelperEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.projectHelperEnabled = enabled
    }
    
    override suspend fun getProjectUserAssistantEnabled(): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.projectUserAssistantEnabled
    }
    
    override suspend fun setProjectUserAssistantEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.projectUserAssistantEnabled = enabled
    }
    
    override suspend fun getUserFormatType(): String = withContext(Dispatchers.IO) {
        preferencesManager.userFormatType
    }
    
    override suspend fun setUserFormatType(formatType: String) = withContext(Dispatchers.IO) {
        preferencesManager.userFormatType = formatType
    }
    
    override suspend fun getProjectFilesEnabled(): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.projectFilesEnabled
    }
    
    override suspend fun setProjectFilesEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.projectFilesEnabled = enabled
    }
    
    override suspend fun getProjectAnalyticEnabled(): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.projectAnalyticEnabled
    }
    
    override suspend fun setProjectAnalyticEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.projectAnalyticEnabled = enabled
    }
}
