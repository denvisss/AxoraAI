package com.axora.app.data.repository

import com.axora.app.domain.model.PromptTemplate
import com.axora.app.domain.model.ProxyConfig
import com.google.gson.annotations.SerializedName

// @SerializedName: имена полей в JSON стабильны независимо от R8-обфускации.
data class BackupData(
    @SerializedName("version") val version: Int = 2,
    @SerializedName("format") val format: String = BACKUP_FORMAT,
    @SerializedName("exportedAt") val exportedAt: Long = 0,
    @SerializedName("settings") val settings: BackupSettings = BackupSettings(),
    @SerializedName("messages") val messages: List<BackupMessage> = emptyList(),
    @SerializedName("templates") val templates: List<PromptTemplate> = emptyList()
) {
    companion object {
        const val BACKUP_FORMAT = "axora-backup"
        const val CURRENT_VERSION = 2
    }
}

// Ключи API в бэкап не попадают — только настройки без секретов.
data class BackupSettings(
    @SerializedName("darkTheme") val darkTheme: Boolean? = null,
    @SerializedName("textModel") val textModel: String = "",
    @SerializedName("language") val language: String = "ru",
    @SerializedName("autoSave") val autoSave: Boolean = false,
    @SerializedName("autoTranslate") val autoTranslate: Boolean = true,
    @SerializedName("imageFormat") val imageFormat: String = "WIDE",
    @SerializedName("imageModel") val imageModel: String = "",
    @SerializedName("proxy") val proxy: ProxyConfig? = null
)

data class BackupMessage(
    @SerializedName("role") val role: String,
    @SerializedName("text") val text: String,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("videoUrl") val videoUrl: String? = null,
    @SerializedName("audioUrl") val audioUrl: String? = null,
    @SerializedName("textContent") val textContent: String? = null,
    @SerializedName("originalPrompt") val originalPrompt: String? = null,
    @SerializedName("translatedPrompt") val translatedPrompt: String? = null,
    @SerializedName("generationType") val generationType: String = "IMAGE",
    @SerializedName("timestamp") val timestamp: Long = 0,
    @SerializedName("isError") val isError: Boolean = false
)
