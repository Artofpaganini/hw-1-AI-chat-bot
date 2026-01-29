package com.example.aiagentchat.feature.chat.data.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

class GitFileDetector(
    private val projectRoot: File? = null,
    private val projectRootPath: String? = null
) {
    companion object {
        private const val TAG = "GitFileDetector"
        private val SUPPORTED_EXTENSIONS = setOf(".kt", ".xml", ".java", ".kts", ".sh")
        private const val GIT_MCP_URL = "http://10.0.2.2:8084/mcp"
    }
    
    suspend fun getChangedFiles(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // Try local git first
            val localResult = tryGetChangedFilesLocal()
            if (localResult.isSuccess) {
                return@withContext localResult
            }
            
            // If local git fails, try via Git MCP Server
            val remoteResult = tryGetChangedFilesViaGitMcp()
            if (remoteResult.isSuccess) {
                return@withContext remoteResult
            }
            
            // If both fail, return helpful error message
            val localError = localResult.exceptionOrNull()?.message ?: "Unknown error"
            val remoteError = remoteResult.exceptionOrNull()?.message ?: "Unknown error"
            val isNotGitRepo = localError.contains("Not a git repository", ignoreCase = true)
            
            // Build detailed error message
            val errorMessage = buildString {
                appendLine("Git repository not accessible on Android device.")
                appendLine()
                appendLine("The /review command requires access to git repository, which is not available on Android devices.")
                appendLine()
                
                // Add Git MCP Server specific error if available
                if (remoteError.contains("Git MCP", ignoreCase = true) || 
                    remoteError.contains("not available", ignoreCase = true) ||
                    remoteError.contains("ConnectException", ignoreCase = true)) {
                    appendLine("Git MCP Server Error: $remoteError")
                    appendLine()
                } else if (!remoteError.contains("Unknown error")) {
                    appendLine("Git MCP Server returned error: $remoteError")
                    appendLine()
                }
                
                appendLine("Solutions:")
                appendLine("1. Start Git MCP Server on host:")
                appendLine("   cd git-mcp-server && ./start-server.sh")
                appendLine()
                appendLine("2. Configure PROJECT_ROOT in local.properties (optional):")
                appendLine("   PROJECT_ROOT=/path/to/your/project")
                appendLine()
                appendLine("3. Verify Git MCP Server is running:")
                appendLine("   curl -X POST http://localhost:8084/mcp \\")
                appendLine("     -H \"Content-Type: application/json\" \\")
                appendLine("     -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}'")
                appendLine()
                appendLine("Git MCP Server should be running on port 8084 (default).")
                appendLine("For Android emulator, use: http://10.0.2.2:8084/mcp")
            }
            
            Result.failure(Exception(errorMessage.trim()))
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting changed files", e)
            Result.failure(e)
        }
    }
    
    private fun tryGetChangedFilesLocal(): Result<List<String>> {
        return try {
            val root = projectRoot ?: File(System.getProperty("user.dir") ?: "/")
            val gitDir = File(root, ".git")
            if (!gitDir.exists() || !gitDir.isDirectory) {
                return Result.failure(Exception("Not a git repository. Project root: ${root.absolutePath}"))
            }
            
            val process = ProcessBuilder()
                .command("git", "diff", "--name-only", "HEAD")
                .directory(root)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                Log.e(TAG, "Git command failed: $error")
                return Result.failure(Exception("Git command failed: $error"))
            }
            
            val changedFiles = output.lines()
                .filter { it.isNotBlank() }
                .filter { filePath ->
                    val extension = File(filePath).extension
                    SUPPORTED_EXTENSIONS.contains(".$extension")
                }
                .map { filePath ->
                    val fileRoot = projectRoot ?: File(System.getProperty("user.dir") ?: "/")
                    val fullPath = if (File(filePath).isAbsolute) {
                        filePath
                    } else {
                        File(fileRoot, filePath).absolutePath
                    }
                    fullPath
                }
                .filter { File(it).exists() }
            
            Log.d(TAG, "Found ${changedFiles.size} changed files: ${changedFiles.joinToString(", ")}")
            Result.success(changedFiles)
        } catch (e: Exception) {
            Log.e(TAG, "Error in local git detection", e)
            Result.failure(e)
        }
    }
    
    private fun tryGetChangedFilesViaGitMcp(): Result<List<String>> {
        return try {
            val projectPath = projectRootPath ?: System.getProperty("user.dir") ?: "/"
            Log.d(TAG, "Attempting to get changed files via Git MCP Server. Project path: $projectPath, URL: $GIT_MCP_URL")
            
            val requestBody = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "tools/call",
                    "params": {
                        "name": "get_changed_files",
                        "arguments": {
                            "projectRoot": "$projectPath"
                        }
                    }
                }
            """.trimIndent()
            
            val url = URL(GIT_MCP_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 30000
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                } catch (e: Exception) {
                    "Failed to read error body: ${e.message}"
                }
                Log.e(TAG, "Git MCP Server returned HTTP $responseCode: $errorBody")
                return Result.failure(Exception("Git MCP Server returned HTTP error $responseCode: $errorBody"))
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            Log.d(TAG, "Git MCP response: $response")
            
            // Parse JSON response using Gson
            try {
                val jsonElement = JsonParser.parseString(response).asJsonObject
                if (jsonElement.has("error")) {
                    val error = jsonElement.getAsJsonObject("error")
                    val errorMessage = error.get("message")?.asString 
                        ?: error.get("code")?.asString?.let { "Error code: $it" }
                        ?: "Unknown error"
                    return Result.failure(Exception("Git MCP error: $errorMessage"))
                }
                
                if (!jsonElement.has("result")) {
                    return Result.failure(Exception("Git MCP response missing 'result' field"))
                }
                
                val result = jsonElement.getAsJsonObject("result")
                if (!result.has("files")) {
                    return Result.failure(Exception("Git MCP response missing 'files' field"))
                }
                
                val filesArray = result.getAsJsonArray("files")
                val files = mutableListOf<String>()
                filesArray.forEach { element ->
                    if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                        files.add(element.asString)
                    }
                }
                
                Log.d(TAG, "Found ${files.size} changed files via Git MCP")
                Result.success(files)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Git MCP JSON response", e)
                Result.failure(Exception("Failed to parse Git MCP response: ${e.message}"))
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Git MCP Server not available - connection refused", e)
            val errorMsg = buildString {
                appendLine("Git MCP Server not available (connection refused).")
                appendLine()
                appendLine("Please start Git MCP Server on host:")
                appendLine("  cd git-mcp-server && ./start-server.sh")
                appendLine()
                appendLine("Server URL: $GIT_MCP_URL")
                appendLine("Make sure the server is running and accessible from the Android device/emulator.")
            }
            Result.failure(Exception(errorMsg.trim()))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Git MCP Server connection timeout", e)
            val errorMsg = buildString {
                appendLine("Git MCP Server connection timeout.")
                appendLine()
                appendLine("The server may be slow to respond or not running.")
                appendLine("Please check:")
                appendLine("  1. Git MCP Server is running: cd git-mcp-server && ./start-server.sh")
                appendLine("  2. Server URL is correct: $GIT_MCP_URL")
                appendLine("  3. Network connectivity between device and host")
            }
            Result.failure(Exception(errorMsg.trim()))
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Git MCP Server host unknown", e)
            val errorMsg = buildString {
                appendLine("Git MCP Server host unknown: ${e.message}")
                appendLine()
                appendLine("Please check the server URL: $GIT_MCP_URL")
                appendLine("For Android emulator, use: http://10.0.2.2:8084/mcp")
            }
            Result.failure(Exception(errorMsg.trim()))
        } catch (e: Exception) {
            Log.e(TAG, "Error executing git via Git MCP: ${e.javaClass.simpleName} - ${e.message}", e)
            val errorMsg = buildString {
                appendLine("Error executing git via Git MCP Server: ${e.javaClass.simpleName}")
                if (e.message != null) {
                    appendLine("Details: ${e.message}")
                }
                appendLine()
                appendLine("Please check:")
                appendLine("  1. Git MCP Server is running")
                appendLine("  2. Server URL: $GIT_MCP_URL")
                appendLine("  3. Check logs for more details")
            }
            Result.failure(Exception(errorMsg.trim()))
        }
    }
    
    suspend fun readFileContent(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Try local file first
            val localResult = tryReadFileContentLocal(filePath)
            if (localResult.isSuccess) {
                return@withContext localResult
            }
            
            // If local fails, try via Git MCP Server
            val remoteResult = tryReadFileContentViaGitMcp(filePath)
            if (remoteResult.isSuccess) {
                return@withContext remoteResult
            }
            
            // Return local error if both fail
            localResult
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: $filePath", e)
            Result.failure(e)
        }
    }
    
    private fun tryReadFileContentLocal(filePath: String): Result<String> {
        return try {
            val root = projectRoot ?: File(System.getProperty("user.dir") ?: "/")
            val file = if (File(filePath).isAbsolute) {
                File(filePath)
            } else {
                File(root, filePath)
            }
            
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $filePath"))
            }
            
            if (!file.canRead()) {
                return Result.failure(Exception("Cannot read file: $filePath"))
            }
            
            val content = file.readText(Charsets.UTF_8)
            Result.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file locally: $filePath", e)
            Result.failure(e)
        }
    }
    
    private fun tryReadFileContentViaGitMcp(filePath: String): Result<String> {
        return try {
            val projectPath = projectRootPath ?: System.getProperty("user.dir") ?: "/"
            Log.d(TAG, "Attempting to read file via Git MCP Server. File: $filePath, Project path: $projectPath")
            
            val requestBody = """
                {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "method": "tools/call",
                    "params": {
                        "name": "read_file_content",
                        "arguments": {
                            "filePath": "$filePath",
                            "projectRoot": "$projectPath"
                        }
                    }
                }
            """.trimIndent()
            
            val url = URL(GIT_MCP_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 30000
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                } catch (e: Exception) {
                    "Failed to read error body: ${e.message}"
                }
                Log.e(TAG, "Git MCP Server returned HTTP $responseCode: $errorBody")
                return Result.failure(Exception("Git MCP Server returned HTTP error $responseCode: $errorBody"))
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            
            // Parse JSON response using Gson
            try {
                val jsonElement = JsonParser.parseString(response).asJsonObject
                if (jsonElement.has("error")) {
                    val error = jsonElement.getAsJsonObject("error")
                    val errorMessage = error.get("message")?.asString 
                        ?: error.get("code")?.asString?.let { "Error code: $it" }
                        ?: "Unknown error"
                    return Result.failure(Exception("Git MCP error: $errorMessage"))
                }
                
                if (!jsonElement.has("result")) {
                    return Result.failure(Exception("Git MCP response missing 'result' field"))
                }
                
                val result = jsonElement.getAsJsonObject("result")
                val content = result.get("content")?.asString ?: ""
                
                Result.success(content)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Git MCP JSON response for file reading", e)
                Result.failure(Exception("Failed to parse Git MCP response: ${e.message}"))
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Git MCP Server not available for file reading", e)
            Result.failure(Exception("Git MCP Server not available. Please start it: cd git-mcp-server && ./start-server.sh"))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Git MCP Server connection timeout for file reading", e)
            Result.failure(Exception("Git MCP Server connection timeout"))
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Git MCP Server host unknown for file reading", e)
            Result.failure(Exception("Git MCP Server host unknown: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file via Git MCP: ${e.javaClass.simpleName} - ${e.message}", e)
            Result.failure(e)
        }
    }
}
