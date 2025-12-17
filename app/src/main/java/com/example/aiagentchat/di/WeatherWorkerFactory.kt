package com.example.aiagentchat.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.aiagentchat.feature.chat.data.worker.WeatherNotificationWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WeatherWorkerFactory : WorkerFactory(), KoinComponent {
    private val preferencesManager: com.example.aiagentchat.core.common.preferences.PreferencesManager by inject()
    private val mcpRepository: com.example.aiagentchat.feature.chat.domain.repository.McpRepository by inject()
    private val weatherDataStorage: com.example.aiagentchat.feature.chat.data.storage.WeatherDataStorage by inject()
    private val weatherSummaryUseCase: com.example.aiagentchat.feature.chat.domain.usecase.WeatherSummaryUseCase by inject()
    private val notificationManager: com.example.aiagentchat.feature.chat.data.notification.NotificationManager by inject()

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            WeatherNotificationWorker::class.java.name -> {
                WeatherNotificationWorker(
                    context = appContext,
                    params = workerParameters,
                    preferencesManager = preferencesManager,
                    mcpRepository = mcpRepository,
                    weatherDataStorage = weatherDataStorage,
                    weatherSummaryUseCase = weatherSummaryUseCase,
                    notificationManager = notificationManager
                )
            }
            else -> null
        }
    }
}

