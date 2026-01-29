package com.example.aiagentchat.feature.chat.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WeatherWorkManager(private val context: Context) {
    companion object {
        private const val WORK_NAME = "weather_notification_work"
        private const val REPEAT_INTERVAL = 15L // 15 minutes
    }
    
    fun getContext(): Context = context

    fun scheduleWeatherNotifications(enabled: Boolean) {
        android.util.Log.d("EWQ", "scheduleWeatherNotifications called: enabled=$enabled")
        
        val workManager = WorkManager.getInstance(context)

        if (enabled) {
            // Constraints для надежной работы даже после перезагрузки устройства
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // Работать даже при низком заряде
                .setRequiresCharging(false) // Работать без зарядки
                .setRequiresDeviceIdle(false) // Работать даже когда устройство активно
                .setRequiresStorageNotLow(false) // Работать даже при нехватке места
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(
                REPEAT_INTERVAL,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("weather_notifications")
                .setInitialDelay(REPEAT_INTERVAL, TimeUnit.MINUTES) // Первый запуск через 15 минут
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            android.util.Log.d("EWQ", "Periodic work scheduled: interval=$REPEAT_INTERVAL minutes")
            
            // Проверяем статус задачи
            workManager.getWorkInfosForUniqueWork(WORK_NAME).get().forEach { workInfo ->
                android.util.Log.d("EWQ", "Work status: ${workInfo.state}, id=${workInfo.id}")
            }
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
            android.util.Log.d("EWQ", "Periodic work cancelled")
        }
    }
}

