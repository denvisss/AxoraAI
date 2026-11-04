package com.imageai.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    val role: String,           // "user" or "assistant"
    val text: String,           // текстовый контент сообщения
    val imageUrl: String? = null,  // URL сгенерированного изображения (для assistant)
    val originalPrompt: String? = null,  // оригинальный промт на русском
    val translatedPrompt: String? = null,  // перевод на английский
    val timestamp: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)
