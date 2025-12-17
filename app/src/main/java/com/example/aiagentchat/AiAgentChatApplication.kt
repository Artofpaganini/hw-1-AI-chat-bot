package com.example.aiagentchat

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class AiAgentChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AiAgentChatApplication)
            modules(com.example.aiagentchat.di.appModule)
        }
        
        val workerFactory = GlobalContext.get().get<com.example.aiagentchat.di.WeatherWorkerFactory>()
        val configuration = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG) // Для отладки
            .build()
        WorkManager.initialize(this, configuration)
        
        android.util.Log.d("AiAgentChatApplication", "WorkManager initialized - will work even after app is killed")
    }
}

