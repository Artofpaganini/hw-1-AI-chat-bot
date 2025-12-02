package com.example.aiagentchat.core.data.di

import android.content.Context
import com.example.aiagentchat.core.data.datasource.ChatLocalDataSource
import com.example.aiagentchat.core.data.datasource.ChatLocalDataSourceImpl
import com.example.aiagentchat.core.data.datasource.ChatRemoteDataSource
import com.example.aiagentchat.core.data.datasource.ChatRemoteDataSourceImpl
import com.example.aiagentchat.core.data.repository.ChatRepositoryImpl
import com.example.aiagentchat.core.domain.repository.ChatRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single<ChatLocalDataSource> { ChatLocalDataSourceImpl(androidContext()) }
    single<ChatRemoteDataSource> { ChatRemoteDataSourceImpl() }
    single<ChatRepository> {
        ChatRepositoryImpl(
            remoteDataSource = get(),
            localDataSource = get()
        )
    }
}

