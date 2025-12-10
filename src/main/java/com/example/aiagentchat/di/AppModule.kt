package com.example.aiagentchat.di

import com.example.aiagentchat.data.AuthManager
import com.example.aiagentchat.data.repository.AiModelRepositoryImpl
import com.example.aiagentchat.data.repository.MetricsRepositoryImpl
import com.example.aiagentchat.domain.repository.AiModelRepository
import com.example.aiagentchat.domain.repository.MetricsRepository
import com.example.aiagentchat.domain.usecase.CompareModelMetricsUseCase
import com.example.aiagentchat.domain.usecase.ExportChatHistoryUseCase
import com.example.aiagentchat.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.domain.usecase.SwitchAiModelUseCase
import com.example.aiagentchat.presentation.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Data
    single { AuthManager() }
    single<AiModelRepository> { AiModelRepositoryImpl(get()) }
    single<MetricsRepository> { MetricsRepositoryImpl() }
    
    // Domain - Use Cases
    factory { SendMessageUseCase(get(), get()) }
    factory { SwitchAiModelUseCase(get()) }
    factory { CompareModelMetricsUseCase() }
    factory { ExportChatHistoryUseCase() }
    
    // Presentation
    viewModel { 
        ChatViewModel(
            sendMessageUseCase = get(),
            switchAiModelUseCase = get(),
            compareModelMetricsUseCase = get(),
            exportChatHistoryUseCase = get(),
            aiModelRepository = get()
        )
    }
}

