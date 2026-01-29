package com.example.aiagentchat.core.common.utils

import android.app.ActivityManager
import android.content.Context

object AppStateHelper {
    fun isAppInForeground(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appProcesses = activityManager.runningAppProcesses ?: return false
            
            val packageName = context.packageName
            for (appProcess in appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    appProcess.processName == packageName) {
                    android.util.Log.d("AppStateHelper", "App is in foreground")
                    return true
                }
            }
            android.util.Log.d("AppStateHelper", "App is NOT in foreground (background or killed)")
            false
        } catch (e: Exception) {
            android.util.Log.w("AppStateHelper", "Error checking app state, assuming background", e)
            false // При ошибке считаем что в фоне
        }
    }
}

