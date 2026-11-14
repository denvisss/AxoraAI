package com.axora.app.data.repository

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.axora.app.data.local.dao.ChatDao
import com.axora.app.data.local.entity.ChatMessageEntity
import com.axora.app.data.network.AudioApiService
import com.axora.app.data.network.ChatMessageRequest
import com.axora.app.data.network.ImageApiService
import com.axora.app.data.network.ImageEditRequest
import com.axora.app.data.network.ImageInput
import com.axora.app.data.network.KEYED_TEXT_URL
import com.axora.app.data.network.Message
import com.axora.app.data.network.TextApiService
import com.axora.app.data.network.TranslationClient
import com.axora.app.data.network.VideoApiService
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.Role
import com.axora.app.util.SaveImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.random.Random

private const val SECURE_PREFS_NAME = "axora_secure_prefs"
private const val KEY_API_KEY = "pollinations_api_key"
private const val KEY_TEXT_MODEL = "text_model"
private const val KEY_IMAGE_MODEL = "image_model"
private const val DEFAULT_IMAGE_MODEL = "black-forest-labs/flux.2-klein-4b"

private const val APP_PREFS_NAME = "axora_prefs"
private const val KEY_AUTO_SAVE = "auto_save_gallery"
private const val KEY_AUTO_TRANSLATE = "auto_translate"

