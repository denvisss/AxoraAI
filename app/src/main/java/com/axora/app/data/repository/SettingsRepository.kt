package com.axora.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.axora.app.domain.model.ImageFormat
import com.axora.app.domain.model.ProxyConfig
import java.util.Locale

class SettingsRepository(private val context: Context) {

    private var securePrefsFailed = false

    private val prefs: SharedPreferences by lazy {
        val created = try {
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
        created.edit { remove(KEY_LEGACY_API_KEY) }
        created
    }

    private val appPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE).also {
            it.edit {
                remove(KEY_LEGACY_TEXT_MODEL)
                remove(KEY_LEGACY_IMAGE_MODEL)
            }
        }
    }

    val isKeyStorageEncrypted: Boolean
        get() {
            prefs
            return !securePrefsFailed
        }

    fun getGroqApiKey(): String = prefs.getString(KEY_GROQ_API_KEY, null)?.trim().orEmpty()

    fun setGroqApiKey(key: String) {
        prefs.edit { putString(KEY_GROQ_API_KEY, key.trim()) }
    }

    fun getCfAccountId(): String = appPrefs.getString(KEY_CF_ACCOUNT_ID, null)?.trim().orEmpty()

    fun setCfAccountId(id: String) {
        appPrefs.edit { putString(KEY_CF_ACCOUNT_ID, id.trim()) }
    }

    fun getCfApiToken(): String = prefs.getString(KEY_CF_API_TOKEN, null)?.trim().orEmpty()

    fun setCfApiToken(token: String) {
        prefs.edit { putString(KEY_CF_API_TOKEN, token.trim()) }
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

    fun setDarkThemeEnabled(enabled: Boolean?) {
        appPrefs.edit {
            if (enabled == null) remove(KEY_DARK_THEME) else putBoolean(KEY_DARK_THEME, enabled)
        }
    }

    fun getTextModel(): String =
        appPrefs.getString(KEY_TEXT_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TEXT_MODEL

    fun setTextModel(model: String) {
        appPrefs.edit { putString(KEY_TEXT_MODEL, model) }
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

    fun getLastUpdateCheck(): Long = appPrefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)

    fun setLastUpdateCheck(timestamp: Long) {
        appPrefs.edit { putLong(KEY_LAST_UPDATE_CHECK, timestamp) }
    }

    fun getAvailableUpdateTag(): String? =
        appPrefs.getString(KEY_AVAILABLE_UPDATE_TAG, null)?.takeIf { it.isNotBlank() }

    fun setAvailableUpdateTag(tag: String?) {
        appPrefs.edit { putString(KEY_AVAILABLE_UPDATE_TAG, tag) }
    }

    fun getLastWhatsNewVersionCode(): Int = appPrefs.getInt(KEY_WHATS_NEW_SHOWN, 0)

    fun setLastWhatsNewVersionCode(code: Int) {
        appPrefs.edit { putInt(KEY_WHATS_NEW_SHOWN, code) }
    }

    fun getProxy(): ProxyConfig? {
        val scheme = appPrefs.getString(KEY_PROXY_SCHEME, null)?.takeIf { it.isNotBlank() } ?: return null
        val host = appPrefs.getString(KEY_PROXY_HOST, null)?.takeIf { it.isNotBlank() } ?: return null
        val port = appPrefs.getInt(KEY_PROXY_PORT, -1)
        if (port !in 1..65535) return null
        return ProxyConfig(
            enabled = appPrefs.getBoolean(KEY_PROXY_ENABLED, false),
            scheme = scheme,
            host = host,
            port = port
        )
    }

    fun setProxy(config: ProxyConfig?) {
        appPrefs.edit {
            if (config == null) {
                remove(KEY_PROXY_ENABLED)
                remove(KEY_PROXY_SCHEME)
                remove(KEY_PROXY_HOST)
                remove(KEY_PROXY_PORT)
            } else {
                putBoolean(KEY_PROXY_ENABLED, config.enabled)
                putString(KEY_PROXY_SCHEME, config.scheme)
                putString(KEY_PROXY_HOST, config.host)
                putInt(KEY_PROXY_PORT, config.port)
            }
        }
    }

    companion object {
        const val LANGUAGE_RU = "ru"
        const val LANGUAGE_EN = "en"
        const val DEFAULT_LANGUAGE = LANGUAGE_RU

        private const val SECURE_PREFS_NAME = "axora_secure_prefs"
        private const val APP_PREFS_NAME = "axora_prefs"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_CF_ACCOUNT_ID = "cf_account_id"
        private const val KEY_CF_API_TOKEN = "cf_api_token"
        private const val KEY_LEGACY_API_KEY = "pollinations_api_key"
        private const val KEY_LEGACY_TEXT_MODEL = "text_model"
        private const val KEY_LEGACY_IMAGE_MODEL = "image_model"
        private const val KEY_AUTO_SAVE = "auto_save_gallery"
        private const val KEY_AUTO_TRANSLATE = "auto_translate"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_IMAGE_FORMAT = "image_format"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_AVAILABLE_UPDATE_TAG = "available_update_tag"
        private const val KEY_WHATS_NEW_SHOWN = "whats_new_shown_code"
        private const val KEY_PROXY_ENABLED = "proxy_enabled"
        private const val KEY_PROXY_SCHEME = "proxy_scheme"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_TEXT_MODEL = "groq_text_model"

        const val TEXT_MODEL_GPT_120B = "openai/gpt-oss-120b"
        const val TEXT_MODEL_GPT_20B = "openai/gpt-oss-20b"
        const val TEXT_MODEL_LLAMA_70B = "llama-3.3-70b-versatile"
        const val DEFAULT_TEXT_MODEL = TEXT_MODEL_GPT_120B

        fun wrapLocale(context: Context): Context {
            val language = context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_APP_LANGUAGE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LANGUAGE
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale(language))
            return context.createConfigurationContext(configuration)
        }
    }
}