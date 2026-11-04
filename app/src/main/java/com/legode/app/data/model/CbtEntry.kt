package com.legode.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cbt_entries")
data class CbtEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // Формат: YYYY-MM-DD
    val timestamp: Long = System.currentTimeMillis(),
    val timeFormatted: String,
    val situation: String,
    val automaticThought: String,
    val beliefBefore: Int, // 0..100%
    val emotion: String,
    val intensityBefore: Int, // 0..100%
    val somaticReaction: String,
    val distortions: List<String>,
    val evidenceFor: String,
    val evidenceAgainst: String,
    val adaptiveResponse: String,
    val beliefAfter: Int, // 0..100%
    val intensityAfter: Int // 0..100%
)