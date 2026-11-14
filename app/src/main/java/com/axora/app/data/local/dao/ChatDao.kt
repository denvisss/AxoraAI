package com.axora.app.data.local.dao

import androidx.room.*
import com.axora.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_messages WHERE generationType = :type ORDER BY timestamp ASC")
    fun getChatHistory(type: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE generationType = :type")
    suspend fun getAllMessagesOnce(type: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE generationType = :type")
    suspend fun clearMessages(type: String)

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ChatMessageEntity?

    @Query("UPDATE chat_messages SET text = :newText, originalPrompt = :newOriginal WHERE id = :messageId")
    suspend fun updateMessage(messageId: Long, newText: String, newOriginal: String?)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("UPDATE chat_messages SET imageUrl = NULL, videoUrl = NULL, audioUrl = NULL")
    suspend fun clearMediaPaths()
}
