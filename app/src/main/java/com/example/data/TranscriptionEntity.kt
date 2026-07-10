package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val audioUri: String,
    val transcribedText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val language: String,
    val duration: Long = 0L
)