class ChatRepository(
    private val chatDao: ChatDao,
    private val imageApiService: ImageApiService,
    private val videoApiService: VideoApiService,
    private val audioApiService: AudioApiService,
    private val textApiService: TextApiService,
    private val translationClient: TranslationClient,
    private val context: Context
) {

    private val prefs: SharedPreferences by lazy { createSecurePrefs(context) }

    private val appPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getTextModel(): String =
        prefs.getString(KEY_TEXT_MODEL, null)?.takeIf { it.isNotBlank() } ?: "openai"

    fun setTextModel(model: String) {
        prefs.edit().putString(KEY_TEXT_MODEL, model.trim()).apply()
    }

    fun getImageModel(): String =
        prefs.getString(KEY_IMAGE_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_IMAGE_MODEL

    fun setImageModel(model: String) {
        prefs.edit().putString(KEY_IMAGE_MODEL, model.trim()).apply()
    }

    fun isAutoSaveEnabled(): Boolean = appPrefs.getBoolean(KEY_AUTO_SAVE, false)

    fun setAutoSaveEnabled(enabled: Boolean) {
        appPrefs.edit().putBoolean(KEY_AUTO_SAVE, enabled).apply()
    }

    fun isAutoTranslateEnabled(): Boolean = appPrefs.getBoolean(KEY_AUTO_TRANSLATE, true)

    fun setAutoTranslateEnabled(enabled: Boolean) {
        appPrefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply()
    }

    fun autoSaveToGallery(message: ChatMessage) {
        if (!isAutoSaveEnabled() || !message.hasMedia()) return
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val path = message.imageUrl ?: message.videoUrl ?: message.audioUrl ?: return
        val file = File(path).takeIf { it.exists() } ?: return
        SaveImageUtil.saveToGallery(context, file, message.generationType)
    }

    fun generatedMediaSize(): Long =
        File(context.filesDir, "generated").listFiles()?.sumOf { it.length() } ?: 0L

    suspend fun clearGeneratedMedia() = withContext(Dispatchers.IO) {
        File(context.filesDir, "generated").listFiles()?.forEach { runCatching { it.delete() } }
        chatDao.clearMediaPaths()
    }

    fun getChatHistory(type: GenerationType): Flow<List<ChatMessage>> =
        chatDao.getChatHistory(type.name).map { entities -> entities.map { it.toDomain() } }

    suspend fun insertMessage(message: ChatMessage): Long =
        chatDao.insertMessage(message.toEntity())

    suspend fun updateMessage(messageId: Long, newText: String, newOriginal: String?) {
        chatDao.updateMessage(messageId, newText, newOriginal)
    }

    suspend fun deleteMessage(messageId: Long) {
        chatDao.getMessageById(messageId)?.let { deleteMediaFiles(it) }
        chatDao.deleteMessage(messageId)
    }

    suspend fun clearChat(type: GenerationType) {
        chatDao.getAllMessagesOnce(type.name).forEach { deleteMediaFiles(it) }
        chatDao.clearMessages(type.name)
    }

    suspend fun generateContent(prompt: String, type: GenerationType): File =
        withContext(Dispatchers.IO) {
            val authorization = getApiKey().takeIf { it.isNotBlank() }?.let { "Bearer $it" }
            val seed = Random.nextInt(0, Int.MAX_VALUE)

            val responseBody = when (type) {
                GenerationType.IMAGE -> {
                    val model = getImageModel()
                    if (authorization != null && model != FREE_IMAGE_MODEL) {
                        val size = if (model.contains("kontext")) 1024 else 1536
                        imageApiService.generateHighResolutionImage(prompt, authorization, seed, model, size, size)
                    } else {
                        imageApiService.generateImage(prompt, seed)
                    }
                }
                GenerationType.VIDEO -> videoApiService.generateVideo(prompt, authorization)
                GenerationType.AUDIO -> audioApiService.generateAudio(prompt, authorization)
                GenerationType.TEXT -> throw IllegalArgumentException("TEXT генерируется через generateText")
            }

            val contentType = responseBody.contentType()?.toString().orEmpty()
            val expectedPrefix = type.name.lowercase()
            if (contentType.isNotEmpty() && !contentType.startsWith(expectedPrefix)) {
                val errorText = runCatching { responseBody.string() }.getOrDefault("")
                throw IOException("Сервер вернул не медиа-ответ ($contentType): ${errorText.take(200)}")
            }

            val dir = File(context.filesDir, "generated").apply { mkdirs() }
            val file = File(dir, "${expectedPrefix}_${System.currentTimeMillis()}.${type.extension(contentType)}")
            try {
                responseBody.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
            if (file.length() == 0L) {
                file.delete()
                throw IOException("Сервер вернул пустой файл")
            }
            file
        }

    suspend fun editImage(source: File, instruction: String): File = withContext(Dispatchers.IO) {
        val key = getApiKey().takeIf { it.isNotBlank() }
            ?: throw IOException("Для редактирования нужен API-ключ Pollinations. Добавьте его в настройках.")
        if (!source.exists()) throw IOException("Исходное изображение недоступно")

        val bytes = source.readBytes()
        val mime = when (source.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val dataUri = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        val model = getImageModel().takeIf { it != FREE_IMAGE_MODEL } ?: DEFAULT_IMAGE_MODEL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val response = imageApiService.editImage(
            "Bearer $key",
            ImageEditRequest(
                prompt = instruction,
                image = listOf(ImageInput(dataUri)),
                model = model,
                size = editSize(bounds.outWidth, bounds.outHeight, model)
            )
        )
        val encoded = response.data?.firstOrNull()?.b64Json
            ?: throw IOException("Сервер не вернул изменённое изображение")
        val decoded = Base64.decode(encoded, Base64.DEFAULT)
        val dir = File(context.filesDir, "generated").apply { mkdirs() }
        val file = File(dir, "image_${System.currentTimeMillis()}.${imageExtension(decoded)}")
        try {
            file.writeBytes(decoded)
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        if (file.length() == 0L) {
            file.delete()
            throw IOException("Сервер вернул пустой файл")
        }
        file
    }

    suspend fun generateText(prompt: String): String {
        val apiKey = getApiKey().takeIf { it.isNotBlank() }
        val model = getTextModel()
        if (apiKey == null && model != "openai") {
            throw IOException("Для выбранной текстовой модели нужен API-ключ Pollinations. Добавьте его в настройках.")
        }
        val request = ChatMessageRequest(
            messages = listOf(Message(content = prompt)),
            model = model
        )
        val response = if (apiKey != null) {
            textApiService.generateTextKeyed(KEYED_TEXT_URL, request, "Bearer $apiKey")
        } else {
            textApiService.generateText(request)
        }
        return response.choices?.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException("модель вернула пустой ответ")
    }

    suspend fun translateToEnglish(text: String): String =
        if (!isAutoTranslateEnabled()) text
        else translationClient.translateToEnglish(text, getApiKey().takeIf { it.isNotBlank() })

    private fun deleteMediaFiles(entity: ChatMessageEntity) {
        listOfNotNull(entity.imageUrl, entity.videoUrl, entity.audioUrl).forEach { path ->
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
    }

    private fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        role = if (role == "user") Role.USER else Role.ASSISTANT,
        text = text,
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        audioUrl = audioUrl,
        textContent = textContent,
        originalPrompt = originalPrompt,
        translatedPrompt = translatedPrompt,
        generationType = GenerationType.valueOf(generationType),
        timestamp = timestamp
    )

    private fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = id,
        role = role.name.lowercase(),
        text = text,
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        audioUrl = audioUrl,
        textContent = textContent,
        originalPrompt = originalPrompt,
        translatedPrompt = translatedPrompt,
        generationType = generationType.name,
        timestamp = timestamp
    )

    private fun editSize(width: Int, height: Int, model: String): String {
        if (width <= 0 || height <= 0) return "auto"
        val maxSide = if (model.contains("kontext")) 1024 else 1536
        val scale = minOf(1f, maxSide.toFloat() / maxOf(width, height))
        val w = ((width * scale).toInt() / 32 * 32).coerceAtLeast(32)
        val h = ((height * scale).toInt() / 32 * 32).coerceAtLeast(32)
        return "${w}x$h"
    }

    private fun imageExtension(bytes: ByteArray): String = when {
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size > 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "webp"
        else -> "jpg"
    }

    private fun GenerationType.extension(contentType: String): String = when (this) {
        GenerationType.IMAGE -> when (contentType.substringBefore(';')) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        GenerationType.VIDEO -> "mp4"
        GenerationType.AUDIO -> "mp3"
        GenerationType.TEXT -> "txt"
    }

    companion object {
        const val FREE_IMAGE_MODEL = "free"

        private fun createSecurePrefs(context: Context): SharedPreferences = try {
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
            context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
}
