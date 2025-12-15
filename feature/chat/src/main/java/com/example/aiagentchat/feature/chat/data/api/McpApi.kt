package com.example.aiagentchat.feature.chat.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class JsonRpcRequest(
    @SerializedName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerializedName("id")
    val id: Int,
    @SerializedName("method")
    val method: String,
    @SerializedName("params")
    val params: Map<String, Any>? = null
)

data class JsonRpcResponse(
    @SerializedName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerializedName("id")
    val id: Int,
    @SerializedName("result")
    val result: Map<String, Any>? = null,
    @SerializedName("error")
    val error: JsonRpcError? = null
)

data class JsonRpcError(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String
)

interface McpApi {
    @POST("mcp")
    suspend fun sendRequest(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Header("Content-Type") contentType: String = "application/json",
        @retrofit2.http.Header("Accept") accept: String = "application/json, text/event-stream",
        @retrofit2.http.Header("mcp-session-id") sessionId: String? = null,
        @Body request: JsonRpcRequest
    ): Response<JsonRpcResponse>
}

