package com.example.aiagentchat.data.toon

/**
 * TOON (Token-Oriented Object Notation) Decoder
 * Преобразует TOON формат обратно в структуры данных Kotlin
 * 
 * Спецификация: https://github.com/toon-format/spec
 */
object ToonDecoder {
    
    private val ARRAY_HEADER_REGEX = Regex("""^(\w+)\[(\d+)](?:\{([^}]+)})?:\s*(.*)$""")
    private val KEY_VALUE_REGEX = Regex("""^(\w+):\s*(.*)$""")
    
    /**
     * Декодирует TOON строку в Map
     */
    fun decode(toon: String): Map<String, Any?> {
        val lines = toon.lines().filter { it.isNotBlank() }
        return parseBlock(lines, 0).first
    }
    
    private fun parseBlock(lines: List<String>, startIndent: Int): Pair<MutableMap<String, Any?>, Int> {
        val result = mutableMapOf<String, Any?>()
        var i = 0
        
        while (i < lines.size) {
            val line = lines[i]
            val currentIndent = countIndent(line)
            
            if (currentIndent < startIndent) {
                break
            }
            
            if (currentIndent > startIndent) {
                i++
                continue
            }
            
            val trimmedLine = line.trim()
            
            // Проверяем на массив
            val arrayMatch = ARRAY_HEADER_REGEX.matchEntire(trimmedLine)
            if (arrayMatch != null) {
                val (key, size, headers, inlineValue) = arrayMatch.destructured
                val arraySize = size.toInt()
                
                if (headers.isNotEmpty()) {
                    // Табличный формат: key[size]{field1,field2}:
                    val headerList = headers.split(",").map { it.trim() }
                    val items = mutableListOf<Map<String, Any?>>()
                    
                    var j = i + 1
                    while (j < lines.size && items.size < arraySize) {
                        val rowLine = lines[j]
                        val rowIndent = countIndent(rowLine)
                        
                        if (rowIndent <= currentIndent) break
                        
                        val values = parseCSVLine(rowLine.trim())
                        val item = mutableMapOf<String, Any?>()
                        
                        for ((idx, header) in headerList.withIndex()) {
                            item[header] = if (idx < values.size) parseValue(values[idx]) else null
                        }
                        
                        items.add(item)
                        j++
                    }
                    
                    result[key] = items
                    i = j
                    continue
                } else if (inlineValue.isNotBlank()) {
                    // Inline массив: key[size]: val1,val2,val3
                    val values = parseCSVLine(inlineValue)
                    result[key] = values.map { parseValue(it) }
                    i++
                    continue
                } else {
                    // Пустой массив или массив на следующих строках
                    result[key] = emptyList<Any>()
                    i++
                    continue
                }
            }
            
            // Проверяем на key: value
            val kvMatch = KEY_VALUE_REGEX.matchEntire(trimmedLine)
            if (kvMatch != null) {
                val key = kvMatch.groupValues[1]
                val value = kvMatch.groupValues[2]
                
                if (value.isBlank()) {
                    // Вложенный объект
                    val subLines = lines.subList(i + 1, lines.size)
                    val (subMap, consumed) = parseBlock(subLines, currentIndent + 2)
                    result[key] = subMap
                    i += consumed + 1
                    continue
                } else {
                    // Простое значение
                    result[key] = parseValue(value)
                    i++
                    continue
                }
            }
            
            i++
        }
        
        return result to i
    }
    
    private fun countIndent(line: String): Int {
        var count = 0
        for (char in line) {
            when (char) {
                ' ' -> count++
                '\t' -> count += 2
                else -> break
            }
        }
        return count
    }
    
    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < line.length) {
            val char = line[i]
            
            when {
                char == '"' && !inQuotes -> {
                    inQuotes = true
                }
                char == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }
        
        result.add(current.toString().trim())
        return result
    }
    
    private fun parseValue(value: String): Any? {
        val trimmed = value.trim()
        
        return when {
            trimmed == "null" -> null
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> {
                trimmed.substring(1, trimmed.length - 1).replace("\\\"", "\"")
            }
            trimmed.toIntOrNull() != null -> trimmed.toInt()
            trimmed.toLongOrNull() != null -> trimmed.toLong()
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> trimmed
        }
    }
}







