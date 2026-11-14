package com.axora.app

import android.app.Application
import androidx.room.Room
import com.axora.app.data.local.AppDatabase
import com.axora.app.data.repository.ChatRepository
import com.axora.app.data.repository.DefaultChatRepository
import com.axora.app.data.repository.MediaStorage
import com.axora.app.data.repository.SettingsRepository
import com.axora.app.di.NetworkModule

class AxoraApplication : Application() {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "axora_database")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val mediaStorage: MediaStorage by lazy { MediaStorage(database.chatDao(), this) }

    val repository: ChatRepository by lazy {
        DefaultChatRepository(
            settings = settingsRepository,
            chatDao = database.chatDao(),
            imageApiService = NetworkModule.imageApiService,
            videoApiService = NetworkModule.videoApiService,
            audioApiService = NetworkModule.audioApiService,
            textApiService = NetworkModule.textApiService,
            translationClient = NetworkModule.translationClient,
            context = this
        )
    }
}