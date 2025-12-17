package com.example.aiagentchat.feature.chat.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.aiagentchat.core.common.preferences.PreferencesManager
import com.example.aiagentchat.core.common.utils.AppStateHelper
import com.example.aiagentchat.feature.chat.data.notification.NotificationManager as WeatherNotificationManager
import com.example.aiagentchat.feature.chat.data.storage.WeatherDataStorage
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.usecase.WeatherSummaryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WeatherNotificationService : Service(), KoinComponent {
    companion object {
        private const val TAG = "WeatherNotificationService"
        private const val CHANNEL_ID = "weather_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val INTERVAL_MS = 60_000L // 1 minute

        const val ACTION_START = "com.example.aiagentchat.START_WEATHER_SERVICE"
        const val ACTION_STOP = "com.example.aiagentchat.STOP_WEATHER_SERVICE"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    // Koin injection - lazy initialization
    private val preferencesManager: PreferencesManager by lazy {
        getKoin().get<PreferencesManager>()
    }
    private val mcpRepository: McpRepository by lazy {
        getKoin().get<McpRepository>()
    }
    private val weatherDataStorage: WeatherDataStorage by lazy {
        getKoin().get<WeatherDataStorage>()
    }
    private val weatherSummaryUseCase: WeatherSummaryUseCase by lazy {
        getKoin().get<WeatherSummaryUseCase>()
    }
    private val weatherNotificationManager: WeatherNotificationManager by lazy {
        getKoin().get<WeatherNotificationManager>()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startForegroundService()
                startPeriodicWork()
            }
            ACTION_STOP -> {
                stopPeriodicWork()
                stopForegroundService()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        stopPeriodicWork()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Weather Notification Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service for periodic weather notifications"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Foreground service started")
    }

    private fun createForegroundNotification(): Notification {
        val packageManager = packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        } ?: Intent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Weather Notifications Active")
            .setContentText("Monitoring weather updates")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startPeriodicWork() {
        if (job?.isActive == true) {
            Log.d(TAG, "Periodic work already running")
            return
        }

        job = serviceScope.launch {
            Log.d(TAG, "Periodic work started")
            while (isActive) {
                try {
                    processWeatherNotification()
                    delay(INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic work", e)
                    delay(INTERVAL_MS)
                }
            }
        }
    }

    private fun stopPeriodicWork() {
        job?.cancel()
        job = null
        Log.d(TAG, "Periodic work stopped")
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun processWeatherNotification() {
        Log.d(TAG, "processWeatherNotification called")

        if (!preferencesManager.weatherNotificationsEnabled) {
            Log.d(TAG, "Weather notifications are disabled, stopping service")
            stopForegroundService()
            return
        }

        if (!preferencesManager.testModeEnabled) {
            Log.d(TAG, "Test mode is disabled, stopping service")
            stopForegroundService()
            return
        }

        // Проверяем, что приложение в фоне (не в foreground)
        if (AppStateHelper.isAppInForeground(applicationContext)) {
            Log.d(TAG, "App is in foreground, skipping this iteration")
            return
        }

        val lastUserQuery = preferencesManager.lastUserQuery
        if (lastUserQuery.isNullOrBlank()) {
            Log.d(TAG, "No last user query found, skipping")
            return
        }

        Log.d(TAG, "Processing weather notification for query: $lastUserQuery")

        val weatherResult = mcpRepository.callTool(
            toolName = "get_weather",
            arguments = mapOf("location" to lastUserQuery)
        )

        if (weatherResult.isSuccess) {
            val weatherData = weatherResult.getOrNull() ?: ""
            Log.d(TAG, "Received weather data: $weatherData")

            val summaryResult = weatherSummaryUseCase.generateSummary(
                model = AiModel.DeepSeek,
                weatherData = weatherData,
                location = lastUserQuery
            )

            if (summaryResult.isSuccess) {
                val summary = summaryResult.getOrNull() ?: ""
                Log.d(TAG, "Generated summary: ${summary.take(100)}...")

                val saveResult = weatherDataStorage.saveWeatherSummary(lastUserQuery, summary)
                if (saveResult.isSuccess) {
                    Log.d(TAG, "Summary saved successfully")
                } else {
                    Log.e(TAG, "Failed to save summary", saveResult.exceptionOrNull())
                }

                weatherNotificationManager.showWeatherSummaryNotification(summary)
                Log.d(TAG, "Notification sent with summary")
            } else {
                val error = summaryResult.exceptionOrNull()
                Log.e(TAG, "Failed to generate summary", error)
            }
        } else {
            Log.e(TAG, "Failed to get weather data", weatherResult.exceptionOrNull())
        }
    }
}

