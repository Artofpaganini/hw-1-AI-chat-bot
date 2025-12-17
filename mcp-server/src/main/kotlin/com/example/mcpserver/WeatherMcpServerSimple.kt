package com.example.mcpserver

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
import okhttp3.OkHttpClient
import okhttp3.Request
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
data class PointsResponse(
    val properties: PointsProperties
)

@Serializable
data class PointsProperties(
    val gridId: String,
    val gridX: Int,
    val gridY: Int,
    val forecast: String,
    val forecastHourly: String? = null,
    val forecastGridData: String? = null,
    val observationStations: String? = null
)

@Serializable
data class ForecastResponse(
    val properties: ForecastProperties
)

@Serializable
data class ForecastProperties(
    val periods: List<ForecastPeriod>
)

@Serializable
data class ForecastPeriod(
    val number: Int,
    val name: String,
    val startTime: String,
    val endTime: String,
    val isDaytime: Boolean,
    val temperature: Int,
    val temperatureUnit: String,
    val temperatureTrend: String? = null,
    val windSpeed: String,
    val windDirection: String,
    val icon: String,
    val shortForecast: String,
    val detailedForecast: String
)

@Serializable
data class GeocodeResponse(
    val lat: String,
    val lon: String,
    val display_name: String
)

private fun parseLocation(
    location: String,
    httpClient: OkHttpClient,
    json: Json,
    logger: Logger
): Pair<String, String> {
    val coordinatesPattern = Regex("^(-?\\d+\\.?\\d*),\\s*(-?\\d+\\.?\\d*)$")
    val match = coordinatesPattern.find(location.trim())
    
    if (match != null) {
        val lat = match.groupValues[1]
        val lon = match.groupValues[2]
        logger.log(Level.INFO, "Using provided coordinates: $lat, $lon")
        return Pair(lat, lon)
    }
    
    logger.log(Level.INFO, "Geocoding location: $location")
    val encodedLocation = java.net.URLEncoder.encode(location, "UTF-8")
    val geocodeUrl = "https://nominatim.openstreetmap.org/search?q=$encodedLocation&format=json&limit=1"
    
    val geocodeRequest = Request.Builder()
        .url(geocodeUrl)
        .header("User-Agent", "MCP-Weather-Server/1.0")
        .build()
    
    val geocodeResponse = httpClient.newCall(geocodeRequest).execute()
    val geocodeBody = geocodeResponse.body?.string() ?: ""
    
    if (geocodeResponse.isSuccessful && geocodeBody.isNotBlank()) {
        val geocodeArray = json.parseToJsonElement(geocodeBody).jsonArray
        if (geocodeArray.isNotEmpty()) {
            val firstResult = geocodeArray[0].jsonObject
            val lat = firstResult["lat"]?.jsonPrimitive?.content ?: throw Exception("No latitude in geocode response")
            val lon = firstResult["lon"]?.jsonPrimitive?.content ?: throw Exception("No longitude in geocode response")
            logger.log(Level.INFO, "Geocoded to coordinates: $lat, $lon")
            return Pair(lat, lon)
        }
    }
    
    throw Exception("Unable to geocode location: $location")
}

