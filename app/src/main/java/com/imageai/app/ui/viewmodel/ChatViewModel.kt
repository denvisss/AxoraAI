package com.imageai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imageai.app.domain.model.ChatMessage
import com.imageai.app.domain.model.ChatUiState
import com.imageai.app.domain.model.Role
import com.imageai.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    init {
        loadChatHistory()
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            repository.getChatHistory().collect { messageEntities ->
                _messages.value = messageEntities
                updateUiState()
            }
        }
    }

    fun sendMessage(prompt: String) {
        if (prompt.trim().isEmpty()) return

        val currentPrompt = prompt.trim()

        // Сохраняем сообщение пользователя
        val userMessage = ChatMessage(
            role = Role.USER,
            text = currentPrompt,
            originalPrompt = currentPrompt
        )

        viewModelScope.launch {
            repository.insertMessage(userMessage)
            updateMessages()
        }

        // Переводим на английский + генерируем изображение
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }

            // Переводим промт на английский
            val translatedPrompt = try {
                repository.translateToEnglish(currentPrompt)
            } catch (e: Exception) {
                currentPrompt // фолбэк если перевод не удался
            }

            try {
                val imageFile = repository.generateImage(translatedPrompt)
                val imageUrl = imageFile.absolutePath

                val assistantMessage = ChatMessage(
                    role = Role.ASSISTANT,
                    text = "Вот изображение по вашему запросу",
                    imageUrl = imageUrl,
                    originalPrompt = currentPrompt,
                    translatedPrompt = translatedPrompt
                )

                repository.insertMessage(assistantMessage)
                updateMessages()

                _uiState.update { it.copy(isGenerating = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        error = "Ошибка: ${e.message ?: "неизвестная ошибка"}"
                    )
                }
            }
        }
    }

    fun editMessage(messageId: Long, newText: String) {
        viewModelScope.launch {
            repository.updateMessage(messageId, newText, null)
            updateMessages()
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
            updateMessages()
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearAllMessages()
            updateMessages()
        }
    }

    private suspend fun updateMessages() {
        val recentMessages = repository.getChatHistory().first()
        _messages.value = recentMessages
        updateUiState()
    }

    private fun updateUiState() {
        _uiState.update { currentState ->
            currentState.copy(messages = _messages.value)
        }
    }

    fun handleVoiceResult(text: String) {
        if (text.isNotBlank()) {
            sendMessage(text.trim())
        }
    }

    fun handleVoiceError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun getImageFile(imageUrl: String): java.io.File? {
        return if (imageUrl.isNotEmpty()) {
            java.io.File(imageUrl)
        } else null
    }
}
