package com.example.aiagentchat

import android.app.Application
import com.example.aiagentchat.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AiAgentChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@AiAgentChatApplication)
            modules(appModule)
        }
    }
}




