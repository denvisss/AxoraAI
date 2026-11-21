package com.axora.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import com.axora.app.R
import com.axora.app.data.local.dao.ChatDao
import com.axora.app.data.local.entity.ChatMessageEntity
import com.axora.app.data.network.ChatMessageRequest
import com.axora.app.data.network.CloudflareApiService
import com.axora.app.data.network.Message
import com.axora.app.data.network.TextApiService
import com.axora.app.data.network.TranslationClient
import com.axora.app.di.NetworkModule
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import com.axora.app.domain.model.Role
import com.axora.app.util.ImageSpec
import com.axora.app.util.SaveImageUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.random.Random

class DefaultChatRepository(
    private val settings: SettingsRepository,
    private val chatDao: ChatDao,
    private val cloudflareApiService: CloudflareApiService,
    private val textApiService: TextApiService,
    private val translationClient: TranslationClient,
    private val context: Context
) : ChatRepository {

    private val stringsContext: Context
        get() = SettingsRepository.wrapLocale(context)

    private val gson = Gson()

    override fun getChatHistory(type: GenerationType, limit: Int): Flow<List<ChatMessage>> =
        chatDao.getChatHistoryDesc(type.name, limit).map { entities -> entities.map { it.toDomain() }.reversed() }

    override suspend fun searchHistory(query: String): List<ChatMessage> =
        chatDao.searchMessages(query).map { it.toDomain() }

    override fun getAllImages(): Flow<List<ChatMessage>> =
        chatDao.getAllImageMessages().map { entities -> entities.map { it.toDomain() } }

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

    override suspend fun generateContent(prompt: String, type: GenerationType): File = when (type) {
        GenerationType.IMAGE -> generateImage(prompt)
        GenerationType.TEXT -> throw IllegalArgumentException("TEXT генерируется через generateText")
        GenerationType.VIDEO, GenerationType.AUDIO ->
            throw IllegalArgumentException("Неподдерживаемый тип генерации: $type")
    }

    override suspend fun editImage(source: File, instruction: String): File = withContext(Dispatchers.IO) {
        val (account, token) = requireCloudflareCredentials()
        if (!source.exists()) throw IOException(stringsContext.getString(R.string.edit_source_missing))

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val (width, height) = ImageSpec.editOutputSize(bounds.outWidth, bounds.outHeight)

        val input = downscaleForEdit(source, bounds)
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("prompt", instruction)
                .addFormDataPart(
                    "input_image_0",
                    input.name,
                    input.asRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart("width", width.toString())
                .addFormDataPart("height", height.toString())
                .addFormDataPart("seed", Random.nextInt(0, Int.MAX_VALUE).toString())
                .build()
            val response = cloudflareApiService.runKlein(kleinUrl(account), "Bearer $token", body)
            val encoded = response.result?.image?.takeIf { it.isNotBlank() }
                ?: throw IOException(stringsContext.getString(R.string.error_no_image))
            saveDecodedImage(Base64.decode(encoded, Base64.DEFAULT))
        } finally {
            if (input != source) input.delete()
        }
    }

    override suspend fun generateText(prompt: String, history: List<ChatMessage>): String {
        val apiKey = settings.getGroqApiKey().takeIf { it.isNotBlank() }
            ?: throw IOException(stringsContext.getString(R.string.groq_key_required))
        val request = ChatMessageRequest(
            model = settings.getTextModel(),
            messages = buildTextMessages(prompt, history),
            temperature = 0.7,
            max_tokens = TEXT_MAX_TOKENS
        )
        val response = textApiService.chatCompletions(request, "Bearer $apiKey")
        return response.choices?.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException(stringsContext.getString(R.string.text_empty))
    }

    override suspend fun generateTextStreamed(
        prompt: String,
        history: List<ChatMessage>,
        onDelta: (String) -> Unit
    ): String {
        val apiKey = settings.getGroqApiKey().takeIf { it.isNotBlank() }
            ?: throw IOException(stringsContext.getString(R.string.groq_key_required))
        val body = gson.toJson(
            ChatMessageRequest(
                model = settings.getTextModel(),
                messages = buildTextMessages(prompt, history),
                temperature = 0.7,
                max_tokens = TEXT_MAX_TOKENS,
                stream = true
            )
        )
        val full = NetworkModule.streamGroqChat(body, apiKey, onDelta).trim()
        if (full.isEmpty()) throw IOException(stringsContext.getString(R.string.text_empty))
        return full
    }

    private fun buildTextMessages(prompt: String, history: List<ChatMessage>): List<Message> =
        buildList {
            add(Message(role = "system", content = TEXT_SYSTEM_PROMPT))
            // При «Повторить»/регенерации исходный запрос уже в истории — не дублируем его в конце.
            val context = history.dropLastWhile { it.isUser() && (it.textContent ?: it.text) == prompt }
            context.takeLast(TEXT_CONTEXT_MESSAGES).forEach { message ->
                val content = message.textContent ?: message.text
                if (content.isNotBlank()) {
                    add(Message(role = if (message.isUser()) "user" else "assistant", content = content))
                }
            }
            add(Message(content = prompt))
        }

    override suspend fun translateToEnglish(text: String): String =
        if (!settings.isAutoTranslateEnabled()) text
        else translationClient.translateToEnglish(text, settings.getGroqApiKey())

    private suspend fun generateImage(prompt: String): File = withContext(Dispatchers.IO) {
        val (account, token) = requireCloudflareCredentials()
        val (width, height) = ImageSpec.generationSize(settings.getImageFormat())
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("width", width.toString())
            .addFormDataPart("height", height.toString())
            .addFormDataPart("seed", Random.nextInt(0, Int.MAX_VALUE).toString())
            .build()
        val response = cloudflareApiService.runKlein(kleinUrl(account), "Bearer $token", body)
        val encoded = response.result?.image?.takeIf { it.isNotBlank() }
            ?: throw IOException(stringsContext.getString(R.string.error_no_image))
        saveDecodedImage(Base64.decode(encoded, Base64.DEFAULT))
    }

    private fun requireCloudflareCredentials(): Pair<String, String> {
        val account = settings.getCfAccountId().takeIf { it.isNotBlank() }
            ?: throw IOException(stringsContext.getString(R.string.cf_key_required))
        val token = settings.getCfApiToken().takeIf { it.isNotBlank() }
            ?: throw IOException(stringsContext.getString(R.string.cf_key_required))
        return account to token
    }

    // Входное изображение для правки должно быть меньше 512×512 — Klein отклоняет крупные.
    private fun downscaleForEdit(source: File, bounds: BitmapFactory.Options): File {
        if (bounds.outWidth <= ImageSpec.EDIT_INPUT_MAX && bounds.outHeight <= ImageSpec.EDIT_INPUT_MAX) {
            return source
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= ImageSpec.EDIT_INPUT_MAX &&
            bounds.outHeight / (sample * 2) >= ImageSpec.EDIT_INPUT_MAX
        ) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IOException(stringsContext.getString(R.string.edit_source_missing))
        val scale = minOf(1f, ImageSpec.EDIT_INPUT_MAX.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val out = File(context.cacheDir, "edit_input_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        return out
    }

    private suspend fun saveDecodedImage(bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "generated").apply { mkdirs() }
        val file = File(dir, "image_${System.currentTimeMillis()}.${ImageSpec.imageExtension(bytes)}")
        try {
            file.writeBytes(bytes)
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        if (file.length() == 0L) {
            file.delete()
            throw IOException(stringsContext.getString(R.string.error_empty_file))
        }
        file
    }

    private fun kleinUrl(account: String): String =
        "https://api.cloudflare.com/client/v4/accounts/$account/ai/run/@cf/black-forest-labs/flux-2-klein-4b"

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

    companion object {
        private const val TEXT_CONTEXT_MESSAGES = 10
        private const val TEXT_MAX_TOKENS = 1000
        private const val TEXT_SYSTEM_PROMPT =
            "You are a helpful assistant. Answer in the language the user writes in. Be concise and accurate."
    }
}
