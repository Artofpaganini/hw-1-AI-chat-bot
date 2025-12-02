package com.example.aiagentchat.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

/**
 * Модель JSON ответа от AI
 */
data class AiResponseData(
    @SerializedName("data")
    val data: ResponseContent
) {
    companion object {
        private val gson = Gson()
        private val prettyGson = GsonBuilder().setPrettyPrinting().create()
        
        fun fromJson(json: String): AiResponseData? {
            return try {
                val cleanJson = extractJson(json)
                gson.fromJson(cleanJson, AiResponseData::class.java)
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
    
    /**
     * Возвращает данные в виде отформатированного JSON
     */
    fun toJsonText(): String {
        return prettyGson.toJson(this)
    }
    
    /**
     * Возвращает данные в обычном текстовом формате
     */
    fun toFormattedText(): String {
        return buildString {
            append("📌 ")
            append(data.title)
            append("\n\n")
            append(data.description)
            append("\n\n")
            append("📂 ")
            append(data.category)
            
            if (data.tags.isNotEmpty()) {
                append("\n\n🏷 ")
                append(data.tags.joinToString(" • "))
            }
        }
    }
}

data class ResponseContent(
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("category")
    val category: String,
    
    @SerializedName("tags")
    val tags: List<String> = emptyList()
)
