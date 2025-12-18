package com.example.aiagentchat

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import com.example.aiagentchat.core.common.preferences.PreferencesManager
import android.util.Log

class AiAgentChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AiAgentChatApplication)
            modules(com.example.aiagentchat.di.appModule)
        }
        
        // Initialize Google Drive access token from BuildConfig if available
        val preferencesManager = PreferencesManager(this)
        if (BuildConfig.GOOGLE_DRIVE_ACCESS_TOKEN.isNotBlank() && 
            preferencesManager.googleDriveAccessToken.isNullOrBlank()) {
            preferencesManager.googleDriveAccessToken = BuildConfig.GOOGLE_DRIVE_ACCESS_TOKEN
            Log.d("AiAgentChatApplication", "Google Drive access token initialized from BuildConfig (local.properties)")
        }
        
        val workerFactory = GlobalContext.get().get<com.example.aiagentchat.di.WeatherWorkerFactory>()
        val configuration = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG) // Для отладки
            .build()
        WorkManager.initialize(this, configuration)
        
        Log.d("AiAgentChatApplication", "WorkManager initialized - will work even after app is killed")
    }
}

