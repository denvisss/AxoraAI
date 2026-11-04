package com.imageai.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.imageai.app.data.local.dao.ChatDao
import com.imageai.app.data.local.entity.ChatMessageEntity
import com.imageai.app.data.network.ImageApiService
import com.imageai.app.data.network.TranslationClient
import com.imageai.app.domain.model.ChatMessage
import com.imageai.app.domain.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val PREFS_NAME = "imageai_prefs"
private const val KEY_API_KEY = "pollinations_api_key"

class ChatRepository(
    private val chatDao: ChatDao,
    private val imageApiService: ImageApiService,
    private val context: Context
) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""
    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun getChatHistory(): Flow<List<ChatMessage>> =
        chatDao.getChatHistory().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun insertMessage(message: ChatMessage): Long {
        return chatDao.insertMessage(message.toEntity())
    }

    suspend fun updateMessage(messageId: Long, newText: String, newOriginal: String?) {
        chatDao.updateMessage(messageId, newText, newOriginal)
    }

    suspend fun deleteMessage(messageId: Long) {
        chatDao.deleteMessage(messageId)
    }

    suspend fun clearAllMessages() {
        chatDao.clearAllMessages()
    }

    suspend fun generateImage(prompt: String, width: Int = 1024, height: Int = 1024): File {
        val apiKey = getApiKey()

        // Кодирование промта
        val encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")

        // Формируем URL
        var url = "https://gen.pollinations.ai/image/${encodedPrompt}?model=flux"

        if (apiKey.isNotBlank()) {
            url += "&key=${apiKey.replace("+", "%2B").replace(" ", "%20")}"
        }

        try {
            val responseBody = imageApiService.generateImage(fullUrl = url)

            val imageFile = File(context.cacheDir, "image_${System.currentTimeMillis()}.jpg")
            imageFile.outputStream().use { outputStream ->
                responseBody.byteStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return imageFile
        } catch (e: Exception) {
            throw Exception("HTTP ${e.message}", e)
        }
    }

    suspend fun translateToEnglish(text: String): String {
        val apiKey = getApiKey()
        return TranslationClient.translateToEnglish(text, apiKey)
    }

    private fun ChatMessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            role = when (role) {
                "user" -> Role.USER
                else -> Role.ASSISTANT
            },
            text = text,
            imageUrl = imageUrl,
            originalPrompt = originalPrompt,
            translatedPrompt = translatedPrompt,
            timestamp = timestamp
        )
    }

    private fun ChatMessage.toEntity(): ChatMessageEntity {
        return ChatMessageEntity(
            id = id,
            role = role.name.lowercase(),
            text = text,
            imageUrl = imageUrl,
            originalPrompt = originalPrompt,
            translatedPrompt = translatedPrompt,
            timestamp = timestamp
        )
    }
}
