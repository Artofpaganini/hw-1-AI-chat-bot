package com.example.aiagentchat.data.toon

import com.example.aiagentchat.data.api.ChatResponse
import com.example.aiagentchat.data.api.UsageDto
import com.example.aiagentchat.domain.model.AiModel
import com.example.aiagentchat.domain.model.AiResponse
import com.example.aiagentchat.domain.model.Message
import com.example.aiagentchat.domain.model.MessageMetrics

/**
 * Конвертер между доменными моделями и TOON форматом
 */
object ToonConverter {
    
    /**
     * Конвертирует Message в TOON
     */
    fun messageToToon(message: Message): String {
        val map = mutableMapOf<String, Any?>(
            "id" to message.id,
            "content" to message.content,
            "isUser" to message.isUser,
            "timestamp" to message.timestamp
        )
        
        message.model?.let {
            map["model"] = it.displayName
        }
        
        message.metrics?.let { metrics ->
            map["metrics"] = mapOf(
                "responseTimeMs" to metrics.responseTimeMs,
                "inputTokens" to metrics.inputTokens,
                "outputTokens" to metrics.outputTokens,
                "costUsd" to metrics.costUsd
            )
        }
        
        return ToonEncoder.encode(map)
    }
    
    /**
     * Конвертирует список сообщений в TOON (табличный формат)
     */
    fun messagesToToon(messages: List<Message>): String {
        if (messages.isEmpty()) return "messages[0]:"
        
        val data = mapOf(
            "messages" to messages.map { msg ->
                mutableMapOf<String, Any?>(
                    "id" to msg.id,
                    "content" to msg.content,
                    "isUser" to msg.isUser,
                    "model" to (msg.model?.displayName ?: ""),
                    "timestamp" to msg.timestamp,
                    "responseTimeMs" to (msg.metrics?.responseTimeMs ?: 0),
                    "inputTokens" to (msg.metrics?.inputTokens ?: 0),
                    "outputTokens" to (msg.metrics?.outputTokens ?: 0),
                    "costUsd" to (msg.metrics?.costUsd ?: 0.0)
                )
            }
        )
        
        return ToonEncoder.encode(data)
    }
    
    /**
     * Конвертирует AiResponse в TOON
     */
    fun aiResponseToToon(response: AiResponse): String {
        val map = mapOf(
            "content" to response.content,
            "inputTokens" to response.inputTokens,
            "outputTokens" to response.outputTokens
        )
        return ToonEncoder.encode(map)
    }
    
    /**
     * Конвертирует ChatResponse (API ответ) в TOON
     */
    fun chatResponseToToon(response: ChatResponse): String {
        val choices = response.choices.map { choice ->
            mapOf(
                "role" to choice.message.role,
                "content" to choice.message.content,
                "finishReason" to (choice.finishReason ?: "")
            )
        }
        
        val map = mutableMapOf<String, Any?>(
            "id" to (response.id ?: ""),
            "choices" to choices
        )
        
        response.usage?.let { usage ->
            map["usage"] = mapOf(
                "promptTokens" to usage.promptTokens,
                "completionTokens" to usage.completionTokens,
                "totalTokens" to usage.totalTokens
            )
        }
        
        return ToonEncoder.encode(map)
    }
    
    /**
     * Конвертирует MessageMetrics в TOON
     */
    fun metricsToToon(metrics: MessageMetrics): String {
        val map = mapOf(
            "responseTimeMs" to metrics.responseTimeMs,
            "inputTokens" to metrics.inputTokens,
            "outputTokens" to metrics.outputTokens,
            "costUsd" to metrics.costUsd
        )
        return ToonEncoder.encode(map)
    }
    
    /**
     * Парсит TOON в Message
     */
    fun toonToMessage(toon: String): Message? {
        return try {
            val map = ToonDecoder.decode(toon)
            
            val id = map["id"] as? String ?: return null
            val content = map["content"] as? String ?: return null
            val isUser = map["isUser"] as? Boolean ?: return null
            val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            
            val modelName = map["model"] as? String
            val model = modelName?.let { AiModel.fromDisplayName(it) }
            
            @Suppress("UNCHECKED_CAST")
            val metricsMap = map["metrics"] as? Map<String, Any?>
            val metrics = metricsMap?.let { m ->
                MessageMetrics(
                    responseTimeMs = (m["responseTimeMs"] as? Number)?.toLong() ?: 0,
                    inputTokens = (m["inputTokens"] as? Number)?.toInt() ?: 0,
                    outputTokens = (m["outputTokens"] as? Number)?.toInt() ?: 0,
                    costUsd = (m["costUsd"] as? Number)?.toDouble() ?: 0.0
                )
            }
            
            Message(
                id = id,
                content = content,
                isUser = isUser,
                model = model,
                metrics = metrics,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Форматирует промпт для LLM в TOON стиле (экономит токены)
     */
    fun formatPromptAsToon(
        systemPrompt: String?,
        userMessage: String,
        context: Map<String, Any?>? = null
    ): String {
        return buildString {
            systemPrompt?.let {
                append("system: $it\n\n")
            }
            
            context?.let { ctx ->
                append("context:\n")
                append(ToonEncoder.encode(ctx).prependIndent("  "))
                append("\n\n")
            }
            
            append("user: $userMessage")
        }
    }
}





