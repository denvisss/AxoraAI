package com.axora.app.data.repository

import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ChatRepository {
    fun getChatHistory(type: GenerationType, limit: Int): Flow<List<ChatMessage>>
    suspend fun searchHistory(query: String): List<ChatMessage>
    fun getAllImages(): Flow<List<ChatMessage>>
    suspend fun insertMessage(message: ChatMessage): Long
    suspend fun updateMessage(messageId: Long, newText: String, newOriginal: String?)
    suspend fun deleteMessage(messageId: Long)
    suspend fun clearChat(type: GenerationType)
    suspend fun generateContent(prompt: String, type: GenerationType, imageModel: String? = null): File
    suspend fun generateText(prompt: String, history: List<ChatMessage> = emptyList()): String

    // Дефолт — не-стриминговый fallback: фейковые репозитории в тестах трогать не нужно.
    suspend fun generateTextStreamed(
        prompt: String,
        history: List<ChatMessage> = emptyList(),
        onDelta: (String) -> Unit = {},
        attachedImage: File? = null
    ): String = generateText(prompt, history).also { onDelta(it) }
    suspend fun editImage(source: File, instruction: String, imageModel: String? = null): File
    suspend fun translateToEnglish(text: String): String
    suspend fun autoSaveToGallery(message: ChatMessage)
    suspend fun deleteMediaOf(message: ChatMessage)
    fun getImageFormat(): ImageFormat
    fun setImageFormat(format: ImageFormat)
    fun getImageModel(): String
    fun setImageModel(modelId: String)
    fun getTextModel(): String
    fun setTextModel(modelId: String)

    // Живой каталог моделей картинок; при ошибке сети/ключей — null (вызывающий код берёт статику).
    suspend fun fetchImageModels(): List<com.axora.app.domain.model.ImageModel>?
}