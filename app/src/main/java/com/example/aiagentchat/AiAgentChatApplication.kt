package com.example.aiagentchat

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import android.util.Log

class AiAgentChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AiAgentChatApplication)
            modules(com.example.aiagentchat.di.appModule)
        }
        
        Log.d("AiAgentChatApplication", "Application initialized")
    }
}

