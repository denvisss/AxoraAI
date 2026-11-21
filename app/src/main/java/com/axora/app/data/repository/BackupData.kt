package com.axora.app.data.repository

import com.axora.app.domain.model.PromptTemplate
import com.axora.app.domain.model.ProxyConfig

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val settings: BackupSettings = BackupSettings(),
    val messages: List<BackupMessage> = emptyList(),
    val templates: List<PromptTemplate> = emptyList()
)

// Ключи API в бэкап не попадают — только настройки без секретов.
data class BackupSettings(
    val darkTheme: Boolean? = null,
    val textModel: String = "",
    val language: String = "ru",
    val autoSave: Boolean = false,
    val autoTranslate: Boolean = true,
    val imageFormat: String = "SQUARE",
    val proxy: ProxyConfig? = null
)

data class BackupMessage(
    val role: String,
    val text: String,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val textContent: String? = null,
    val originalPrompt: String? = null,
    val translatedPrompt: String? = null,
    val generationType: String = "IMAGE",
    val timestamp: Long = 0,
    val isError: Boolean = false
)
