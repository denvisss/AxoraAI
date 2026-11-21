package com.axora.app.domain.model

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val generatingTypes: Set<GenerationType> = emptySet(),
    val error: String? = null,
    val errorType: GenerationType? = null,
    val selectedType: GenerationType = GenerationType.IMAGE,
    val imageFormat: ImageFormat = ImageFormat.SQUARE,
    val streamingText: String? = null
) {
    val isGenerating: Boolean get() = selectedType in generatingTypes
}