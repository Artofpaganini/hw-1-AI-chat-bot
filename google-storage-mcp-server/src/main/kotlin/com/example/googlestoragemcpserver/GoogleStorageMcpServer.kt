package com.example.googlestoragemcpserver

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.logging.Logger
import java.util.logging.Level

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int?,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int?,
    val result: JsonObject? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

@Serializable
data class GoogleDriveFile(
    val id: String? = null,
    val name: String,
    val mimeType: String = "application/json"
)

@Serializable
data class GoogleDriveFileList(
    val files: List<GoogleDriveFile>? = null
)

private fun findFileByName(
    fileName: String,
    accessToken: String,
    httpClient: OkHttpClient,
    json: Json,
    logger: Logger
): String? {
    try {
        val query = "name='$fileName' and trashed=false"
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name)"
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        
        if (response.isSuccessful && body.isNotBlank()) {
            val fileList = json.decodeFromString<GoogleDriveFileList>(body)
            val file = fileList.files?.firstOrNull()
            return file?.id
        } else {
            logger.log(Level.WARNING, "Failed to search file: ${response.code} - $body")
            return null
        }
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error searching file", e)
        return null
    }
}

private fun createFile(
    fileName: String,
    content: String,
    accessToken: String,
    httpClient: OkHttpClient,
    json: Json,
    logger: Logger
): Result<String> {
    return try {
        // First, create file metadata
        val metadata = buildJsonObject {
            put("name", fileName)
            put("mimeType", "application/json")
        }
        
        val createUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
        val contentType = "multipart/related; boundary=$boundary"
        
        val multipartBody = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json.encodeToString(JsonObject.serializer(), metadata))
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(content)
            append("\r\n--$boundary--\r\n")
        }
        
        val request = Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", contentType)
            .post(multipartBody.toRequestBody(contentType.toMediaType()))
            .build()
        
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        
        if (response.isSuccessful && body.isNotBlank()) {
            val file = json.decodeFromString<GoogleDriveFile>(body)
            logger.log(Level.INFO, "File created: ${file.id}")
            Result.success(file.id ?: "")
        } else {
            logger.log(Level.WARNING, "Failed to create file: ${response.code} - $body")
            Result.failure(Exception("Failed to create file: ${response.code} - $body"))
        }
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error creating file", e)
        Result.failure(e)
    }
}

