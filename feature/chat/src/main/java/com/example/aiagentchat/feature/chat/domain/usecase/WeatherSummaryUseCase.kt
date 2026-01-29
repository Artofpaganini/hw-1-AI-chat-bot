package com.example.aiagentchat.feature.chat.domain.usecase

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.storage.WeatherDataEntry
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository

class WeatherSummaryUseCase(
    private val aiModelRepository: AiModelRepository
) {
    companion object {
        private const val TAG = "WeatherSummaryUseCase"
    }

    suspend fun generateSummary(
        model: AiModel,
        weatherData: String,
        location: String
    ): Result<String> {
        return try {
            if (weatherData.isBlank()) {
                return Result.failure(Exception("No weather data available"))
            }

            val prompt = """
                Проанализируй следующие данные о погоде и создай краткое summary (максимум 2-3 предложения):
                
                Location: $location
                Weather: $weatherData
                
                Создай краткое summary с основной информацией о погоде.
            """.trimIndent()

            val messages = listOf(
                ChatMessageDto(role = "user", content = prompt)
            )

            val response = aiModelRepository.sendMessage(model, messages)
            response.fold(
                onSuccess = { aiResponse ->
                    Log.d(TAG, "Generated summary: ${aiResponse.content}")
                    Result.success(aiResponse.content)
                },
                onFailure = { error ->
                    Log.e(TAG, "Error generating summary", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateSummary", e)
            Result.failure(e)
        }
    }
}

