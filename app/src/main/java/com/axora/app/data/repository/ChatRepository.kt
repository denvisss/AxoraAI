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
    suspend fun generateContent(prompt: String, type: GenerationType): File
    suspend fun generateText(prompt: String, history: List<ChatMessage> = emptyList()): String

    // Дефолт — не-стриминговый fallback: фейковые репозитории в тестах трогать не нужно.
    suspend fun generateTextStreamed(
        prompt: String,
        history: List<ChatMessage> = emptyList(),
        onDelta: (String) -> Unit = {}
    ): String = generateText(prompt, history).also { onDelta(it) }
    suspend fun editImage(source: File, instruction: String): File
    suspend fun translateToEnglish(text: String): String
    suspend fun autoSaveToGallery(message: ChatMessage)
    suspend fun deleteMediaOf(message: ChatMessage)
    fun getImageFormat(): ImageFormat
    fun setImageFormat(format: ImageFormat)
}