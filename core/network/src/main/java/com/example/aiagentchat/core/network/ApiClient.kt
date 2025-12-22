package com.example.aiagentchat.core.network

import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object SystemDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            InetAddress.getAllByName(hostname).toList()
        } catch (e: UnknownHostException) {
            throw e
        }
    }
}

object ApiClient {
    private const val TAG = "ApiClient"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Log.d(TAG, "Resolving DNS for: $hostname")
                val addresses = SystemDns.lookup(hostname)
                Log.d(TAG, "Resolved $hostname to ${addresses.size} address(es): ${addresses.joinToString { it.hostAddress }}")
                addresses
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Failed to resolve hostname: $hostname", e)
                Log.e(TAG, "This may indicate network connectivity issues. Please check:")
                Log.e(TAG, "1. Internet connection on emulator/device")
                Log.e(TAG, "2. DNS settings")
                Log.e(TAG, "3. Network security configuration")
                throw e
            }
        }
    }

    private val internetOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .dns(dns)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // DNS для локальных адресов - прямое разрешение IP без DNS запросов
    private val localDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                when {
                    hostname == "10.0.2.2" -> {
                        Log.d(TAG, "Direct IP resolution for 10.0.2.2 (emulator localhost)")
                        listOf(InetAddress.getByName("10.0.2.2"))
                    }
                    hostname == "localhost" || hostname == "127.0.0.1" -> {
                        Log.d(TAG, "Direct IP resolution for localhost")
                        listOf(InetAddress.getByName("127.0.0.1"))
                    }
                    else -> {
                        Log.d(TAG, "Resolving local hostname: $hostname")
                        SystemDns.lookup(hostname)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve local hostname: $hostname", e)
                throw e
            }
        }
    }
    
    private val localOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .dns(localDns) // Используем специальный DNS для локальных адресов
        .connectTimeout(60, TimeUnit.SECONDS) // Увеличено для Ollama (может быть медленным)
        .readTimeout(120, TimeUnit.SECONDS) // Увеличено для генерации embeddings
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun createRetrofit(baseUrl: String): Retrofit {
        Log.d(TAG, "Creating Retrofit instance for baseUrl: $baseUrl")
        val isLocal = baseUrl.contains("10.0.2.2") || 
                     baseUrl.contains("localhost") || 
                     baseUrl.contains("127.0.0.1")
        
        val client = if (isLocal) {
            Log.d(TAG, "Using local OkHttp client (with local DNS resolver) for: $baseUrl")
            Log.d(TAG, "Local addresses (10.0.2.2, localhost, 127.0.0.1) will be resolved directly")
            localOkHttpClient
        } else {
            Log.d(TAG, "Using internet OkHttp client (with DNS resolver) for: $baseUrl")
            internetOkHttpClient
        }
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

