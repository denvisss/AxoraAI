package com.axora.app.data.local.dao

import androidx.room.*
import com.axora.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // Пагинация: последние limit сообщений DESC — репозиторий разворачивает в ASC.
    @Query("SELECT * FROM chat_messages WHERE generationType = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getChatHistoryDesc(type: String, limit: Int): Flow<List<ChatMessageEntity>>

    @Query(
        "SELECT chat_messages.* FROM chat_messages JOIN chat_messages_fts " +
            "ON chat_messages.rowid = chat_messages_fts.rowid " +
            "WHERE chat_messages_fts MATCH :query ORDER BY timestamp DESC LIMIT 100"
    )
    suspend fun searchMessages(query: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE generationType = :type")
    suspend fun getAllMessagesOnce(type: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE imageUrl IS NOT NULL AND imageUrl != '' AND isError = 0 AND originalPrompt IS NOT NULL ORDER BY timestamp DESC")
    fun getAllImageMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE generationType = :type")
    suspend fun clearMessages(type: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    // Импорт бэкапа атомарно: crash посреди восстановления не оставит пустую базу.
    @Transaction
    suspend fun restoreMessages(messages: List<ChatMessageEntity>) {
        deleteAllMessages()
        messages.forEach { insertMessage(it) }
    }

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ChatMessageEntity?

    // textContent — то, что реально уходит в контекст и экспорт; без него правка
    // не долетала до следующих запросов (использовался старый текст).
    @Query("UPDATE chat_messages SET text = :newText, textContent = :newText, originalPrompt = :newOriginal WHERE id = :messageId")
    suspend fun updateMessage(messageId: Long, newText: String, newOriginal: String?)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    // Чистим только ссылки на удалённые файлы: импортированные картинки лежат
    // в другой папке и должны пережить очистку сгенерированных медиа.
    @Query(
        "UPDATE chat_messages SET " +
            "imageUrl = CASE WHEN imageUrl LIKE :dirPrefix || '%' THEN NULL ELSE imageUrl END, " +
            "videoUrl = CASE WHEN videoUrl LIKE :dirPrefix || '%' THEN NULL ELSE videoUrl END, " +
            "audioUrl = CASE WHEN audioUrl LIKE :dirPrefix || '%' THEN NULL ELSE audioUrl END"
    )
    suspend fun clearMediaPaths(dirPrefix: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE generationType = :type AND role = 'assistant' AND isError = 0 AND timestamp >= :since")
    suspend fun countAssistantMessages(type: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE generationType = :type AND role = 'assistant' AND isError = 0")
    suspend fun countAssistantMessagesTotal(type: String): Int
}
