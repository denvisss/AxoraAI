package com.axora.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import com.axora.app.R
import com.axora.app.data.local.dao.ChatDao
import com.axora.app.data.local.entity.ChatMessageEntity
import com.axora.app.data.network.AudioApiService
import com.axora.app.data.network.ChatMessageRequest
import com.axora.app.data.network.ImageApiService
import com.axora.app.data.network.ImageEditBody
import com.axora.app.data.network.KEYED_TEXT_URL
import com.axora.app.data.network.Message
import com.axora.app.data.network.TextApiService
import com.axora.app.data.network.TranslationClient
import com.axora.app.data.network.VideoApiService
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import com.axora.app.domain.model.Role
import com.axora.app.util.ImageSpec
import com.axora.app.util.SaveImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.random.Random

class DefaultChatRepository(
    private val settings: SettingsRepository,
    private val chatDao: ChatDao,
    private val imageApiService: ImageApiService,
    private val videoApiService: VideoApiService,
    private val audioApiService: AudioApiService,
    private val textApiService: TextApiService,
    private val translationClient: TranslationClient,
    private val context: Context
) : ChatRepository {

    override fun getChatHistory(type: GenerationType): Flow<List<ChatMessage>> =
        chatDao.getChatHistory(type.name).map { entities -> entities.map { it.toDomain() } }

    override suspend fun insertMessage(message: ChatMessage): Long =
        chatDao.insertMessage(message.toEntity())

    override suspend fun updateMessage(messageId: Long, newText: String, newOriginal: String?) {
        chatDao.updateMessage(messageId, newText, newOriginal)
    }

    override suspend fun deleteMessage(messageId: Long) {
        chatDao.getMessageById(messageId)?.let { deleteMediaFiles(it) }
        chatDao.deleteMessage(messageId)
    }

    override suspend fun clearChat(type: GenerationType) {
        chatDao.getAllMessagesOnce(type.name).forEach { deleteMediaFiles(it) }
        chatDao.clearMessages(type.name)
    }

    override suspend fun autoSaveToGallery(message: ChatMessage): Unit = withContext(Dispatchers.IO) {
        if (!settings.isAutoSaveEnabled() || !message.hasMedia()) return@withContext
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) return@withContext
        val path = message.imageUrl ?: message.videoUrl ?: message.audioUrl ?: return@withContext
        val file = File(path).takeIf { it.exists() } ?: return@withContext
        runCatching { SaveImageUtil.saveToGallery(context, file, message.generationType) }
    }

    override suspend fun deleteMediaOf(message: ChatMessage) = withContext(Dispatchers.IO) {
        listOfNotNull(message.imageUrl, message.videoUrl, message.audioUrl).forEach { path ->
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
    }

    override fun getImageFormat(): ImageFormat = settings.getImageFormat()

    override fun setImageFormat(format: ImageFormat) = settings.setImageFormat(format)

    override suspend fun generateContent(prompt: String, type: GenerationType): File =
        withContext(Dispatchers.IO) {
            val authorization = settings.getApiKey().takeIf { it.isNotBlank() }?.let { "Bearer $it" }
            val seed = Random.nextInt(0, Int.MAX_VALUE)

            val responseBody = when (type) {
                GenerationType.IMAGE -> {
                    val model = settings.getImageModel()
                    val keyed = authorization != null && model != SettingsRepository.FREE_IMAGE_MODEL
                    val (width, height) = ImageSpec.generationSize(settings.getImageFormat(), model, keyed)
                    if (keyed) {
                        imageApiService.generateHighResolutionImage(prompt, authorization, seed, model, width, height)
                    } else {
                        imageApiService.generateImage(prompt, seed, width = width, height = height)
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
                throw IOException(context.getString(R.string.error_not_media, contentType, errorText.take(200)))
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
                throw IOException(context.getString(R.string.error_empty_file))
            }
            file
        }

    override suspend fun editImage(source: File, instruction: String): File = withContext(Dispatchers.IO) {
        val key = settings.getApiKey().takeIf { it.isNotBlank() }
            ?: throw IOException(context.getString(R.string.edit_key_required))
        if (!source.exists()) throw IOException(context.getString(R.string.edit_source_missing))

        val mime = when (source.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val model = settings.getImageModel().takeIf { it != SettingsRepository.FREE_IMAGE_MODEL }
            ?: SettingsRepository.DEFAULT_IMAGE_MODEL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val response = imageApiService.editImage(
            "Bearer $key",
            ImageEditBody(
                file = source,
                mime = mime,
                prompt = instruction,
                model = model,
                size = ImageSpec.editSize(bounds.outWidth, bounds.outHeight, model)
            )
        )
        val encoded = response.data?.firstOrNull()?.b64Json
            ?: throw IOException(context.getString(R.string.error_no_edited_image))
        val decoded = Base64.decode(encoded, Base64.DEFAULT)
        val dir = File(context.filesDir, "generated").apply { mkdirs() }
        val file = File(dir, "image_${System.currentTimeMillis()}.${ImageSpec.imageExtension(decoded)}")
        try {
            file.writeBytes(decoded)
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        if (file.length() == 0L) {
            file.delete()
            throw IOException(context.getString(R.string.error_empty_file))
        }
        file
    }

    override suspend fun generateText(prompt: String, history: List<ChatMessage>): String {
        val apiKey = settings.getApiKey().takeIf { it.isNotBlank() }
        val model = settings.getTextModel()
        if (apiKey == null && model != SettingsRepository.DEFAULT_TEXT_MODEL) {
            throw IOException(context.getString(R.string.text_key_required))
        }
        val messages = buildList {
            add(Message(role = "system", content = TEXT_SYSTEM_PROMPT))
            history.takeLast(TEXT_CONTEXT_MESSAGES).forEach { message ->
                val content = message.textContent ?: message.text
                if (content.isNotBlank()) {
                    add(Message(role = if (message.isUser()) "user" else "assistant", content = content))
                }
            }
            add(Message(content = prompt))
        }
        val request = ChatMessageRequest(
            messages = messages,
            model = model,
            max_tokens = TEXT_MAX_TOKENS
        )
        val response = if (apiKey != null) {
            textApiService.generateTextKeyed(KEYED_TEXT_URL, request, "Bearer $apiKey")
        } else {
            textApiService.generateText(request)
        }
        return response.choices?.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException(context.getString(R.string.text_empty))
    }

    override suspend fun translateToEnglish(text: String): String =
        if (!settings.isAutoTranslateEnabled()) text
        else translationClient.translateToEnglish(text, settings.getApiKey().takeIf { it.isNotBlank() })

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
        timestamp = timestamp,
        isError = isError
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
        timestamp = timestamp,
        isError = isError
    )

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
        private const val TEXT_CONTEXT_MESSAGES = 10
        private const val TEXT_MAX_TOKENS = 1000
        private const val TEXT_SYSTEM_PROMPT =
            "You are a helpful assistant. Answer in the language the user writes in. Be concise and accurate."
    }
}