package com.example.aiagentchat.feature.chat.data.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class WeatherDataEntry(
    val timestamp: Long,
    val location: String,
    val weatherData: String
)

data class WeatherSummaryData(
    val timestamp: Long,
    val location: String,
    val summary: String
)

class WeatherDataStorage(
    private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "WeatherDataStorage"
        private const val SUMMARY_FILE_NAME = "weather_summary.json"
    }

    private val summaryFile: File
        get() = File(context.filesDir, SUMMARY_FILE_NAME)

    fun saveWeatherSummary(location: String, summary: String): Result<Unit> {
        return try {
            val summaryData = WeatherSummaryData(
                timestamp = System.currentTimeMillis(),
                location = location,
                summary = summary
            )

            val json = gson.toJson(summaryData)
            FileWriter(summaryFile).use { it.write(json) }

            Log.d(TAG, "Saved weather summary for location: $location")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving weather summary", e)
            Result.failure(e)
        }
    }

    fun loadWeatherSummary(): Result<WeatherSummaryData?> {
        return try {
            if (!summaryFile.exists()) {
                return Result.success(null)
            }

            FileReader(summaryFile).use { reader ->
                val data = gson.fromJson<WeatherSummaryData>(reader, WeatherSummaryData::class.java)
                Result.success(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading weather summary", e)
            Result.failure(e)
        }
    }

    fun clearWeatherSummary(): Result<Unit> {
        return try {
            if (summaryFile.exists()) {
                summaryFile.delete()
            }
            Log.d(TAG, "Cleared weather summary")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing weather summary", e)
            Result.failure(e)
        }
    }
}

