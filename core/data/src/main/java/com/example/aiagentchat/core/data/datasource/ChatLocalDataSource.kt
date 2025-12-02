package com.example.aiagentchat.core.data.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChatLocalDataSource {
    fun getApiKey(): Flow<String?>
    suspend fun setApiKey(apiKey: String)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_preferences")

class ChatLocalDataSourceImpl(
    private val context: Context
) : ChatLocalDataSource {
    companion object {
        private val API_KEY_KEY = stringPreferencesKey("api_key")
    }

    override fun getApiKey(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[API_KEY_KEY]
        }
    }

    override suspend fun setApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY_KEY] = apiKey
        }
    }
}

