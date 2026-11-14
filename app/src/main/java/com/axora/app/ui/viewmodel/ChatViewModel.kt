package com.axora.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axora.app.data.repository.ChatRepository
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.ChatUiState
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var historyJob: Job? = null

    init {
        loadHistory(GenerationType.IMAGE)
    }

    private fun loadHistory(type: GenerationType) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.getChatHistory(type).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun setGenerationType(type: GenerationType) {
        if (type == _uiState.value.selectedType) return
        _uiState.update { it.copy(selectedType = type, error = null, errorType = null) }
        loadHistory(type)
    }

    fun sendMessage(prompt: String, type: GenerationType = _uiState.value.selectedType) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || type in _uiState.value.generatingTypes) return
        startGeneration(trimmed, type, before = {
            repository.insertMessage(
                ChatMessage(role = Role.USER, text = trimmed, originalPrompt = trimmed, generationType = type)
            )
        })
    }

    private fun startGeneration(
        prompt: String,
        type: GenerationType,
        before: suspend () -> Unit,
        insertErrorMessage: Boolean = true,
        produce: suspend () -> ChatMessage = { generate(prompt, type) }
    ) {
        _uiState.update {
            it.copy(generatingTypes = it.generatingTypes + type, error = null, errorType = null)
        }
        viewModelScope.launch {
            try {
                before()
                val produced = produce()
                repository.insertMessage(produced)
                repository.autoSaveToGallery(produced)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = "${ChatMessage.ERROR_PREFIX}: ${errorMessage(e)}"
                if (insertErrorMessage) {
                    repository.insertMessage(
                        ChatMessage(role = Role.ASSISTANT, text = message, originalPrompt = prompt, generationType = type)
                    )
                }
                _uiState.update { it.copy(error = message, errorType = type) }
            } finally {
                _uiState.update { it.copy(generatingTypes = it.generatingTypes - type) }
            }
        }
    }

    private suspend fun generate(prompt: String, type: GenerationType): ChatMessage {
        if (type == GenerationType.TEXT) {
            val content = repository.generateText(prompt)
            return ChatMessage(
                role = Role.ASSISTANT,
                text = content,
                textContent = content,
                originalPrompt = prompt,
                generationType = type
            )
        }
        val translated = repository.translateToEnglish(prompt)
        val path = repository.generateContent(translated, type).absolutePath
        return ChatMessage(
            role = Role.ASSISTANT,
            text = when (type) {
                GenerationType.VIDEO -> "Вот видео по вашему запросу"
                GenerationType.AUDIO -> "Вот аудио по вашему запросу"
                else -> "Вот изображение по вашему запросу"
            },
            imageUrl = path.takeIf { type == GenerationType.IMAGE },
            videoUrl = path.takeIf { type == GenerationType.VIDEO },
            audioUrl = path.takeIf { type == GenerationType.AUDIO },
            originalPrompt = prompt,
            translatedPrompt = translated,
            generationType = type
        )
    }

    fun editImage(message: ChatMessage, instruction: String) {
        val trimmed = instruction.trim()
        val sourcePath = message.imageUrl ?: return
        val type = GenerationType.IMAGE
        if (trimmed.isEmpty() || type in _uiState.value.generatingTypes) return
        startGeneration(
            prompt = trimmed,
            type = type,
            before = {
                repository.insertMessage(
                    ChatMessage(role = Role.USER, text = trimmed, originalPrompt = trimmed, generationType = type)
                )
            },
            // «Повторить» в бабле ошибки сгенерировал бы новую картинку вместо правки исходной.
            insertErrorMessage = false
        ) {
            val translated = repository.translateToEnglish(trimmed)
            val edited = repository.editImage(File(sourcePath), translated)
            ChatMessage(
                role = Role.ASSISTANT,
                text = "Вот изменённое изображение",
                imageUrl = edited.absolutePath,
                originalPrompt = trimmed,
                translatedPrompt = translated,
                generationType = type
            )
        }
    }

    fun retry(message: ChatMessage) {
        val prompt = message.originalPrompt ?: return
        val type = message.generationType
        if (type in _uiState.value.generatingTypes) return
        startGeneration(prompt, type, before = { repository.deleteMessage(message.id) })
    }

    fun editMessage(messageId: Long, newText: String) {
        viewModelScope.launch { repository.updateMessage(messageId, newText, newText) }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    fun clearChat() {
        viewModelScope.launch { repository.clearChat(_uiState.value.selectedType) }
    }

    fun handleVoiceResult(text: String) {
        if (text.isNotBlank()) sendMessage(text)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun errorMessage(e: Exception): String {
        if (e is UnknownHostException || e is ConnectException) return "нет подключения к интернету"
        if (e is SocketTimeoutException) return "сервер не ответил вовремя, попробуйте ещё раз"
        if (e !is HttpException) return e.message ?: "неизвестная ошибка"
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        return when {
            e.code() == 402 && body.contains("FREE_LIMIT") ->
                "бесплатный лимит: 1 запрос в 45 секунд. Повторите позже или укажите API-ключ в настройках."
            e.code() == 404 && body.contains("Classic API") ->
                "эта модель доступна только с API-ключом. Получите ключ и укажите его в настройках."
            e.code() == 401 ->
                "сервер требует действующий API-ключ. Проверьте ключ в настройках."
            e.code() == 402 ->
                "недостаточно баланса Pollinations для этой генерации."
            e.code() == 429 ->
                "слишком много запросов. Повторите чуть позже."
            else -> "HTTP ${e.code()}${body.take(200).takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
        }
    }
}
