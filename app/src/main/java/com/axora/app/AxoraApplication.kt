package com.axora.app

import android.app.Application
import androidx.room.Room
import com.axora.app.data.local.AppDatabase
import com.axora.app.data.repository.ChatRepository
import com.axora.app.di.NetworkModule

class AxoraApplication : Application() {

    val repository: ChatRepository by lazy {
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "axora_database")
            .fallbackToDestructiveMigration()
            .build()
        ChatRepository(
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
