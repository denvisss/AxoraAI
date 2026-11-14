package com.axora.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axora.app.R
import com.axora.app.data.repository.ChatRepository
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.ChatUiState
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import com.axora.app.domain.model.Role
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ChatViewModel(
    private val repository: ChatRepository,
    private val strings: StringResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(imageFormat = repository.getImageFormat()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var historyJob: Job? = null
    private val generationJobs = mutableMapOf<GenerationType, Job>()

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

    fun setImageFormat(format: ImageFormat) {
        if (format == _uiState.value.imageFormat) return
        _uiState.update { it.copy(imageFormat = format) }
        repository.setImageFormat(format)
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

    fun regenerate(message: ChatMessage) {
        val prompt = message.originalPrompt ?: return
        val type = message.generationType
        if (type in _uiState.value.generatingTypes) return
        startGeneration(prompt, type, before = {})
    }

    fun cancelGeneration(type: GenerationType = _uiState.value.selectedType) {
        generationJobs.remove(type)?.cancel()
        _uiState.update { it.copy(generatingTypes = it.generatingTypes - type) }
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
        val job = viewModelScope.launch {
            var produced: ChatMessage? = null
            try {
                before()
                produced = produce()
                repository.insertMessage(produced)
                repository.autoSaveToGallery(produced)
            } catch (e: CancellationException) {
                produced?.let { message ->
                    withContext(NonCancellable) { repository.deleteMediaOf(message) }
                }
                throw e
            } catch (e: Exception) {
                val message = errorMessage(e)
                if (insertErrorMessage) {
                    repository.insertMessage(
                        ChatMessage(
                            role = Role.ASSISTANT,
                            text = message,
                            originalPrompt = prompt,
                            generationType = type,
                            isError = true
                        )
                    )
                }
                _uiState.update { it.copy(error = message, errorType = type) }
            } finally {
                _uiState.update { it.copy(generatingTypes = it.generatingTypes - type) }
                generationJobs.remove(type)
            }
        }
        generationJobs[type] = job
    }

    private suspend fun generate(prompt: String, type: GenerationType): ChatMessage {
        if (type == GenerationType.TEXT) {
            val history = _uiState.value.messages.filter { !it.isError }
            val content = repository.generateText(prompt, history)
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
            text = strings.get(
                when (type) {
                    GenerationType.VIDEO -> R.string.video_ready
                    GenerationType.AUDIO -> R.string.audio_ready
                    else -> R.string.image_ready
                }
            ),
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
                text = strings.get(R.string.image_edited),
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
        if (e is UnknownHostException || e is ConnectException) return strings.get(R.string.error_offline)
        if (e is SocketTimeoutException) return strings.get(R.string.error_timeout)
        if (e !is HttpException) {
            val detail = e.message
            return if (detail.isNullOrBlank()) strings.get(R.string.error_unknown)
            else strings.get(R.string.error_unknown_detail, detail)
        }
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        return when {
            e.code() == 402 && body.contains("FREE_LIMIT") -> strings.get(R.string.error_free_limit)
            e.code() == 404 && body.contains("Classic API") -> strings.get(R.string.error_model_key_required)
            e.code() == 401 -> strings.get(R.string.error_invalid_key)
            e.code() == 402 -> strings.get(R.string.error_insufficient_balance)
            e.code() == 429 -> strings.get(R.string.error_rate_limited)
            e.code() == 503 -> strings.get(R.string.error_service_unavailable)
            body.isBlank() -> strings.get(R.string.error_http, e.code())
            else -> strings.get(R.string.error_http_detail, e.code(), serverMessage(body) ?: body.take(200))
        }
    }

    private fun serverMessage(body: String): String? = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        val error = root.get("error")
        val message = if (error != null && error.isJsonObject) error.asJsonObject.get("message") else root.get("message")
        message?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }.getOrNull()
}