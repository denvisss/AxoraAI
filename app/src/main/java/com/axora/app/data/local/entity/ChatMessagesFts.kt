package com.axora.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = ChatMessageEntity::class)
@Entity(tableName = "chat_messages_fts")
data class ChatMessagesFts(
    val text: String,
    val textContent: String?,
    val originalPrompt: String?
)
