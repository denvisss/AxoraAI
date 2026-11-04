package com.imageai.app.data.local

import android.content.Context
import androidx.room.Room
import com.imageai.app.data.local.dao.ChatDao
import com.imageai.app.data.network.ImageApiService
import com.imageai.app.data.repository.ChatRepository
import com.imageai.app.di.NetworkModule
import java.io.File

object DatabaseProvider {
    private var database: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "imageai_database"
        ).build().also { database = it }
    }

    fun clear() {
        database = null
    }
}

fun createRepository(context: Context): ChatRepository {
    val database = DatabaseProvider.getDatabase(context)
    val imageDir = File(context.cacheDir, "images")
    return ChatRepository(
        chatDao = database.chatDao(),
        imageApiService = NetworkModule.imageApiService,
        context = context
    )
}
