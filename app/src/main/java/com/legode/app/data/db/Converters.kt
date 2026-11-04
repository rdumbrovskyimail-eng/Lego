package com.legode.app.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.legode.app.data.model.WorkoutExercise

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromExerciseList(value: List<WorkoutExercise>?): String {
        return gson.toJson(value ?: emptyList<WorkoutExercise>())
    }

    @TypeConverter
    fun toExerciseList(value: String?): List<WorkoutExercise> {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<WorkoutExercise>>() {}.type
        return gson.fromJson(value, listType)
    }
}