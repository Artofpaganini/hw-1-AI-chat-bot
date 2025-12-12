package com.example.aiagentchat.data.toon

/**
 * TOON (Token-Oriented Object Notation) Encoder
 * Преобразует объекты Kotlin в формат TOON для эффективной работы с LLM
 * 
 * Спецификация: https://github.com/toon-format/spec
 */
object ToonEncoder {
    
    /**
     * Кодирует Map в формат TOON
     */
    fun encode(data: Map<String, Any?>): String {
        return buildString {
            encodeMap(data, this, 0)
        }.trimEnd()
    }
    
    /**
     * Кодирует объект в TOON через рефлексию
     */
    fun encodeObject(obj: Any): String {
        val map = objectToMap(obj)
        return encode(map)
    }
    
    private fun encodeMap(map: Map<String, Any?>, sb: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)
        
        for ((key, value) in map) {
            when (value) {
                null -> {
                    sb.append("$indentStr$key: null\n")
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val nestedMap = value as Map<String, Any?>
                    sb.append("$indentStr$key:\n")
                    encodeMap(nestedMap, sb, indent + 1)
                }
                is List<*> -> {
                    encodeList(key, value, sb, indent)
                }
                is String -> {
                    // Если строка содержит спецсимволы, обрамляем кавычками
                    val escapedValue = if (value.contains(",") || value.contains("\n") || value.contains(":")) {
                        "\"${value.replace("\"", "\\\"")}\""
                    } else {
                        value
                    }
                    sb.append("$indentStr$key: $escapedValue\n")
                }
                is Number, is Boolean -> {
                    sb.append("$indentStr$key: $value\n")
                }
                else -> {
                    // Для других объектов — конвертируем в Map
                    val objMap = objectToMap(value)
                    sb.append("$indentStr$key:\n")
                    encodeMap(objMap, sb, indent + 1)
                }
            }
        }
    }
    
    private fun encodeList(key: String, list: List<*>, sb: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)
        
        if (list.isEmpty()) {
            sb.append("$indentStr$key[0]:\n")
            return
        }
        
        val firstItem = list.first()
        
        // Проверяем, является ли список примитивным (строки, числа)
        if (firstItem is String || firstItem is Number || firstItem is Boolean) {
            val values = list.joinToString(",")
            sb.append("$indentStr$key[${list.size}]: $values\n")
            return
        }
        
        // Список объектов — используем табличный формат TOON
        if (firstItem != null && firstItem !is Map<*, *>) {
            // Конвертируем объекты в Map
            val mapList = list.map { objectToMap(it!!) }
            encodeObjectList(key, mapList, sb, indent)
            return
        }
        
        // Список Map — табличный формат
        if (firstItem is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val mapList = list as List<Map<String, Any?>>
            encodeObjectList(key, mapList, sb, indent)
        }
    }
    
    private fun encodeObjectList(key: String, list: List<Map<String, Any?>>, sb: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)
        val rowIndent = "  ".repeat(indent + 1)
        
        if (list.isEmpty()) {
            sb.append("$indentStr$key[0]:\n")
            return
        }
        
        // Собираем все уникальные ключи из всех объектов
        val headers = list.flatMap { it.keys }.distinct()
        val headersStr = headers.joinToString(",")
        
        sb.append("$indentStr$key[${list.size}]{$headersStr}:\n")
        
        for (item in list) {
            val values = headers.map { header ->
                val value = item[header]
                formatValue(value)
            }.joinToString(",")
            sb.append("$rowIndent$values\n")
        }
    }
    
    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> {
                if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
                    "\"${value.replace("\"", "\\\"")}\""
                } else {
                    value
                }
            }
            is Map<*, *>, is List<*> -> {
                // Вложенные структуры в табличном формате — конвертируем в JSON-like строку
                "\"${value.toString().replace("\"", "\\\"")}\""
            }
            else -> value.toString()
        }
    }
    
    private fun objectToMap(obj: Any): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val clazz = obj::class.java
        
        // Получаем все поля (включая приватные)
        for (field in clazz.declaredFields) {
            if (field.name.startsWith("$")) continue // Пропускаем синтетические поля
            field.isAccessible = true
            try {
                result[field.name] = field.get(obj)
            } catch (e: Exception) {
                // Игнорируем недоступные поля
            }
        }
        
        // Для data class используем properties
        try {
            for (method in clazz.declaredMethods) {
                if (method.name.startsWith("get") && method.parameterCount == 0) {
                    val propName = method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
                    if (propName !in result) {
                        result[propName] = method.invoke(obj)
                    }
                }
                // Kotlin properties
                if (method.name.startsWith("component") || method.name == "copy") continue
                if (!method.name.startsWith("get") && method.parameterCount == 0 && method.returnType != Void.TYPE) {
                    val name = method.name
                    if (name !in result && !name.startsWith("$")) {
                        try {
                            result[name] = method.invoke(obj)
                        } catch (e: Exception) {
                            // Игнорируем
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки рефлексии
        }
        
        return result
    }
}



