package com.example.aiagentchat.di

import android.content.Context
import androidx.room.Room
import com.example.aiagentchat.core.database.ChatDatabase
import com.example.aiagentchat.core.database.dao.ChatMessageDao
import com.example.aiagentchat.core.database.dao.ContextSummaryDao
import com.example.aiagentchat.BuildConfig
import com.example.aiagentchat.feature.chat.data.PricingConfig
import com.example.aiagentchat.feature.chat.data.repository.AiModelRepositoryImpl
import com.example.aiagentchat.feature.chat.data.repository.ChatRepositoryImpl
import com.example.aiagentchat.feature.chat.data.repository.MetricsRepositoryImpl
import com.example.aiagentchat.feature.chat.data.repository.PricingRepositoryImpl
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository
import com.example.aiagentchat.feature.chat.domain.repository.PricingRepository
import com.example.aiagentchat.feature.chat.data.repository.McpRepositoryImpl
import com.example.aiagentchat.feature.chat.data.api.McpApi
import com.example.aiagentchat.core.network.ApiClient
import com.google.gson.Gson
import com.example.aiagentchat.feature.chat.domain.usecase.CompareModelMetricsUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.CompressionScheduler
import com.example.aiagentchat.feature.chat.domain.usecase.ContextInitializer
import com.example.aiagentchat.feature.chat.domain.usecase.ExportChatHistoryUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.FallbackSummarizer
import com.example.aiagentchat.feature.chat.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SwitchAiModelUseCase
import com.example.aiagentchat.feature.chat.presentation.chat.ChatViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.error.InstanceCreationException
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import android.util.Log

val appModule = module {
    single<ChatDatabase> {
        Room.databaseBuilder(
            androidContext(),
            ChatDatabase::class.java,
            "chat_database"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    single<ChatMessageDao> { get<ChatDatabase>().chatMessageDao() }
    single<ContextSummaryDao> { get<ChatDatabase>().contextSummaryDao() }

    single {
        com.example.aiagentchat.feature.chat.data.AuthManager(
            deepSeekApiKey = BuildConfig.DEEPSEEK_API_KEY,
            openRouterApiKey = BuildConfig.OPENROUTER_API_KEY
        )
    }
    single {
        PricingConfig(
            deepSeekInputPrice = BuildConfig.DEEPSEEK_INPUT_PRICE,
            deepSeekOutputPrice = BuildConfig.DEEPSEEK_OUTPUT_PRICE,
            claude35SonnetInputPrice = BuildConfig.CLAUDE_35_SONNET_INPUT_PRICE,
            claude35SonnetOutputPrice = BuildConfig.CLAUDE_35_SONNET_OUTPUT_PRICE,
            gpt4oMiniInputPrice = BuildConfig.GPT_4O_MINI_INPUT_PRICE,
            gpt4oMiniOutputPrice = BuildConfig.GPT_4O_MINI_OUTPUT_PRICE,
            geminiPro15InputPrice = BuildConfig.GEMINI_PRO_15_INPUT_PRICE,
            geminiPro15OutputPrice = BuildConfig.GEMINI_PRO_15_OUTPUT_PRICE
        )
    }
    single<PricingRepository> { PricingRepositoryImpl(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get()) }
    single<AiModelRepository> { AiModelRepositoryImpl(get()) }
    single<MetricsRepository> { MetricsRepositoryImpl(get()) }
    
    single<Gson> { Gson() }
    
    single<McpApi> {
        try {
            val mcpUrl = BuildConfig.MCP_SERVER_URL
            require(mcpUrl.isNotBlank()) { "MCP_SERVER_URL is blank" }
            val baseUrl = if (mcpUrl.endsWith("/")) mcpUrl else "$mcpUrl/"
            ApiClient.createRetrofit(baseUrl).create(McpApi::class.java)
        } catch (e: Exception) {
            Log.e("AppModule", "Failed to create McpApi", e)
            throw InstanceCreationException("Could not create McpApi: ${e.message}", e)
        }
    }
    
    single<McpRepository> { 
        McpRepositoryImpl(
            mcpApi = get(),
            context7ApiKey = BuildConfig.CONTEXT7_API_KEY.takeIf { it.isNotBlank() },
            gson = get()
        )
    }
    
    single<com.example.aiagentchat.feature.chat.domain.repository.MultiMcpRepository> {
        com.example.aiagentchat.feature.chat.data.repository.MultiMcpRepositoryImpl(
            gson = get()
        )
    }

    factory { SendMessageUseCase(get(), get(), get(), get(), get<com.example.aiagentchat.core.common.preferences.PreferencesManager>(), get<Gson>()) }
    factory { SwitchAiModelUseCase(get()) }
    factory { CompareModelMetricsUseCase() }
    factory { ExportChatHistoryUseCase() }

    factory { FallbackSummarizer() }
    factory { ContextInitializer(get()) }
    factory { CompressionScheduler(get(), get(), get()) }
    
    single<com.example.aiagentchat.core.common.preferences.PreferencesManager> {
        com.example.aiagentchat.core.common.preferences.PreferencesManager(androidContext())
    }
    
    single<com.example.aiagentchat.feature.chat.data.storage.WeatherDataStorage> {
        com.example.aiagentchat.feature.chat.data.storage.WeatherDataStorage(
            context = androidContext(),
            gson = get()
        )
    }
    
    single<com.example.aiagentchat.feature.chat.data.notification.NotificationManager> {
        com.example.aiagentchat.feature.chat.data.notification.NotificationManager(androidContext())
    }
    
    factory<com.example.aiagentchat.feature.chat.domain.usecase.WeatherSummaryUseCase> {
        com.example.aiagentchat.feature.chat.domain.usecase.WeatherSummaryUseCase(get())
    }
    
    single<com.example.aiagentchat.feature.chat.data.worker.WeatherWorkManager> {
        com.example.aiagentchat.feature.chat.data.worker.WeatherWorkManager(androidContext())
    }
    
    single<com.example.aiagentchat.di.WeatherWorkerFactory> {
        com.example.aiagentchat.di.WeatherWorkerFactory()
    }

    viewModel {
        ChatViewModel(
            sendMessageUseCase = get(),
            switchAiModelUseCase = get(),
            compareModelMetricsUseCase = get(),
            exportChatHistoryUseCase = get(),
            aiModelRepository = get(),
            chatRepository = get(),
            compressionScheduler = get(),
            contextInitializer = get(),
            mcpRepository = get(),
            multiMcpRepository = get(),
            preferencesManager = get(),
            weatherWorkManager = get()
        )
    }
}

