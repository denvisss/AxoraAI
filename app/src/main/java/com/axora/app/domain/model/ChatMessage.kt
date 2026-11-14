package com.axora.app.domain.model

data class ChatMessage(
    val id: Long = 0,
    val role: Role,
    val text: String,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val textContent: String? = null,
    val originalPrompt: String? = null,
    val translatedPrompt: String? = null,
    val generationType: GenerationType = GenerationType.IMAGE,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
) {
    fun isUser(): Boolean = role == Role.USER
    fun isAssistant(): Boolean = role == Role.ASSISTANT
    fun isImageMessage(): Boolean = !imageUrl.isNullOrEmpty()
    fun isVideoMessage(): Boolean = !videoUrl.isNullOrEmpty()
    fun isAudioMessage(): Boolean = !audioUrl.isNullOrEmpty()
    fun hasMedia(): Boolean = isImageMessage() || isVideoMessage() || isAudioMessage()
}

enum class Role {
    USER,
    ASSISTANT
}

enum class GenerationType {
    IMAGE,
    VIDEO,
    AUDIO,
    TEXT
}