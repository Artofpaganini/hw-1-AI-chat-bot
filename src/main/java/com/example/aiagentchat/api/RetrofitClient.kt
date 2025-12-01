package com.example.aiagentchat.api

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val QWEN_BASE_URL = "https://api.together.xyz/v1/"
    private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/"
    private const val GROQ_BASE_URL = "https://api.groq.com/openai/"
    private const val OPENAI_BASE_URL = "https://api.openai.com/"
    private const val HUGGINGFACE_BASE_URL = "https://router.huggingface.co/"
    private const val OLLAMA_BASE_URL = "http://10.0.2.2:11434/" // Для эмулятора Android

    private val loggingInterceptor =
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    private val okHttpClient =
            OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
    }

    val qwenService: QwenApiService =
            createRetrofit(QWEN_BASE_URL).create(QwenApiService::class.java)
    val deepSeekService: DeepSeekApiService =
            createRetrofit(DEEPSEEK_BASE_URL).create(DeepSeekApiService::class.java)
    val groqService: GroqApiService =
            createRetrofit(GROQ_BASE_URL).create(GroqApiService::class.java)
    val openAiService: ApiService = createRetrofit(OPENAI_BASE_URL).create(ApiService::class.java)
    val huggingFaceService: HuggingFaceApiService =
            createRetrofit(HUGGINGFACE_BASE_URL).create(HuggingFaceApiService::class.java)
    val ollamaService: OllamaApiService =
            createRetrofit(OLLAMA_BASE_URL).create(OllamaApiService::class.java)

    // Метод для установки кастомного URL для Ollama (если используется реальное устройство)
    fun createOllamaService(baseUrl: String): OllamaApiService {
        return createRetrofit(baseUrl).create(OllamaApiService::class.java)
    }
}
