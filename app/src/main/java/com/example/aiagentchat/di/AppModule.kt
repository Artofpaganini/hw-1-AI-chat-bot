package com.example.aiagentchat.di

import android.content.Context
import androidx.room.Room
import com.example.aiagentchat.core.database.ChatDatabase
import com.example.aiagentchat.core.database.dao.ChatMessageDao
import com.example.aiagentchat.core.database.dao.ContextSummaryDao
import com.example.aiagentchat.core.database.dao.UserContextDao
import com.example.aiagentchat.core.database.dao.UserDao
import com.example.aiagentchat.BuildConfig
import com.example.aiagentchat.feature.chat.data.PricingConfig
import com.example.aiagentchat.feature.chat.data.repository.AiModelRepositoryImpl
import com.example.aiagentchat.feature.chat.data.repository.ChatRepositoryImpl
import com.example.aiagentchat.feature.chat.data.repository.MetricsRepositoryImpl
import com.example.aiagentchat.feature.chat.data.repository.PersonalizationRepositoryImpl
import com.example.aiagentchat.feature.chat.data.repository.PreferencesRepositoryImpl
import com.example.aiagentchat.feature.chat.data.repository.PricingRepositoryImpl
import com.example.aiagentchat.feature.chat.data.service.McpContextService
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository
import com.example.aiagentchat.feature.chat.domain.repository.PersonalizationRepository
import com.example.aiagentchat.feature.chat.domain.repository.PreferencesRepository
import com.example.aiagentchat.feature.chat.domain.repository.PricingRepository
import com.example.aiagentchat.feature.chat.domain.usecase.CompareModelMetricsUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.CompressionScheduler
import com.example.aiagentchat.feature.chat.domain.usecase.ContextInitializer
import com.example.aiagentchat.feature.chat.domain.usecase.ExportChatHistoryUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.FallbackSummarizer
import com.example.aiagentchat.feature.chat.domain.usecase.GetDatabaseInfoUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.PersonalizeUserUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.feature.chat.domain.usecase.SwitchAiModelUseCase
import com.example.aiagentchat.feature.chat.presentation.chat.ChatViewModel
import com.example.aiagentchat.data.speech.SpeechRecognizerManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

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
    single<UserDao> { get<ChatDatabase>().userDao() }
    single<UserContextDao> { get<ChatDatabase>().userContextDao() }

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
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get(), get()) }
    single<AiModelRepository> { AiModelRepositoryImpl(get()) }
    single<MetricsRepository> { MetricsRepositoryImpl(get()) }
    single<McpContextService> { McpContextService(get(), get()) }
    single<PersonalizationRepository> { PersonalizationRepositoryImpl(get(), get(), get()) }
    single<PreferencesRepository> { PreferencesRepositoryImpl(androidContext()) }
    single { SpeechRecognizerManager(androidContext()) }

    factory { SendMessageUseCase(get(), get()) }
    factory { SwitchAiModelUseCase(get()) }
    factory { CompareModelMetricsUseCase() }
    factory { ExportChatHistoryUseCase() }
    factory { GetDatabaseInfoUseCase(get(), get(), get(), get()) }
    factory { PersonalizeUserUseCase(get(), get()) }

    viewModel {
        ChatViewModel(
            sendMessageUseCase = get(),
            switchAiModelUseCase = get(),
            compareModelMetricsUseCase = get(),
            exportChatHistoryUseCase = get(),
            getDatabaseInfoUseCase = get(),
            personalizeUserUseCase = get(),
            aiModelRepository = get(),
            chatRepository = get(),
            preferencesRepository = get(),
            personalizationRepository = get(),
            compressionScheduler = get(),
            contextInitializer = get(),
            speechRecognizerManager = get()
        )
    }

    factory { FallbackSummarizer() }
    factory { ContextInitializer(get()) }
    factory { CompressionScheduler(get(), get(), get()) }
}

