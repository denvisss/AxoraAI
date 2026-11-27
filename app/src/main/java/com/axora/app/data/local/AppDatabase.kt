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
    version = 4,
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

        // MIGRATION_2_3 создала FTS-таблицу, но без триггеров: новые/изменённые
        // сообщения в индекс не попадали. Здесь досоздаём триггеры как в схеме Room.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_generationType_timestamp` ON `chat_messages` (`generationType`, `timestamp`)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `chat_messages_fts` USING FTS4(" +
                    "`text` TEXT NOT NULL, `textContent` TEXT, `originalPrompt` TEXT, content=`chat_messages`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chat_messages_fts_BEFORE_UPDATE BEFORE UPDATE ON `chat_messages` BEGIN DELETE FROM `chat_messages_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chat_messages_fts_BEFORE_DELETE BEFORE DELETE ON `chat_messages` BEGIN DELETE FROM `chat_messages_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chat_messages_fts_AFTER_UPDATE AFTER UPDATE ON `chat_messages` BEGIN INSERT INTO `chat_messages_fts`(`docid`, `text`, `textContent`, `originalPrompt`) VALUES (NEW.`rowid`, NEW.`text`, NEW.`textContent`, NEW.`originalPrompt`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chat_messages_fts_AFTER_INSERT AFTER INSERT ON `chat_messages` BEGIN INSERT INTO `chat_messages_fts`(`docid`, `text`, `textContent`, `originalPrompt`) VALUES (NEW.`rowid`, NEW.`text`, NEW.`textContent`, NEW.`originalPrompt`); END")
                db.execSQL("INSERT INTO chat_messages_fts(chat_messages_fts) VALUES('rebuild')")
            }
        }
    }
}