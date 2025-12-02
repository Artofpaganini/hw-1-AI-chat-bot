package com.example.aiagentchat.core.domain.di

import com.example.aiagentchat.core.domain.repository.ChatRepository
import com.example.aiagentchat.core.domain.usecase.ClearHistoryUseCase
import com.example.aiagentchat.core.domain.usecase.GetApiKeyUseCase
import com.example.aiagentchat.core.domain.usecase.GetMessagesUseCase
import com.example.aiagentchat.core.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.core.domain.usecase.SetApiKeyUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { SendMessageUseCase(get<ChatRepository>()) }
    factory { GetMessagesUseCase(get<ChatRepository>()) }
    factory { ClearHistoryUseCase(get<ChatRepository>()) }
    factory { SetApiKeyUseCase(get<ChatRepository>()) }
    factory { GetApiKeyUseCase(get<ChatRepository>()) }
}

