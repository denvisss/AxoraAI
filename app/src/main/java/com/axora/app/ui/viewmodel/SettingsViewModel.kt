package com.axora.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.axora.app.BuildConfig
import com.axora.app.data.local.dao.ChatDao
import com.axora.app.data.local.entity.ChatMessageEntity
import com.axora.app.data.network.GitHubRelease
import com.axora.app.data.network.ProxyEntry
import com.axora.app.data.repository.BackupData
import com.axora.app.data.repository.BackupMessage
import com.axora.app.data.repository.BackupSettings
import com.axora.app.data.repository.MediaStorage
import com.axora.app.data.repository.SettingsRepository
import com.axora.app.data.repository.TemplateStore
import com.axora.app.data.repository.UpdateManager
import com.axora.app.di.NetworkModule
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationStats
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import com.axora.app.domain.model.ProxyConfig
import com.axora.app.util.ChatExport
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SettingsUiState(
    val groqApiKey: String = "",
    val cfAccountId: String = "",
    val cfApiToken: String = "",
    val autoSave: Boolean = false,
    val autoTranslate: Boolean = true,
    val darkTheme: Boolean? = null,
    val textModel: String = SettingsRepository.DEFAULT_TEXT_MODEL,
    val language: String = SettingsRepository.DEFAULT_LANGUAGE,
    val mediaSize: Long = 0L,
    val keyStorageEncrypted: Boolean = true,
    val availableUpdateTag: String = "",
    val proxy: ProxyConfig? = null,
    val stats: GenerationStats = GenerationStats(),
    val update: UpdateUiState = UpdateUiState()
)

