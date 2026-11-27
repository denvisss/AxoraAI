package com.axora.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axora.app.R
import com.axora.app.data.repository.ChatRepository
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.ChatUiState
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import com.axora.app.domain.model.ImageModel
import com.axora.app.domain.model.Role
import com.axora.app.domain.model.TextModel
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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

    // Живой каталог моделей картинок: null, пока запрос к API не завершён (успешно или нет).
    private val _imageModels = MutableStateFlow<List<ImageModel>?>(null)

    init {
        viewModelScope.launch {
            _imageModels.value = repository.fetchImageModels()
        }
    }

    /** Модели для селектора: живой список с API, при ошибке — статический каталог. */
    fun availableImageModels(): List<ImageModel> = _imageModels.value ?: ImageModel.catalog

    private val _uiState = MutableStateFlow(
        ChatUiState(
            imageFormat = repository.getImageFormat(),
            imageModel = repository.getImageModel(),
            textModel = repository.getTextModel()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Инкремент при каждом успешном завершении генерации — для виброотклика в UI.
    private val _generationCompleted = MutableStateFlow(0L)
    val generationCompleted: StateFlow<Long> = _generationCompleted.asStateFlow()

    // Одноразовое событие: генерация закончилась, когда приложение было в фоне.
    private val _completedInBackground = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val completedInBackground: SharedFlow<Boolean> = _completedInBackground.asSharedFlow()

    // Уведомление нужно только когда пользователь ушёл из приложения во время генерации.
    // AtomicBoolean: lifecycle-callback и генерирующая корутина — разные потоки.
    val appInForeground = java.util.concurrent.atomic.AtomicBoolean(true)

    val galleryImages: StateFlow<List<ChatMessage>> = repository.getAllImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var historyJob: Job? = null
    private val generationJobs = mutableMapOf<GenerationType, Job>()

    // Подгружается по странице при прокрутке вверх; Flow-запрос с LIMIT пересдаёт и новые сообщения.
    private val historyLimit = MutableStateFlow(HISTORY_PAGE)
    private val _historyHasMore = MutableStateFlow(false)
    val historyHasMore: StateFlow<Boolean> = _historyHasMore.asStateFlow()

    init {
        loadHistory(GenerationType.IMAGE)
    }

    private fun loadHistory(type: GenerationType) {
        historyJob?.cancel()
        historyLimit.value = HISTORY_PAGE
        historyJob = viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            historyLimit.flatMapLatest { limit -> repository.getChatHistory(type, limit) }.collect { messages ->
                _historyHasMore.value = messages.size >= historyLimit.value
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun loadMoreHistory() {
        if (!_historyHasMore.value || _uiState.value.generatingTypes.isNotEmpty()) return
        historyLimit.value += HISTORY_PAGE
    }

    suspend fun searchHistory(query: String): List<ChatMessage> {
        val sanitized = query.trim()
        if (sanitized.isEmpty()) return emptyList()
        // Кавычка — синтаксис FTS: экранируем удвоением и всегда шлём фразой,
        // иначе запрос вида "a OR ( b" роняет MATCH с SQLiteException.
        val escaped = sanitized.replace("\"", "\"\"")
        return try {
            repository.searchHistory("\"$escaped\"")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setGenerationType(type: GenerationType) {
        if (type == _uiState.value.selectedType) return
        _uiState.update { it.copy(selectedType = type, error = null, errorType = null, streamingText = null) }
        loadHistory(type)
    }

    fun setTextModel(modelId: String) {
        if (modelId == _uiState.value.textModel) return
        _uiState.update { it.copy(textModel = modelId) }
        repository.setTextModel(modelId)
    }

    // id строкой, а не ImageModel: в живом каталоге Cloudflare есть модели,
    // которых нет в статическом списке — их тоже нужно уметь выбрать.
    fun setImageModel(modelId: String) {
        if (modelId == _uiState.value.imageModel) return
        _uiState.update { it.copy(imageModel = modelId) }
        repository.setImageModel(modelId)
    }

    fun setImageFormat(format: ImageFormat) {
        if (format == _uiState.value.imageFormat) return
        _uiState.update { it.copy(imageFormat = format) }
        repository.setImageFormat(format)
    }

    fun sendMessage(prompt: String, type: GenerationType = _uiState.value.selectedType) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || type in _uiState.value.generatingTypes) return
        val textHistory = textHistorySnapshot()
        val attachment = _pendingTextAttachment.value
        _pendingTextAttachment.value = null
        startGeneration(
            trimmed,
            type,
            before = {
                repository.insertMessage(
                    ChatMessage(
                        role = Role.USER,
                        text = trimmed,
                        imageUrl = attachment?.absolutePath,
                        originalPrompt = trimmed,
                        generationType = type
                    )
                )
            },
            produce = { generate(trimmed, type, textHistory, attachment) }
        )
    }

    fun regenerate(message: ChatMessage) {
        val prompt = message.originalPrompt ?: return
        val type = message.generationType
        if (type in _uiState.value.generatingTypes) return
        // Снапшот до регенерации включает сам ответ — исключаем, иначе модель
        // видит свой прошлый ответ как часть диалога до промпта.
        val textHistory = textHistorySnapshot().filter { it.id != message.id }
        val attachment = message.imageUrl?.let { File(it) }
        startGeneration(prompt, type, before = {}, produce = { generate(prompt, type, textHistory, attachment) })
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
        produce: suspend () -> ChatMessage
    ) {
        _uiState.update {
            it.copy(generatingTypes = it.generatingTypes + type, error = null, errorType = null)
        }
        val job = viewModelScope.launch {
            var produced: ChatMessage? = null
            try {
                before()
                produced = produceWithRetry(produce)
                repository.insertMessage(produced)
                repository.autoSaveToGallery(produced)
                _generationCompleted.value += 1
                _completedInBackground.emit(!appInForeground.get())
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
                _uiState.update { it.copy(generatingTypes = it.generatingTypes - type, streamingText = null) }
                generationJobs.remove(type)
            }
        }
        generationJobs[type] = job
    }

    // Бесплатные тарифы часто троттлят (429) или отдают 503/таймаут — пару тихих
    // повторов с паузой спасают от ручного «Повторить». Перед повтором сбрасываем
    // streamingText: дельты первой попытки могли уйти в UI, иначе текст «отматывается».
    private suspend fun produceWithRetry(produce: suspend () -> ChatMessage): ChatMessage {
        var lastError: Exception? = null
        repeat(GENERATION_ATTEMPTS - 1) { attempt ->
            try {
                return produce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isTransient(e)) throw e
                lastError = e
                _uiState.update { it.copy(streamingText = null) }
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return produce()
    }

    private fun isTransient(e: Exception): Boolean {
        if (e is retrofit2.HttpException) {
            return e.code() == 429 || e.code() == 502 || e.code() == 503
        }
        // Стриминг Groq/Cloudflare кидает IOException("HTTP 502: ...") вместо
        // HttpException — код вытаскиваем из текста, иначе повторов не будет.
        if (e is java.io.IOException) {
            val code = HTTP_CODE_REGEX.find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
            if (code != null) return code == 429 || code == 502 || code == 503
        }
        // OCI-код Cloudflare 5028 = модель перегружена, приходит как HTTP-ошибка выше.
        return e is java.net.SocketTimeoutException || e is java.net.ConnectException
    }

    // Снимок истории берётся до вставки сообщения: иначе при смене вкладки
    // во время генерации контекст текста собирался бы из чужого чата.
    private fun textHistorySnapshot(): List<ChatMessage> =
        _uiState.value.messages.filter { !it.isError }

    private suspend fun generate(
        prompt: String,
        type: GenerationType,
        textHistory: List<ChatMessage>,
        attachedImage: File? = null
    ): ChatMessage {
        if (type == GenerationType.TEXT) {
            val content = repository.generateTextStreamed(
                prompt,
                textHistory,
                onDelta = { partial -> _uiState.update { it.copy(streamingText = partial) } },
                attachedImage = attachedImage
            )
            return ChatMessage(
                role = Role.ASSISTANT,
                text = content,
                textContent = content,
                originalPrompt = prompt,
                generationType = type
            )
        }
        val translated = repository.translateToEnglish(prompt)
        val path = repository.generateContent(translated, type, _uiState.value.imageModel).absolutePath
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
            val edited = repository.editImage(File(sourcePath), translated, _uiState.value.imageModel)
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
        val textHistory = textHistorySnapshot()
        startGeneration(
            prompt,
            type,
            before = { repository.deleteMessage(message.id) },
            produce = { generate(prompt, type, textHistory) }
        )
    }

    // Вложение для следующего текстового промпта (vision). StateFlow: превью в UI
    // должно обновляться при установке/снятии, в т.ч. после поворота экрана.
    private val _pendingTextAttachment = MutableStateFlow<File?>(null)
    val pendingTextAttachment: StateFlow<File?> = _pendingTextAttachment.asStateFlow()

    fun attachImageForText(file: File) {
        _pendingTextAttachment.value = file
    }

    fun clearPendingTextAttachment() {
        _pendingTextAttachment.value = null
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

    // Изображение, пришедшее через «Поделиться»: попадает в чат как сообщение пользователя.
    fun importImageFile(file: File, type: GenerationType = GenerationType.IMAGE) {
        viewModelScope.launch {
            repository.insertMessage(
                ChatMessage(
                    role = Role.USER,
                    text = "",
                    imageUrl = file.absolutePath,
                    generationType = type
                )
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun errorMessage(e: Exception): String {
        if (e is UnknownHostException || e is ConnectException) return strings.get(R.string.error_offline)
        if (e is SocketTimeoutException) return strings.get(R.string.error_timeout)
        // Прокси отвечает на CONNECT ошибкой — виноват прокси, а не сервис; подсказываем пользователю.
        if (e.message?.contains("response code for CONNECT", ignoreCase = true) == true) {
            return strings.get(R.string.error_proxy_connect)
        }
        if (e !is HttpException) {
            val detail = e.message
            return if (detail.isNullOrBlank()) strings.get(R.string.error_unknown)
            else strings.get(R.string.error_unknown_detail, detail)
        }
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        // Cloudflare отклоняет промпт/изображение модерацией — объясняем понятным языком.
        if (body.contains("flagged", ignoreCase = true)) {
            return strings.get(R.string.error_flagged)
        }
        return when {
            e.code() == 401 -> strings.get(R.string.error_invalid_key)
            e.code() == 429 -> strings.get(R.string.error_rate_limited)
            e.code() == 503 -> strings.get(R.string.error_service_unavailable)
            body.isBlank() -> strings.get(R.string.error_http, e.code())
            else -> strings.get(R.string.error_http_detail, e.code(), serverMessage(body) ?: body.take(200))
        }
    }

    private fun serverMessage(body: String): String? = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        val error = root.get("error")
        if (error != null && error.isJsonObject) {
            val message = error.asJsonObject.get("message")
            val text = message?.takeIf { it.isJsonPrimitive }?.asString
            if (!text.isNullOrBlank()) return@runCatching text
        }
        root.get("message")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { it.isNotBlank() }
            ?.let { return@runCatching it }
        // Cloudflare: {"errors":[[code,"text"]]} или {"errors":[{"message":"..."}]}
        val errors = root.get("errors")
        if (errors != null && errors.isJsonArray) {
            val first = errors.asJsonArray.firstOrNull() ?: return@runCatching null
            val text = when {
                first.isJsonArray -> first.asJsonArray.elementAtOrNull(1)?.takeIf { it.isJsonPrimitive }?.asString
                first.isJsonObject -> first.asJsonObject.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                else -> null
            }
            if (!text.isNullOrBlank()) return@runCatching text
        }
        null
    }.getOrNull()

    companion object {
        const val HISTORY_PAGE = 50
        private const val GENERATION_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
        private val HTTP_CODE_REGEX = Regex("HTTP\\s+(\\d{3})")
    }
}