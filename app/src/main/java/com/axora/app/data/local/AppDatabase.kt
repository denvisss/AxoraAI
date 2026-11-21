package com.axora.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.axora.app.data.local.dao.ChatDao
import com.axora.app.data.local.entity.ChatMessageEntity
import com.axora.app.data.local.entity.ChatMessagesFts

@Database(
    entities = [ChatMessageEntity::class, ChatMessagesFts::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isError INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE chat_messages SET isError = 1 WHERE role = 'assistant' AND text LIKE 'Ошибка:%'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `chat_messages_fts` USING FTS4(" +
                        "`text` TEXT NOT NULL, `textContent` TEXT, `originalPrompt` TEXT, content=`chat_messages`)"
                )
                db.execSQL("INSERT INTO chat_messages_fts(chat_messages_fts) VALUES('rebuild')")
            }
        }
    }
}