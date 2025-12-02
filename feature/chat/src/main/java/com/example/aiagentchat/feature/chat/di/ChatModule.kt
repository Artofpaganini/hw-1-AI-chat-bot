package com.example.aiagentchat.feature.chat.di

import com.example.aiagentchat.core.domain.usecase.ClearHistoryUseCase
import com.example.aiagentchat.core.domain.usecase.GetApiKeyUseCase
import com.example.aiagentchat.core.domain.usecase.GetMessagesUseCase
import com.example.aiagentchat.core.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.core.domain.usecase.SetApiKeyUseCase
import com.example.aiagentchat.feature.chat.presentation.viewmodel.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val chatModule = module {
    viewModel {
        ChatViewModel(
            sendMessageUseCase = get<SendMessageUseCase>(),
            getMessagesUseCase = get<GetMessagesUseCase>(),
            clearHistoryUseCase = get<ClearHistoryUseCase>(),
            setApiKeyUseCase = get<SetApiKeyUseCase>(),
            getApiKeyUseCase = get<GetApiKeyUseCase>()
        )
    }
}

