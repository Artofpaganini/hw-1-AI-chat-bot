package com.example.aiagentchat.core.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContextConverters {
    private val gson: Gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun fromJson(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toJson(list: List<String>?): String {
        return gson.toJson(list ?: emptyList<String>(), listType)
    }
}

