package com.axora.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axora.app.data.repository.MediaStorage
import com.axora.app.data.repository.SettingsRepository
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
    val mediaSize: Long = 0L,
    val keyStorageEncrypted: Boolean = true
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val mediaStorage: MediaStorage
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
                keyStorageEncrypted = settings.isKeyStorageEncrypted
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

    fun clearMedia() {
        viewModelScope.launch {
            mediaStorage.clearGeneratedMedia()
            _uiState.update { it.copy(mediaSize = mediaStorage.generatedMediaSize()) }
        }
    }
}