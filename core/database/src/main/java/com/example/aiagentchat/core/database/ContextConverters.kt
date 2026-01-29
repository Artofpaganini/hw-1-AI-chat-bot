package com.example.aiagentchat.core.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContextConverters {
    private val gson: Gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type
    private val floatListType = object : TypeToken<List<Float>>() {}.type

    @TypeConverter
    fun fromJson(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toJson(list: List<String>?): String {
        return gson.toJson(list ?: emptyList<String>(), listType)
    }
    
    @TypeConverter
    fun fromFloatListJson(value: String?): List<Float> {
        if (value.isNullOrBlank()) return emptyList()
        return gson.fromJson(value, floatListType)
    }

    @TypeConverter
    fun toFloatListJson(list: List<Float>?): String {
        return gson.toJson(list ?: emptyList<Float>(), floatListType)
    }
}