private fun updateFile(
    fileId: String,
    content: String,
    accessToken: String,
    httpClient: OkHttpClient,
    logger: Logger
): Result<Unit> {
    return try {
        val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        
        val request = Request.Builder()
            .url(updateUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .put(content.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (response.isSuccessful) {
            logger.log(Level.INFO, "File updated: $fileId")
            Result.success(Unit)
        } else {
            val errorBody = response.body?.string() ?: ""
            logger.log(Level.WARNING, "Failed to update file: ${response.code} - $errorBody")
            Result.failure(Exception("Failed to update file: ${response.code} - $errorBody"))
        }
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error updating file", e)
        Result.failure(e)
    }
}

private fun deleteFile(
    fileId: String,
    accessToken: String,
    httpClient: OkHttpClient,
    logger: Logger
): Result<Unit> {
    return try {
        val deleteUrl = "https://www.googleapis.com/drive/v3/files/$fileId"
        
        val request = Request.Builder()
            .url(deleteUrl)
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (response.isSuccessful) {
            logger.log(Level.INFO, "File deleted: $fileId")
            Result.success(Unit)
        } else {
            val errorBody = response.body?.string() ?: ""
            logger.log(Level.WARNING, "Failed to delete file: ${response.code} - $errorBody")
            Result.failure(Exception("Failed to delete file: ${response.code} - $errorBody"))
        }
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error deleting file", e)
        Result.failure(e)
    }
}

fun main(args: Array<String>) {
    val logger = Logger.getLogger("GoogleStorageMcpServer")
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8081
    logger.log(Level.INFO, "Starting Google Storage MCP Server on port $port...")
    
    val json = Json { ignoreUnknownKeys = true }
    val httpClient = OkHttpClient()
    
    val server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            anyHost()
        }
        
        install(ContentNegotiation) {
            json(json)
        }
        
        routing {
            route("mcp") {
                post {
                    try {
                        val rawBody = call.receiveText()
                        logger.log(Level.INFO, "Received raw request body: $rawBody")
                        
                        val request = json.decodeFromString<JsonRpcRequest>(rawBody)
                        logger.log(Level.INFO, "Parsed request: method=${request.method}, id=${request.id}, params=${request.params}")
                        
                        val response = when (request.method) {
                            "initialize" -> {
                                JsonRpcResponse(
                                    id = request.id,
                                    result = buildJsonObject {
                                        put("protocolVersion", "2024-11-05")
                                        putJsonObject("capabilities") {
                                            putJsonObject("tools") {
                                                put("listChanged", JsonNull)
                                            }
                                        }
                                        putJsonObject("serverInfo") {
                                            put("name", "google-storage-mcp-server")
                                            put("version", "1.0.0")
                                        }
                                    }
                                )
                            }
                            "tools/list" -> {
                                JsonRpcResponse(
                                    id = request.id,
                                    result = buildJsonObject {
                                        putJsonArray("tools") {
                                            // save_to_drive - создает или обновляет файл
                                            addJsonObject {
                                                put("name", "save_to_drive")
                                                put("description", "Save JSON data to Google Drive. If file exists, it will be overwritten. Otherwise, a new file will be created.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("accessToken") {
                                                            put("type", "string")
                                                            put("description", "Google Drive API access token (OAuth 2.0)")
                                                        }
                                                        putJsonObject("fileName") {
                                                            put("type", "string")
                                                            put("description", "Name of the file to save (default: 'ai-chat-results')")
                                                        }
                                                        putJsonObject("data") {
                                                            put("type", "string")
                                                            put("description", "JSON data to save to the file")
                                                        }
                                                    }
                                                    putJsonArray("required") {
                                                        add("accessToken")
                                                        add("data")
                                                    }
                                                }
                                            }
                                            // update_file_in_drive - обновляет содержимое существующего файла
                                            addJsonObject {
                                                put("name", "update_file_in_drive")
                                                put("description", "Update content of an existing file in Google Drive. File must exist.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("accessToken") {
                                                            put("type", "string")
                                                            put("description", "Google Drive API access token (OAuth 2.0)")
                                                        }
                                                        putJsonObject("fileName") {
                                                            put("type", "string")
                                                            put("description", "Name of the file to update (default: 'ai-chat-results')")
                                                        }
                                                        putJsonObject("data") {
                                                            put("type", "string")
                                                            put("description", "New JSON data to replace file content")
                                                        }
                                                    }
                                                    putJsonArray("required") {
                                                        add("accessToken")
                                                        add("data")
                                                    }
                                                }
                                            }
                                            // delete_file_from_drive - удаляет файл из Google Drive
                                            addJsonObject {
                                                put("name", "delete_file_from_drive")
                                                put("description", "Delete a file from Google Drive by name.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("accessToken") {
                                                            put("type", "string")
                                                            put("description", "Google Drive API access token (OAuth 2.0)")
                                                        }
                                                        putJsonObject("fileName") {
                                                            put("type", "string")
                                                            put("description", "Name of the file to delete (default: 'ai-chat-results')")
                                                        }
                                                    }
                                                    putJsonArray("required") {
                                                        add("accessToken")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            "tools/call" -> {
                                logger.log(Level.INFO, "Processing tools/call request. Params: ${request.params}")
                                
                                val toolName = request.params?.get("name")?.jsonPrimitive?.content
                                val argumentsElement = request.params?.get("arguments")
                                
                                logger.log(Level.INFO, "Tool name: $toolName, Arguments element: $argumentsElement")
                                
                                val arguments = when {
                                    argumentsElement is JsonObject -> argumentsElement
                                    argumentsElement is JsonElement -> {
                                        logger.log(Level.INFO, "Arguments is JsonElement, converting to JsonObject")
                                        argumentsElement.jsonObject
                                    }
                                    else -> {
                                        logger.log(Level.WARNING, "Arguments is not JsonObject or JsonElement, type: ${argumentsElement?.javaClass?.simpleName}")
                                        null
                                    }
                                }
                                
                                logger.log(Level.INFO, "Parsed arguments: $arguments")
                                
                                if (toolName == "save_to_drive") {
                                    val accessToken = arguments?.get("accessToken")?.jsonPrimitive?.content
                                    val fileName = arguments?.get("fileName")?.jsonPrimitive?.content ?: "ai-chat-results"
                                    val data = arguments?.get("data")?.jsonPrimitive?.content
                                    
                                    logger.log(Level.INFO, "Save to drive: fileName=$fileName, data length=${data?.length ?: 0}")
                                    
                                    if (accessToken.isNullOrBlank()) {
                                        logger.log(Level.WARNING, "Access token is missing or blank")
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32602,
                                                message = "accessToken parameter is required"
                                            )
                                        )
                                    } else if (data.isNullOrBlank()) {
                                        logger.log(Level.WARNING, "Data is missing or blank")
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32602,
                                                message = "data parameter is required"
                                            )
                                        )
                                    } else {
                                        try {
                                            // Check if file exists
                                            val existingFileId = findFileByName(fileName, accessToken, httpClient, json, logger)
                                            
                                            val result = if (existingFileId != null) {
                                                // Update existing file
                                                logger.log(Level.INFO, "File exists, updating: $existingFileId")
                                                updateFile(existingFileId, data, accessToken, httpClient, logger)
                                            } else {
                                                // Create new file
                                                logger.log(Level.INFO, "File does not exist, creating new file")
                                                createFile(fileName, data, accessToken, httpClient, json, logger).map { }
                                            }
                                            
                                            if (result.isSuccess) {
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    result = buildJsonObject {
                                                        putJsonArray("content") {
                                                            addJsonObject {
                                                                put("type", "text")
                                                                put("text", if (existingFileId != null) {
                                                                    "File '$fileName' updated successfully in Google Drive"
                                                                } else {
                                                                    "File '$fileName' created successfully in Google Drive"
                                                                })
                                                            }
                                                        }
                                                        put("isError", false)
                                                    }
                                                )
                                            } else {
                                                val error = result.exceptionOrNull()
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    result = buildJsonObject {
                                                        putJsonArray("content") {
                                                            addJsonObject {
                                                                put("type", "text")
                                                                put("text", "Error: ${error?.message ?: "Unknown error occurred"}")
                                                            }
                                                        }
                                                        put("isError", true)
                                                    }
                                                )
                                            }
                                        } catch (e: Exception) {
                                            logger.log(Level.SEVERE, "Error in save_to_drive", e)
                                            JsonRpcResponse(
                                                id = request.id,
                                                result = buildJsonObject {
                                                    putJsonArray("content") {
                                                        addJsonObject {
                                                            put("type", "text")
                                                            put("text", "Error: ${e.message ?: "Unknown error occurred"}")
                                                        }
                                                    }
                                                    put("isError", true)
                                                }
                                            )
                                        }
                                    }
                                } else if (toolName == "update_file_in_drive") {
                                    val accessToken = arguments?.get("accessToken")?.jsonPrimitive?.content
                                    val fileName = arguments?.get("fileName")?.jsonPrimitive?.content ?: "ai-chat-results"
                                    val data = arguments?.get("data")?.jsonPrimitive?.content
                                    
                                    logger.log(Level.INFO, "Update file in drive: fileName=$fileName, data length=${data?.length ?: 0}")
                                    
                                    if (accessToken.isNullOrBlank()) {
                                        logger.log(Level.WARNING, "Access token is missing or blank")
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32602,
                                                message = "accessToken parameter is required"
                                            )
                                        )
                                    } else if (data.isNullOrBlank()) {
                                        logger.log(Level.WARNING, "Data is missing or blank")
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32602,
                                                message = "data parameter is required"
                                            )
                                        )
                                    } else {
                                        try {
                                            val existingFileId = findFileByName(fileName, accessToken, httpClient, json, logger)
                                            
                                            if (existingFileId == null) {
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    result = buildJsonObject {
                                                        putJsonArray("content") {
                                                            addJsonObject {
                                                                put("type", "text")
                                                                put("text", "Error: File '$fileName' not found in Google Drive")
                                                            }
                                                        }
                                                        put("isError", true)
                                                    }
                                                )
                                            } else {
                                                val result = updateFile(existingFileId, data, accessToken, httpClient, logger)
                                                
                                                if (result.isSuccess) {
                                                    JsonRpcResponse(
                                                        id = request.id,
                                                        result = buildJsonObject {
                                                            putJsonArray("content") {
                                                                addJsonObject {
                                                                    put("type", "text")
                                                                    put("text", "File '$fileName' updated successfully in Google Drive")
                                                                }
                                                            }
                                                            put("isError", false)
                                                        }
                                                    )
                                                } else {
                                                    val error = result.exceptionOrNull()
                                                    JsonRpcResponse(
                                                        id = request.id,
                                                        result = buildJsonObject {
                                                            putJsonArray("content") {
                                                                addJsonObject {
                                                                    put("type", "text")
                                                                    put("text", "Error: ${error?.message ?: "Unknown error occurred"}")
                                                                }
                                                            }
                                                            put("isError", true)
                                                        }
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            logger.log(Level.SEVERE, "Error in update_file_in_drive", e)
                                            JsonRpcResponse(
                                                id = request.id,
                                                result = buildJsonObject {
                                                    putJsonArray("content") {
                                                        addJsonObject {
                                                            put("type", "text")
                                                            put("text", "Error: ${e.message ?: "Unknown error occurred"}")
                                                        }
                                                    }
                                                    put("isError", true)
                                                }
                                            )
                                        }
                                    }
                                } else if (toolName == "delete_file_from_drive") {
                                    val accessToken = arguments?.get("accessToken")?.jsonPrimitive?.content
                                    val fileName = arguments?.get("fileName")?.jsonPrimitive?.content ?: "ai-chat-results"
                                    
                                    logger.log(Level.INFO, "Delete file from drive: fileName=$fileName")
                                    
                                    if (accessToken.isNullOrBlank()) {
                                        logger.log(Level.WARNING, "Access token is missing or blank")
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32602,
                                                message = "accessToken parameter is required"
                                            )
                                        )
                                    } else {
                                        try {
                                            val existingFileId = findFileByName(fileName, accessToken, httpClient, json, logger)
                                            
                                            if (existingFileId == null) {
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    result = buildJsonObject {
                                                        putJsonArray("content") {
                                                            addJsonObject {
                                                                put("type", "text")
                                                                put("text", "File '$fileName' not found in Google Drive (may already be deleted)")
                                                            }
                                                        }
                                                        put("isError", false)
                                                    }
                                                )
                                            } else {
                                                val result = deleteFile(existingFileId, accessToken, httpClient, logger)
                                                
                                                if (result.isSuccess) {
                                                    JsonRpcResponse(
                                                        id = request.id,
                                                        result = buildJsonObject {
                                                            putJsonArray("content") {
                                                                addJsonObject {
                                                                    put("type", "text")
                                                                    put("text", "File '$fileName' deleted successfully from Google Drive")
                                                                }
                                                            }
                                                            put("isError", false)
                                                        }
                                                    )
                                                } else {
                                                    val error = result.exceptionOrNull()
                                                    JsonRpcResponse(
                                                        id = request.id,
                                                        result = buildJsonObject {
                                                            putJsonArray("content") {
                                                                addJsonObject {
                                                                    put("type", "text")
                                                                    put("text", "Error: ${error?.message ?: "Unknown error occurred"}")
                                                                }
                                                            }
                                                            put("isError", true)
                                                        }
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            logger.log(Level.SEVERE, "Error in delete_file_from_drive", e)
                                            JsonRpcResponse(
                                                id = request.id,
                                                result = buildJsonObject {
                                                    putJsonArray("content") {
                                                        addJsonObject {
                                                            put("type", "text")
                                                            put("text", "Error: ${e.message ?: "Unknown error occurred"}")
                                                        }
                                                    }
                                                    put("isError", true)
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    logger.log(Level.WARNING, "Unknown tool requested: $toolName")
                                    JsonRpcResponse(
                                        id = request.id,
                                        error = JsonRpcError(
                                            code = -32601,
                                            message = "Tool not found: $toolName. Available tools: save_to_drive, update_file_in_drive, delete_file_from_drive"
                                        )
                                    )
                                }
                            }
                            else -> {
                                JsonRpcResponse(
                                    id = request.id,
                                    error = JsonRpcError(
                                        code = -32601,
                                        message = "Method not found: ${request.method}"
                                    )
                                )
                            }
                        }
                        
                        call.respond(response)
                    } catch (e: Exception) {
                        logger.log(Level.SEVERE, "Error processing request", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            JsonRpcResponse(
                                id = null,
                                error = JsonRpcError(
                                    code = -32603,
                                    message = "Internal error: ${e.message}"
                                )
                            )
                        )
                    }
                }
            }
        }
    }
    
    try {
        logger.log(Level.INFO, "Google Storage MCP Server starting on port $port...")
        val serverInstance = server.start(wait = false)
        logger.log(Level.INFO, "Google Storage MCP Server started successfully on port $port")
        logger.log(Level.INFO, "MCP endpoint available at: http://0.0.0.0:$port/mcp")
        logger.log(Level.INFO, "For Android emulator use: http://10.0.2.2:$port/mcp")
        
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.log(Level.INFO, "Shutting down Google Storage MCP Server...")
            serverInstance.stop(1000, 2000)
        })
        
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Failed to start server", e)
        e.printStackTrace()
        System.exit(1)
    }
}

