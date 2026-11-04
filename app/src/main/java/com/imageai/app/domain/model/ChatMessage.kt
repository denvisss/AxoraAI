package com.imageai.app.domain.model

import java.io.File

data class ChatMessage(
    val id: Long = 0,
    val role: Role,
    val text: String,
    val imageUrl: String? = null,
    val imageFile: File? = null,  // локальный файл изображения
    val originalPrompt: String? = null,  // оригинальный промт на русском
    val translatedPrompt: String? = null,  // перевод на английский
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun isUser(): Boolean = role == Role.USER
    fun isAssistant(): Boolean = role == Role.ASSISTANT
    fun isImageMessage(): Boolean = imageUrl != null || imageFile != null
}

enum class Role {
    USER,
    ASSISTANT
}
