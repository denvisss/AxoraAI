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
import com.axora.app.data.network.ApiErrors
import com.axora.app.data.network.ImageUrl
import com.axora.app.data.network.ChatMessageRequest
import com.axora.app.data.network.CloudflareApiService
import com.axora.app.data.network.Message
import com.axora.app.data.network.MessagePart
import com.axora.app.data.network.TextApiService
import com.axora.app.data.network.TranslationClient
import com.axora.app.di.NetworkModule
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import com.axora.app.domain.model.ImageModel
import com.axora.app.domain.model.Role
import com.axora.app.util.ImageSpec
import com.axora.app.util.SaveImageUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
        deleteMediaPaths(message.imageUrl, message.videoUrl, message.audioUrl)
    }

    override fun getImageFormat(): ImageFormat = settings.getImageFormat()

    override fun setImageFormat(format: ImageFormat) = settings.setImageFormat(format)

    override fun getImageModel(): String = settings.getImageModel()

    override fun setImageModel(modelId: String) = settings.setImageModel(modelId)

    override fun getTextModel(): String = settings.getTextModel()

    override fun setTextModel(modelId: String) = settings.setTextModel(modelId)

    override suspend fun fetchImageModels(): List<ImageModel>? = withContext(Dispatchers.IO) {
        val account = settings.getCfAccountId().takeIf { it.isNotBlank() } ?: return@withContext null
        val token = settings.getCfApiToken().takeIf { it.isNotBlank() } ?: return@withContext null
        runCatching {
            val remote = NetworkModule.modelCatalogClient.cloudflareImageModels(account, token)
            // Порядок статического каталога сохраняем (самая мощная сверху);
            // удаляем модели, которых нет на аккаунте, добавляем в хвост новые.
            val known = ImageModel.catalog.filter { it.id in remote }
            val unknown = remote.filter { id -> ImageModel.byId(id) == null && id.startsWith("@cf/") }
                .mapNotNull { id ->
                    val label = id.substringAfterLast('/')
                    ImageModel(id = id, displayName = label, description = "Cloudflare Workers AI")
                }
            (known + unknown).takeIf { known.isNotEmpty() || unknown.isNotEmpty() }
        }.getOrNull()
    }

    override suspend fun generateContent(prompt: String, type: GenerationType, imageModel: String?): File = when (type) {
        GenerationType.IMAGE -> generateImage(prompt, imageModel)
        GenerationType.TEXT -> throw IllegalArgumentException("TEXT генерируется через generateText")
        GenerationType.VIDEO, GenerationType.AUDIO ->
            throw IllegalArgumentException("Неподдерживаемый тип генерации: $type")
    }

    override suspend fun editImage(source: File, instruction: String, imageModel: String?): File = withContext(Dispatchers.IO) {
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
            val response = cloudflareApiService.runKlein(runUrl(account, resolveImageModelId(imageModel)), "Bearer $token", body)
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

    @Volatile private var groqBlockedUntilMs = 0L

    override suspend fun generateTextStreamed(
        prompt: String,
        history: List<ChatMessage>,
        onDelta: (String) -> Unit,
        attachedImage: File?
    ): String {
        // Vision-запросы идут на Cloudflare (Groq отключила llama-4-scout 17.07.2026),
        // текстовые — на Groq; при недоступности Groq (403/5xx/мёртвая модель) — fallback на Cloudflare.
        // Без ключа Groq текст тоже можно генерировать: только через Cloudflare.
        if (attachedImage != null) {
            return generateTextOnCloudflare(prompt, history, attachedImage, onDelta)
        }
        val apiKey = settings.getGroqApiKey().takeIf { it.isNotBlank() }
        // Регион-блок Groq (403) запоминаем на несколько минут: на каждой реплике
        // ждать отказ бессмысленно, рабочий провайдер всё равно Cloudflare.
        if (apiKey == null || System.currentTimeMillis() < groqBlockedUntilMs) {
            return generateTextOnCloudflare(prompt, history, null, onDelta)
        }
        val model = settings.getTextModel()
        val messages = buildTextMessages(prompt, history, attachedImage)
        val body = gson.toJson(
            ChatMessageRequest(
                model = model,
                messages = messages,
                temperature = 0.7,
                max_tokens = TEXT_MAX_TOKENS,
                stream = true,
                reasoning_effort = if (model.contains("gpt-oss")) "low" else null
            )
        )
        var emitted = false
        val full = try {
            NetworkModule.streamGroqChat(body, apiKey) { updated ->
                emitted = true
                onDelta(updated)
            }.trim()
        } catch (e: IOException) {
            // 403 Forbidden / блокировка сети / мёртвая модель — пробуем Cloudflare,
            // но только если UI ещё ничего не получил: иначе текст начнётся заново.
            if (emitted) throw e
            val forbidden = e.message.orEmpty().contains("HTTP 403")
            if (forbidden) groqBlockedUntilMs = System.currentTimeMillis() + GROQ_BLOCK_TTL_MS
            try {
                generateTextOnCloudflare(prompt, history, null) { updated -> onDelta(updated) }
            } catch (cf: IOException) {
                throw textProvidersFailure(e, cf, forbidden)
            }
        }
        if (full.isEmpty()) throw IOException(stringsContext.getString(R.string.text_empty))
        return full
    }

    // Оба провайдера упали: показываем причины вместе, чтобы было видно, кто именно
    // отказал (Groq 403 регион + исчерпанный общий лимит Cloudflare — частый случай).
    private fun textProvidersFailure(groq: IOException, cf: IOException, forbidden: Boolean): IOException {
        val cfDaily = ApiErrors.isDailyNeuronLimit(cf.message.orEmpty())
        val cfConfigured = settings.getCfAccountId().isNotBlank() && settings.getCfApiToken().isNotBlank()
        val message = when {
            forbidden && cfDaily -> stringsContext.getString(R.string.error_text_quota)
            forbidden && !cfConfigured -> stringsContext.getString(R.string.error_groq_forbidden)
            else -> stringsContext.getString(
                R.string.error_text_providers,
                if (forbidden) stringsContext.getString(R.string.error_groq_forbidden_short)
                else groq.message.orEmpty().take(200),
                if (cfDaily) stringsContext.getString(R.string.error_cf_daily_short)
                else cf.message.orEmpty().take(200)
            )
        }
        return UserFacingException(message)
    }

    // Текст и vision через OpenAI-совместимый эндпоинт Workers AI (vision-модель Groq отключена).
    private suspend fun generateTextOnCloudflare(
        prompt: String,
        history: List<ChatMessage>,
        attachedImage: File?,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (attachedImage == null) {
            val account = settings.getCfAccountId().takeIf { it.isNotBlank() }
            val token = settings.getCfApiToken().takeIf { it.isNotBlank() }
            if (account == null || token == null) {
                throw IOException(stringsContext.getString(R.string.text_key_required))
            }
            return@withContext generateTextRequest(account, token, prompt, history, null, onDelta)
        }
        val (account, token) = requireCloudflareCredentials()
        generateTextRequest(account, token, prompt, history, attachedImage, onDelta)
    }

    private suspend fun generateTextRequest(
        account: String,
        token: String,
        prompt: String,
        history: List<ChatMessage>,
        attachedImage: File?,
        onDelta: (String) -> Unit
    ): String {
        val model = if (attachedImage != null) CF_VISION_MODEL else cloudflareTextModel(settings.getTextModel())
        val messages = buildTextMessages(prompt, history, attachedImage)
        val body = gson.toJson(
            ChatMessageRequest(
                model = model,
                messages = messages,
                temperature = 0.7,
                max_tokens = TEXT_MAX_TOKENS,
                stream = true,
                reasoning_effort = if (model.contains("gpt-oss")) "low" else null
            )
        )
        val url = "https://api.cloudflare.com/client/v4/accounts/$account/ai/v1/chat/completions"
        val full = streamOpenAiChat(url, "Bearer $token", body, onDelta)
        if (full.isEmpty()) throw IOException(stringsContext.getString(R.string.text_empty))
        return full
    }

    private suspend fun streamOpenAiChat(url: String, auth: String, requestJson: String, onDelta: (String) -> Unit): String =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("Authorization", auth)
                .header("Accept", "text/event-stream")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()
            val call = NetworkModule.httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    response.use { resp ->
                        try {
                            val body = resp.body ?: throw IOException("Empty response body")
                            if (!resp.isSuccessful) {
                                val raw = body.string()
                                throw IOException("HTTP ${resp.code}: ${raw.take(300)}")
                            }
                            val full = StringBuilder()
                            val source = body.source()
                            while (true) {
                                val line = source.readUtf8Line() ?: break
                                if (!line.startsWith("data:")) continue
                                val payload = line.substring(5).trim()
                                if (payload == "[DONE]") break
                                val delta = parseSseDelta(payload)
                                if (delta.isNotEmpty()) {
                                    full.append(delta)
                                    onDelta(full.toString())
                                }
                            }
                            if (continuation.isActive) continuation.resume(full.toString())
                        } catch (t: Throwable) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(t as? IOException ?: IOException(t.message, t))
                            }
                        }
                    }
                }
            })
        }

    private fun parseSseDelta(payload: String): String = runCatching {
        val root = gson.fromJson(payload, JsonObject::class.java)
        val choices = root.getAsJsonArray("choices") ?: return ""
        val first = choices.firstOrNull() as? JsonObject ?: return ""
        first.getAsJsonObject("delta")
            ?.get("content")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            .orEmpty()
    }.getOrDefault("")

    private suspend fun buildTextMessages(prompt: String, history: List<ChatMessage>, attachedImage: File? = null): List<Message> =
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
            add(
                if (attachedImage != null) {
                    Message(role = "user", content = listOf(MessagePart(type = "text", text = prompt), imagePart(attachedImage)))
                } else {
                    Message(content = prompt)
                }
            )
        }

    // Vision-модели Groq принимают максимум ~4 МБ base64; большие фото даунскейлим
    // тем же путём, что и правку по референсу, иначе OOM и отказ запроса.
    private suspend fun imagePart(file: File): MessagePart = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val source = downscaleForEdit(file, bounds)
        try {
            val bytes = source.readBytes()
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            MessagePart(
                type = "image_url",
                image_url = ImageUrl(url = "data:image/jpeg;base64,$encoded")
            )
        } finally {
            if (source != file) source.delete()
        }
    }

    override suspend fun translateToEnglish(text: String): String =
        if (!settings.isAutoTranslateEnabled()) text
        else translationClient.translateToEnglish(text, settings.getGroqApiKey())

    private suspend fun generateImage(prompt: String, imageModel: String?): File = withContext(Dispatchers.IO) {
        val (account, token) = requireCloudflareCredentials()
        val modelId = resolveImageModelId(imageModel)
        val (width, height) = ImageSpec.generationSize(settings.getImageFormat())
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("width", width.toString())
            .addFormDataPart("height", height.toString())
            .addFormDataPart("seed", Random.nextInt(0, Int.MAX_VALUE).toString())
            .build()
        val response = cloudflareApiService.runKlein(runUrl(account, modelId), "Bearer $token", body)
        val encoded = response.result?.image?.takeIf { it.isNotBlank() }
            ?: throw IOException(stringsContext.getString(R.string.error_no_image))
        saveDecodedImage(Base64.decode(encoded, Base64.DEFAULT))
    }

    // Живой каталог отдаёт id, которых нет в ImageModel.catalog — принимаем любой
    // @cf/-идентификатор, статический каталог остаётся только дефолтом.
    private fun resolveImageModelId(id: String?): String =
        id?.takeIf { it.startsWith("@cf/") } ?: ImageModel.DEFAULT.id

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

    private fun runUrl(account: String, modelId: String): String =
        "https://api.cloudflare.com/client/v4/accounts/$account/ai/run/$modelId"

    private fun deleteMediaFiles(entity: ChatMessageEntity) {
        deleteMediaPaths(entity.imageUrl, entity.videoUrl, entity.audioUrl)
    }

    private fun deleteMediaPaths(vararg paths: String?) {
        paths.forEach { path ->
            runCatching { File(path!!).takeIf { it.exists() }?.delete() }
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

    // Groq-идентификаторы моделей не существуют на Workers AI — маппим на CF-аналоги.
    private fun cloudflareTextModel(groqId: String): String = when (groqId) {
        SettingsRepository.TEXT_MODEL_GPT_120B -> "@cf/openai/gpt-oss-120b"
        SettingsRepository.TEXT_MODEL_GPT_20B -> "@cf/openai/gpt-oss-20b"
        else -> CF_FALLBACK_TEXT_MODEL
    }

    companion object {
        private const val TEXT_CONTEXT_MESSAGES = 10
        private const val TEXT_MAX_TOKENS = 2048
        private const val GROQ_BLOCK_TTL_MS = 5 * 60 * 1000L
        private const val CF_VISION_MODEL = "@cf/meta/llama-4-scout-17b-16e-instruct"
        private const val CF_FALLBACK_TEXT_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
        private const val TEXT_SYSTEM_PROMPT =
            "You are a helpful assistant. Answer in the language the user writes in. Be concise and accurate."
    }
}