enum class UpdateStatus { Idle, Checking, UpToDate, Available, Downloading, Ready, Error }

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val version: String = "",
    val progress: Int = 0,
    val release: GitHubRelease? = null,
    val filePath: String? = null,
    val error: String? = null
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val mediaStorage: MediaStorage,
    private val updateManager: UpdateManager,
    private val chatDao: ChatDao,
    private val templateStore: TemplateStore
) : ViewModel() {

    private val gson = Gson()

    private val _pendingImport = MutableStateFlow<BackupData?>(null)
    val pendingImport: StateFlow<BackupData?> = _pendingImport.asStateFlow()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                groqApiKey = settings.getGroqApiKey(),
                cfAccountId = settings.getCfAccountId(),
                cfApiToken = settings.getCfApiToken(),
                autoSave = settings.isAutoSaveEnabled(),
                autoTranslate = settings.isAutoTranslateEnabled(),
                darkTheme = settings.isDarkThemeEnabled(),
                textModel = settings.getTextModel(),
                language = settings.getAppLanguage(),
                keyStorageEncrypted = settings.isKeyStorageEncrypted,
                availableUpdateTag = settings.getAvailableUpdateTag()
                    ?.takeIf { UpdateManager.isNewerVersion(it, BuildConfig.VERSION_NAME) }
                    .orEmpty(),
                proxy = settings.getProxy()
            )
        }
        viewModelScope.launch {
            val size = mediaStorage.generatedMediaSize()
            _uiState.update { it.copy(mediaSize = size) }
        }
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) { loadStats() }
            _uiState.update { it.copy(stats = stats) }
        }
    }

    private suspend fun loadStats(): GenerationStats {
        val dayStart = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val weekStart = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return GenerationStats(
            imagesToday = chatDao.countAssistantMessages(GenerationType.IMAGE.name, dayStart),
            imagesWeek = chatDao.countAssistantMessages(GenerationType.IMAGE.name, weekStart),
            imagesTotal = chatDao.countAssistantMessagesTotal(GenerationType.IMAGE.name),
            textsToday = chatDao.countAssistantMessages(GenerationType.TEXT.name, dayStart),
            textsWeek = chatDao.countAssistantMessages(GenerationType.TEXT.name, weekStart),
            textsTotal = chatDao.countAssistantMessagesTotal(GenerationType.TEXT.name)
        )
    }

    private fun BackupMessage.toEntity() = ChatMessageEntity(
        role = role,
        text = text,
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        audioUrl = audioUrl,
        textContent = textContent,
        originalPrompt = originalPrompt,
        translatedPrompt = translatedPrompt,
        generationType = generationType,
        timestamp = timestamp,
        isError = isError
    )

    fun setGroqApiKey(key: String) {
        settings.setGroqApiKey(key)
        _uiState.update { it.copy(groqApiKey = key.trim()) }
    }

    fun setCfAccountId(id: String) {
        settings.setCfAccountId(id)
        _uiState.update { it.copy(cfAccountId = id.trim()) }
    }

    fun setCfApiToken(token: String) {
        settings.setCfApiToken(token)
        _uiState.update { it.copy(cfApiToken = token.trim()) }
    }

    fun setAutoSave(enabled: Boolean) {
        settings.setAutoSaveEnabled(enabled)
        _uiState.update { it.copy(autoSave = enabled) }
    }

    fun setAutoTranslate(enabled: Boolean) {
        settings.setAutoTranslateEnabled(enabled)
        _uiState.update { it.copy(autoTranslate = enabled) }
    }

    fun setDarkTheme(enabled: Boolean?) {
        settings.setDarkThemeEnabled(enabled)
        _uiState.update { it.copy(darkTheme = enabled) }
    }

    fun setTextModel(model: String) {
        settings.setTextModel(model)
        _uiState.update { it.copy(textModel = model) }
    }

    fun setAppLanguage(language: String) {
        settings.setAppLanguage(language)
        _uiState.update { it.copy(language = language) }
    }

    fun setProxy(config: ProxyConfig?) {
        settings.setProxy(config)
        NetworkModule.proxyConfig.value = config
        _uiState.update { it.copy(proxy = config) }
    }

    suspend fun loadProxyList(protocol: String): List<ProxyEntry> =
        NetworkModule.proxyListClient.load(protocol)

    suspend fun testProxy(config: ProxyConfig, timeoutMs: Long = 10_000L): Result<Long> =
        runCatching { NetworkModule.testProxy(config, timeoutMs) }

    fun checkForUpdate() {
        val status = _uiState.value.update.status
        if (status == UpdateStatus.Checking || status == UpdateStatus.Downloading) return
        _uiState.update { it.copy(update = UpdateUiState(status = UpdateStatus.Checking)) }
        viewModelScope.launch {
            val result = runCatching { updateManager.findUpdate(BuildConfig.VERSION_NAME) }
            result.onSuccess { settings.setAvailableUpdateTag(it?.tagName) }
            _uiState.update { state ->
                state.copy(
                    availableUpdateTag = if (result.isSuccess) {
                        result.getOrNull()?.tagName.orEmpty()
                    } else {
                        state.availableUpdateTag
                    },
                    update = result.fold(
                        onSuccess = { release ->
                            if (release == null) {
                                UpdateUiState(status = UpdateStatus.UpToDate)
                            } else {
                                UpdateUiState(
                                    status = UpdateStatus.Available,
                                    version = release.tagName,
                                    release = release
                                )
                            }
                        },
                        onFailure = { e ->
                            UpdateUiState(status = UpdateStatus.Error, error = e.message)
                        }
                    )
                )
            }
        }
    }

    fun downloadUpdate() {
        val current = _uiState.value.update
        val release = current.release ?: return
        if (current.status == UpdateStatus.Downloading) return
        _uiState.update { it.copy(update = it.update.copy(status = UpdateStatus.Downloading, progress = 0)) }
        viewModelScope.launch {
            val result = runCatching {
                updateManager.download(release) { progress ->
                    _uiState.update { it.copy(update = it.update.copy(progress = progress)) }
                }
            }
            _uiState.update { state ->
                state.copy(
                    update = result.fold(
                        onSuccess = { file ->
                            state.update.copy(
                                status = UpdateStatus.Ready,
                                filePath = file.absolutePath,
                                progress = 100
                            )
                        },
                        onFailure = { e ->
                            state.update.copy(status = UpdateStatus.Error, error = e.message)
                        }
                    )
                )
            }
        }
    }

    fun clearMedia() {
        viewModelScope.launch {
            mediaStorage.clearGeneratedMedia()
            _uiState.update { it.copy(mediaSize = mediaStorage.generatedMediaSize()) }
        }
    }

    // ===== Резервная копия (без API-ключей и медиафайлов) =====

    fun shareChatExport(type: GenerationType, context: Context, onEmpty: () -> Unit) {
        viewModelScope.launch {
            val messages = withContext(Dispatchers.IO) {
                chatDao.getAllMessagesOnce(type.name).map { entity ->
                    ChatMessage(
                        role = if (entity.role == "user") com.axora.app.domain.model.Role.USER else com.axora.app.domain.model.Role.ASSISTANT,
                        text = entity.text,
                        imageUrl = entity.imageUrl,
                        videoUrl = entity.videoUrl,
                        audioUrl = entity.audioUrl,
                        textContent = entity.textContent,
                        originalPrompt = entity.originalPrompt,
                        translatedPrompt = entity.translatedPrompt,
                        generationType = GenerationType.valueOf(entity.generationType),
                        timestamp = entity.timestamp,
                        isError = entity.isError
                    )
                }
            }
            val body = ChatExport.buildBody(context, messages)
            if (body == null) onEmpty() else ChatExport.share(context, body)
        }
    }

    fun exportBackup(uri: Uri, context: Context, onError: (String) -> Unit, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = gson.toJson(buildBackup())
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Cannot open output stream")
                }
            }
            if (result.isSuccess) onSuccess()
            else onError(result.exceptionOrNull()?.message.orEmpty())
        }
    }

    fun readBackupForImport(uri: Uri, context: Context, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IOException("Cannot open input stream")
                    gson.fromJson(json, BackupData::class.java)
                        ?: throw IOException("Empty backup")
                }
            }
            result.onSuccess { backup ->
                if (backup.version > 1) {
                    onError("Unsupported version ${backup.version}")
                } else {
                    _pendingImport.value = backup
                }
            }.onFailure { onError(it.message.orEmpty()) }
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun confirmImport(onDone: () -> Unit, onError: (String) -> Unit) {
        val backup = _pendingImport.value ?: return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    chatDao.deleteAllMessages()
                    backup.messages.forEach { entity ->
                        chatDao.insertMessage(entity.toEntity())
                    }
                    templateStore.save(backup.templates)
                    val s = backup.settings
                    settings.setDarkThemeEnabled(s.darkTheme)
                    settings.setTextModel(s.textModel)
                    settings.setAppLanguage(s.language)
                    settings.setAutoSaveEnabled(s.autoSave)
                    settings.setAutoTranslateEnabled(s.autoTranslate)
                    ImageFormat.entries.firstOrNull { it.name == s.imageFormat }
                        ?.let { settings.setImageFormat(it) }
                    settings.setProxy(s.proxy)
                    NetworkModule.proxyConfig.value = s.proxy
                }
            }
            if (result.isSuccess) {
                _pendingImport.value = null
                refresh()
                onDone()
            } else {
                onError(result.exceptionOrNull()?.message.orEmpty())
            }
        }
    }

    private suspend fun buildBackup(): BackupData = withContext(Dispatchers.IO) {
        val messages = GenerationType.entries.flatMap { type ->
            chatDao.getAllMessagesOnce(type.name)
        }
        BackupData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            settings = BackupSettings(
                darkTheme = settings.isDarkThemeEnabled(),
                textModel = settings.getTextModel(),
                language = settings.getAppLanguage(),
                autoSave = settings.isAutoSaveEnabled(),
                autoTranslate = settings.isAutoTranslateEnabled(),
                imageFormat = settings.getImageFormat().name,
                proxy = settings.getProxy()
            ),
            messages = messages.map { entity ->
                BackupMessage(
                    role = entity.role,
                    text = entity.text,
                    imageUrl = entity.imageUrl,
                    videoUrl = entity.videoUrl,
                    audioUrl = entity.audioUrl,
                    textContent = entity.textContent,
                    originalPrompt = entity.originalPrompt,
                    translatedPrompt = entity.translatedPrompt,
                    generationType = entity.generationType,
                    timestamp = entity.timestamp,
                    isError = entity.isError
                )
            },
            templates = templateStore.load()
        )
    }
}