fun main(args: Array<String>) {
    val logger = Logger.getLogger("WeatherMcpServer")
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8080
    logger.log(Level.INFO, "Starting MCP Weather Server on port $port...")
    
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
                                            put("name", "my mcp server")
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
                                            addJsonObject {
                                                put("name", "get_weather")
                                                put("description", "Get current weather information for a location. When this tool is enabled, all user questions are treated as search queries for weather information.")
                                                putJsonObject("inputSchema") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                putJsonObject("location") {
                                                    put("type", "string")
                                                    put("description", "City name, address, or coordinates in format 'lat,lon' (e.g., '40.7128,-74.0060' or 'New York')")
                                                }
                                                    }
                                                    putJsonArray("required") {
                                                        add("location")
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
                                
                                if (toolName == "get_weather") {
                                    val location = arguments?.get("location")?.jsonPrimitive?.content
                                    
                                    logger.log(Level.INFO, "Location parameter: $location")
                                    
                                    if (location.isNullOrBlank()) {
                                        logger.log(Level.WARNING, "Location parameter is missing or blank")
                                        JsonRpcResponse(
                                            id = request.id,
                                            error = JsonRpcError(
                                                code = -32602,
                                                message = "location parameter is required"
                                            )
                                        )
                                    } else {
                                        try {
                                            val (lat, lon) = parseLocation(location, httpClient, json, logger)
                                            
                                            logger.log(Level.INFO, "Getting weather for coordinates: $lat, $lon")
                                            
                                            val userAgent = "MCP-Weather-Server/1.0 (contact: weather@example.com)"
                                            
                                            val pointsUrl = "https://api.weather.gov/points/$lat,$lon"
                                            val pointsRequest = Request.Builder()
                                                .url(pointsUrl)
                                                .header("User-Agent", userAgent)
                                                .build()
                                            
                                            val pointsResponse = httpClient.newCall(pointsRequest).execute()
                                            val pointsBody = pointsResponse.body?.string() ?: ""
                                            
                                            if (!pointsResponse.isSuccessful) {
                                                logger.log(Level.WARNING, "Weather.gov points request failed: ${pointsResponse.code} - $pointsBody")
                                                JsonRpcResponse(
                                                    id = request.id,
                                                    result = buildJsonObject {
                                                        putJsonArray("content") {
                                                            addJsonObject {
                                                                put("type", "text")
                                                                put("text", "Error: Unable to get weather grid point for location: $location")
                                                            }
                                                        }
                                                        put("isError", true)
                                                    }
                                                )
                                            } else {
                                                val pointsData = json.decodeFromString<PointsResponse>(pointsBody)
                                                val forecastUrl = pointsData.properties.forecast
                                                
                                                logger.log(Level.INFO, "Getting forecast from: $forecastUrl")
                                                
                                                val forecastRequest = Request.Builder()
                                                    .url(forecastUrl)
                                                    .header("User-Agent", userAgent)
                                                    .build()
                                                
                                                val forecastResponse = httpClient.newCall(forecastRequest).execute()
                                                val forecastBody = forecastResponse.body?.string() ?: ""
                                                
                                                if (!forecastResponse.isSuccessful) {
                                                    logger.log(Level.WARNING, "Weather.gov forecast request failed: ${forecastResponse.code} - $forecastBody")
                                                    JsonRpcResponse(
                                                        id = request.id,
                                                        result = buildJsonObject {
                                                            putJsonArray("content") {
                                                                addJsonObject {
                                                                    put("type", "text")
                                                                    put("text", "Error: Unable to get weather forecast for location: $location")
                                                                }
                                                            }
                                                            put("isError", true)
                                                        }
                                                    )
                                                } else {
                                                    val forecastData = json.decodeFromString<ForecastResponse>(forecastBody)
                                                    val currentPeriod = forecastData.properties.periods.firstOrNull()
                                                    
                                                    if (currentPeriod != null) {
                                                        val temp = currentPeriod.temperature
                                                        val unit = currentPeriod.temperatureUnit
                                                        val forecast = currentPeriod.shortForecast
                                                        val wind = currentPeriod.windSpeed
                                                        
                                                        val sentences = listOf(
                                                            "$location: $temp°$unit, $forecast.",
                                                            "Wind: $wind."
                                                        )
                                                        
                                                        val result = sentences.joinToString(" ")
                                                        
                                                        JsonRpcResponse(
                                                            id = request.id,
                                                            result = buildJsonObject {
                                                                putJsonArray("content") {
                                                                    addJsonObject {
                                                                        put("type", "text")
                                                                        put("text", result)
                                                                    }
                                                                }
                                                                put("isError", false)
                                                            }
                                                        )
                                                    } else {
                                                        JsonRpcResponse(
                                                            id = request.id,
                                                            result = buildJsonObject {
                                                                putJsonArray("content") {
                                                                    addJsonObject {
                                                                        put("type", "text")
                                                                        put("text", "Error: No forecast data available for location: $location")
                                                                    }
                                                                }
                                                                put("isError", true)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            logger.log(Level.SEVERE, "Error in get_weather", e)
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
                                            message = "Tool not found: $toolName. Available tools: get_weather"
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
        logger.log(Level.INFO, "MCP Weather Server starting on port $port...")
        val serverInstance = server.start(wait = false)
        logger.log(Level.INFO, "MCP Weather Server started successfully on port $port")
        logger.log(Level.INFO, "MCP endpoint available at: http://0.0.0.0:$port/mcp")
        logger.log(Level.INFO, "For Android emulator use: http://10.0.2.2:$port/mcp")
        
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.log(Level.INFO, "Shutting down MCP Weather Server...")
            serverInstance.stop(1000, 2000)
        })
        
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Failed to start server", e)
        e.printStackTrace()
        System.exit(1)
    }
}

