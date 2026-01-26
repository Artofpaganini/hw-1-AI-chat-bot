package com.example.aiagentchat.feature.chat.data.repository

import android.content.Context
import android.content.SharedPreferences
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
}
