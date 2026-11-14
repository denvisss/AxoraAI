package com.axora.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axora.app.BuildConfig
import com.axora.app.data.network.GitHubRelease
import com.axora.app.data.repository.MediaStorage
import com.axora.app.data.repository.SettingsRepository
import com.axora.app.data.repository.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val imageModel: String = SettingsRepository.DEFAULT_IMAGE_MODEL,
    val textModel: String = SettingsRepository.DEFAULT_TEXT_MODEL,
    val autoSave: Boolean = false,
    val autoTranslate: Boolean = true,
    val darkTheme: Boolean? = null,
    val language: String = SettingsRepository.DEFAULT_LANGUAGE,
    val mediaSize: Long = 0L,
    val keyStorageEncrypted: Boolean = true,
    val availableUpdateTag: String = "",
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
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                apiKey = settings.getApiKey(),
                imageModel = settings.getImageModel(),
                textModel = settings.getTextModel(),
                autoSave = settings.isAutoSaveEnabled(),
                autoTranslate = settings.isAutoTranslateEnabled(),
                darkTheme = settings.isDarkThemeEnabled(),
                language = settings.getAppLanguage(),
                keyStorageEncrypted = settings.isKeyStorageEncrypted,
                availableUpdateTag = settings.getAvailableUpdateTag()
                    ?.takeIf { UpdateManager.isNewerVersion(it, BuildConfig.VERSION_NAME) }
                    .orEmpty()
            )
        }
        viewModelScope.launch {
            val size = mediaStorage.generatedMediaSize()
            _uiState.update { it.copy(mediaSize = size) }
        }
    }

    fun setApiKey(key: String) {
        settings.setApiKey(key)
        _uiState.update { it.copy(apiKey = key.trim()) }
    }

    fun setImageModel(model: String) {
        settings.setImageModel(model)
        _uiState.update { it.copy(imageModel = model) }
    }

    fun setTextModel(model: String) {
        settings.setTextModel(model)
        _uiState.update { it.copy(textModel = model) }
    }

    fun setAutoSave(enabled: Boolean) {
        settings.setAutoSaveEnabled(enabled)
        _uiState.update { it.copy(autoSave = enabled) }
    }

    fun setAutoTranslate(enabled: Boolean) {
        settings.setAutoTranslateEnabled(enabled)
        _uiState.update { it.copy(autoTranslate = enabled) }
    }

    fun setDarkTheme(enabled: Boolean) {
        settings.setDarkThemeEnabled(enabled)
        _uiState.update { it.copy(darkTheme = enabled) }
    }

    fun setAppLanguage(language: String) {
        settings.setAppLanguage(language)
        _uiState.update { it.copy(language = language) }
    }

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
}