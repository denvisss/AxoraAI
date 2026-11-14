package com.axora.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.axora.app.domain.model.ImageFormat
import java.util.Locale

class SettingsRepository(private val context: Context) {

    private var securePrefsFailed = false

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            securePrefsFailed = true
            context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private val appPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    }

    val isKeyStorageEncrypted: Boolean
        get() {
            prefs
            return !securePrefsFailed
        }

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()

    fun setApiKey(key: String) {
        prefs.edit { putString(KEY_API_KEY, key.trim()) }
    }

    fun getTextModel(): String =
        prefs.getString(KEY_TEXT_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TEXT_MODEL

    fun setTextModel(model: String) {
        prefs.edit { putString(KEY_TEXT_MODEL, model.trim()) }
    }

    fun getImageModel(): String =
        prefs.getString(KEY_IMAGE_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_IMAGE_MODEL

    fun setImageModel(model: String) {
        prefs.edit { putString(KEY_IMAGE_MODEL, model.trim()) }
    }

    fun isAutoSaveEnabled(): Boolean = appPrefs.getBoolean(KEY_AUTO_SAVE, false)

    fun setAutoSaveEnabled(enabled: Boolean) {
        appPrefs.edit { putBoolean(KEY_AUTO_SAVE, enabled) }
    }

    fun isAutoTranslateEnabled(): Boolean = appPrefs.getBoolean(KEY_AUTO_TRANSLATE, true)

    fun setAutoTranslateEnabled(enabled: Boolean) {
        appPrefs.edit { putBoolean(KEY_AUTO_TRANSLATE, enabled) }
    }

    fun isDarkThemeEnabled(): Boolean? =
        if (appPrefs.contains(KEY_DARK_THEME)) appPrefs.getBoolean(KEY_DARK_THEME, false) else null

    fun setDarkThemeEnabled(enabled: Boolean) {
        appPrefs.edit { putBoolean(KEY_DARK_THEME, enabled) }
    }

    fun getImageFormat(): ImageFormat =
        appPrefs.getString(KEY_IMAGE_FORMAT, null)
            ?.let { runCatching { ImageFormat.valueOf(it) }.getOrNull() }
            ?: ImageFormat.SQUARE

    fun setImageFormat(format: ImageFormat) {
        appPrefs.edit { putString(KEY_IMAGE_FORMAT, format.name) }
    }

    fun getAppLanguage(): String =
        appPrefs.getString(KEY_APP_LANGUAGE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LANGUAGE

    fun setAppLanguage(language: String) {
        appPrefs.edit { putString(KEY_APP_LANGUAGE, language) }
    }

    companion object {
        const val FREE_IMAGE_MODEL = "free"
        const val DEFAULT_IMAGE_MODEL = "black-forest-labs/flux.2-klein-4b"
        const val DEFAULT_TEXT_MODEL = "openai"
        const val LANGUAGE_RU = "ru"
        const val LANGUAGE_EN = "en"
        const val DEFAULT_LANGUAGE = LANGUAGE_RU

        private const val SECURE_PREFS_NAME = "axora_secure_prefs"
        private const val APP_PREFS_NAME = "axora_prefs"
        private const val KEY_API_KEY = "pollinations_api_key"
        private const val KEY_TEXT_MODEL = "text_model"
        private const val KEY_IMAGE_MODEL = "image_model"
        private const val KEY_AUTO_SAVE = "auto_save_gallery"
        private const val KEY_AUTO_TRANSLATE = "auto_translate"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_IMAGE_FORMAT = "image_format"
        private const val KEY_APP_LANGUAGE = "app_language"

        fun wrapLocale(context: Context): Context {
            val language = context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_APP_LANGUAGE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LANGUAGE
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale(language))
            return context.createConfigurationContext(configuration)
        }
    }
}