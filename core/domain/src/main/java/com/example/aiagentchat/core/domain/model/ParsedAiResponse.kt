package com.example.aiagentchat.core.domain.model

data class ParsedAiResponse(
    val title: String,
    val description: String,
    val category: String,
    val tags: List<String> = emptyList()
) {
    fun toJsonText(): String {
        return buildString {
            appendLine("{")
            appendLine("  \"data\": {")
            appendLine("    \"title\": \"$title\",")
            appendLine("    \"description\": \"$description\",")
            appendLine("    \"category\": \"$category\",")
            appendLine("    \"tags\": [")
            tags.forEachIndexed { index, tag ->
                append("      \"$tag\"")
                if (index < tags.size - 1) append(",")
                appendLine()
            }
            appendLine("    ]")
            appendLine("  }")
            append("}")
        }
    }

    fun toFormattedText(): String {
        return buildString {
            append("📌 ")
            append(title)
            append("\n\n")
            append(description)
            append("\n\n")
            append("📂 ")
            append(category)
            if (tags.isNotEmpty()) {
                append("\n\n🏷 ")
                append(tags.joinToString(" • "))
            }
        }
    }
}

