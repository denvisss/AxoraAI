package com.axora.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["generationType", "timestamp"])]
)
data class ChatMessageEntity(
    val role: String,
    val text: String,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val textContent: String? = null,
    val originalPrompt: String? = null,
    val translatedPrompt: String? = null,
    val generationType: String = "IMAGE",
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)