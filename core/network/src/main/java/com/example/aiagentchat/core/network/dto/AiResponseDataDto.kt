package com.example.aiagentchat.core.network.dto

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

data class AiResponseDataDto(
    @SerializedName("data")
    val data: ResponseContentDto
) {
    companion object {
        private val gson = Gson()
        private val prettyGson = GsonBuilder().setPrettyPrinting().create()

        fun fromJson(json: String): AiResponseDataDto? {
            return try {
                val cleanJson = extractJson(json)
                gson.fromJson(cleanJson, AiResponseDataDto::class.java)
            } catch (e: Exception) {
                null
            }
        }

        private fun extractJson(text: String): String {
            val trimmed = text.trim()
            val jsonBlockRegex = Regex("```json\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            jsonBlockRegex.find(trimmed)?.let { return it.groupValues[1].trim() }
            val codeBlockRegex = Regex("```\\s*([\\s\\S]*?)\\s*```")
            codeBlockRegex.find(trimmed)?.let { return it.groupValues[1].trim() }
            val jsonObjectRegex = Regex("\\{[\\s\\S]*\\}")
            jsonObjectRegex.find(trimmed)?.let { return it.value }
            return trimmed
        }
    }

    fun toJsonText(): String {
        return prettyGson.toJson(this)
    }
}

data class ResponseContentDto(
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("category")
    val category: String,
    @SerializedName("tags")
    val tags: List<String> = emptyList()
)

