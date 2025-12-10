package com.example.aiagentchat.data.toon

import com.example.aiagentchat.domain.model.Message
import com.example.aiagentchat.domain.usecase.MetricsComparison
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экспортирует историю чата в TOON формате
 * Формат оптимизирован для минимального использования токенов
 */
object ChatHistoryExporter {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    /**
     * Экспортирует полную историю чата в TOON
     */
    fun exportChatHistory(
        messages: List<Message>,
        comparison: MetricsComparison? = null
    ): String {
        return buildString {
            // Заголовок
            append("chatExport:\n")
            append("  exportedAt: ${dateFormat.format(Date())}\n")
            append("  totalMessages: ${messages.size}\n\n")
            
            // Сообщения в табличном формате TOON
            if (messages.isNotEmpty()) {
                append(ToonConverter.messagesToToon(messages))
                append("\n\n")
            }
            
            // Сравнение метрик (если есть)
            comparison?.let { comp ->
                if (comp.isComplete) {
                    append(exportComparison(comp))
                }
            }
        }
    }
    
    /**
     * Экспортирует сравнение метрик в TOON
     */
    fun exportComparison(comparison: MetricsComparison): String {
        val compMap = mutableMapOf<String, Any?>(
            "prompt" to comparison.prompt
        )
        
        comparison.deepSeekMessage?.metrics?.let { m ->
            compMap["deepSeek"] = mapOf(
                "responseTimeMs" to m.responseTimeMs,
                "inputTokens" to m.inputTokens,
                "outputTokens" to m.outputTokens,
                "costUsd" to m.costUsd
            )
        }
        
        comparison.zaiMessage?.metrics?.let { m ->
            compMap["zai"] = mapOf(
                "responseTimeMs" to m.responseTimeMs,
                "inputTokens" to m.inputTokens,
                "outputTokens" to m.outputTokens,
                "costUsd" to m.costUsd
            )
        }
        
        comparison.timeDifferenceMs?.let { compMap["timeDiffMs"] = it }
        comparison.costDifferenceUsd?.let { compMap["costDiffUsd"] = it }
        comparison.tokensDifference?.let { compMap["tokensDiff"] = it }
        
        return buildString {
            append("comparison:\n")
            append(ToonEncoder.encode(compMap).prependIndent("  "))
        }
    }
    
    /**
     * Экспортирует сводку по использованию моделей
     */
    fun exportUsageSummary(messages: List<Message>): String {
        val aiMessages = messages.filter { !it.isUser && it.metrics != null }
        
        if (aiMessages.isEmpty()) {
            return "summary:\n  noData: true"
        }
        
        val byModel = aiMessages.groupBy { it.model?.displayName ?: "Unknown" }
        
        val summaryData = byModel.map { (modelName, msgs) ->
            val metrics = msgs.mapNotNull { it.metrics }
            mapOf(
                "model" to modelName,
                "requests" to msgs.size,
                "avgTimeMs" to metrics.map { it.responseTimeMs }.average().toLong(),
                "totalInputTokens" to metrics.sumOf { it.inputTokens },
                "totalOutputTokens" to metrics.sumOf { it.outputTokens },
                "totalCostUsd" to metrics.sumOf { it.costUsd }
            )
        }
        
        return ToonEncoder.encode(mapOf("summary" to summaryData))
    }
    
    /**
     * Парсит экспортированную историю чата из TOON
     */
    fun importChatHistory(toon: String): List<Message> {
        return try {
            val data = ToonDecoder.decode(toon)
            
            @Suppress("UNCHECKED_CAST")
            val messagesData = data["messages"] as? List<Map<String, Any?>> ?: return emptyList()
            
            messagesData.mapNotNull { msgMap ->
                ToonConverter.toonToMessage(ToonEncoder.encode(msgMap))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

