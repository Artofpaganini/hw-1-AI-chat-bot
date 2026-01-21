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
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository
import com.example.aiagentchat.feature.chat.domain.repository.PricingRepository
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
    single<com.example.aiagentchat.core.database.dao.VectorDao> { get<ChatDatabase>().vectorDao() }

    single {
        val preferencesManager = get<com.example.aiagentchat.core.common.preferences.PreferencesManager>()
        com.example.aiagentchat.feature.chat.data.AuthManager(
            deepSeekApiKey = BuildConfig.DEEPSEEK_API_KEY,
            openRouterApiKey = BuildConfig.OPENROUTER_API_KEY,
            vpsOllamaUrl = preferencesManager.vpsOllamaUrl
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
    single<AiModelRepository> { AiModelRepositoryImpl(get(), get()) }
    single<MetricsRepository> { MetricsRepositoryImpl(get()) }
    
    single<Gson> { Gson() }

    factory { SendMessageUseCase(get(), get()) }
    factory { SwitchAiModelUseCase(get()) }
    factory { CompareModelMetricsUseCase() }
    factory { ExportChatHistoryUseCase() }

    factory { FallbackSummarizer() }
    factory { ContextInitializer(get()) }
    factory { CompressionScheduler(get(), get(), get()) }
    
    single<com.example.aiagentchat.core.common.preferences.PreferencesManager> {
        com.example.aiagentchat.core.common.preferences.PreferencesManager(androidContext())
    }
    
    single<com.example.aiagentchat.feature.chat.data.api.OllamaApi> {
        com.example.aiagentchat.feature.chat.data.api.OllamaApi.create()
    }
    
    single<com.example.aiagentchat.feature.chat.data.service.VectorDatabaseService> {
        com.example.aiagentchat.feature.chat.data.service.VectorDatabaseService(
            indexedBookDao = get<com.example.aiagentchat.core.database.ChatDatabase>().indexedBookDao(),
            bookChunkDao = get<com.example.aiagentchat.core.database.ChatDatabase>().bookChunkDao()
        )
    }
    
    single<com.example.aiagentchat.feature.chat.data.service.TextIndexingService> {
        com.example.aiagentchat.feature.chat.data.service.TextIndexingService(
            context = androidContext(),
            ollamaApi = get(),
            vectorDatabaseService = get()
        )
    }
    
    single<com.example.aiagentchat.feature.chat.domain.repository.VectorRepository> {
        com.example.aiagentchat.feature.chat.data.repository.VectorRepositoryImpl(
            vectorDao = get()
        )
    }
    
    single<com.example.aiagentchat.feature.chat.data.service.GitFileDetector> {
        val preferencesManager = get<com.example.aiagentchat.core.common.preferences.PreferencesManager>()
        // Try to get projectRootPath from SharedPreferences first, then from BuildConfig
        val projectRootPath = preferencesManager.projectRootPath?.takeIf { it.isNotBlank() }
            ?: BuildConfig.PROJECT_ROOT.takeIf { it.isNotBlank() }
        android.util.Log.d("AppModule", "GitFileDetector projectRootPath: $projectRootPath")
        com.example.aiagentchat.feature.chat.data.service.GitFileDetector(
            projectRoot = projectRootPath?.let { java.io.File(it) },
            projectRootPath = projectRootPath
        )
    }
    
    single<com.example.aiagentchat.feature.chat.data.repository.ReviewRepository> {
        com.example.aiagentchat.feature.chat.data.repository.ReviewRepository(
            gitFileDetector = get(),
            textIndexingService = get(),
            vectorDatabaseService = get(),
            ollamaApi = get(),
            githubMcpApi = null // Will be initialized lazily in ViewModel when GitHub MCP is enabled
        )
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
            preferencesManager = get(),
            textIndexingService = get(),
            vectorDatabaseService = get(),
            ollamaApi = get(),
            reviewRepository = get()
        )
    }
}

