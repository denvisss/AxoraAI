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
) {
    fun isUser(): Boolean = role == Role.USER
    fun isAssistant(): Boolean = role == Role.ASSISTANT
    fun isImageMessage(): Boolean = !imageUrl.isNullOrEmpty()
    fun isVideoMessage(): Boolean = !videoUrl.isNullOrEmpty()
    fun isAudioMessage(): Boolean = !audioUrl.isNullOrEmpty()
    fun hasMedia(): Boolean = isImageMessage() || isVideoMessage() || isAudioMessage()
    fun isError(): Boolean =
        isAssistant() && !hasMedia() && textContent.isNullOrEmpty() && text.startsWith(ERROR_PREFIX)

    companion object {
        const val ERROR_PREFIX = "Ошибка"
    }
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
