package com.example.aiagentchat.feature.chat.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaMcpApi {
    @POST("mcp")
    suspend fun sendRequest(@Body request: JsonRpcRequest): Response<JsonRpcResponse>
}
