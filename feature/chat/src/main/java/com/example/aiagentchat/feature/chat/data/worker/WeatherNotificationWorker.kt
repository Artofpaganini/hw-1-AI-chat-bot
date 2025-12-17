package com.example.aiagentchat.feature.chat.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aiagentchat.core.common.preferences.PreferencesManager
import com.example.aiagentchat.core.common.utils.AppStateHelper
import com.example.aiagentchat.feature.chat.data.notification.NotificationManager
import com.example.aiagentchat.feature.chat.data.storage.WeatherDataStorage
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.usecase.WeatherSummaryUseCase

class WeatherNotificationWorker(
    context: Context,
    params: WorkerParameters,
    private val preferencesManager: PreferencesManager,
    private val mcpRepository: McpRepository,
    private val weatherDataStorage: WeatherDataStorage,
    private val weatherSummaryUseCase: WeatherSummaryUseCase,
    private val notificationManager: NotificationManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WeatherNotificationWorker"
        private const val WORK_NAME = "weather_notification_work"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "WeatherNotificationWorker started - doWork() called")

            if (!preferencesManager.weatherNotificationsEnabled) {
                Log.d(TAG, "Weather notifications are disabled, skipping")
                return Result.success()
            }

            // Проверка: работаем только когда приложение в фоне или убито
            // WorkManager уже работает в фоне, но для дополнительной проверки
            // можно проверить состояние приложения
            try {
                val isForeground = AppStateHelper.isAppInForeground(applicationContext)
                if (isForeground) {
                    Log.d(TAG, "App is in foreground, skipping worker execution")
                    // Не завершаем задачу, а просто пропускаем выполнение
                    // Это позволит задаче выполниться позже, когда приложение будет в фоне
                    return Result.success()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking app state, continuing execution", e)
                // При ошибке продолжаем выполнение
            }

            val lastUserQuery = preferencesManager.lastUserQuery
            if (lastUserQuery.isNullOrBlank()) {
                Log.d(TAG, "No last user query found, skipping")
                return Result.success()
            }

            Log.d(TAG, "Processing weather notification for query: $lastUserQuery")

            val weatherResult = mcpRepository.callTool(
                toolName = "get_weather",
                arguments = mapOf("location" to lastUserQuery)
            )

            if (weatherResult.isSuccess) {
                val weatherData = weatherResult.getOrNull() ?: ""
                Log.d(TAG, "Received weather data: $weatherData")

                // Генерируем summary сразу после получения данных
                val summaryResult = weatherSummaryUseCase.generateSummary(
                    model = AiModel.DeepSeek,
                    weatherData = weatherData,
                    location = lastUserQuery
                )

                if (summaryResult.isSuccess) {
                    val summary = summaryResult.getOrNull() ?: ""
                    Log.d(TAG, "Generated summary: ${summary.take(100)}...")
                    
                    // Сохраняем только summary (перезаписываем предыдущие данные)
                    val saveResult = weatherDataStorage.saveWeatherSummary(lastUserQuery, summary)
                    if (saveResult.isSuccess) {
                        Log.d(TAG, "Summary saved successfully")
                    } else {
                        Log.e(TAG, "Failed to save summary", saveResult.exceptionOrNull())
                    }
                    
                    // Отправляем уведомление
                    notificationManager.showWeatherSummaryNotification(summary)
                    Log.d(TAG, "Notification sent with summary")
                } else {
                    val error = summaryResult.exceptionOrNull()
                    Log.e(TAG, "Failed to generate summary", error)
                }
            } else {
                Log.e(TAG, "Failed to get weather data", weatherResult.exceptionOrNull())
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in WeatherNotificationWorker", e)
            Result.retry()
        }
    }
}

