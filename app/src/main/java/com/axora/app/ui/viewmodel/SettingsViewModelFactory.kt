package com.axora.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.axora.app.data.repository.MediaStorage
import com.axora.app.data.repository.SettingsRepository

class SettingsViewModelFactory(
    private val settings: SettingsRepository,
    private val mediaStorage: MediaStorage
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settings, mediaStorage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}