package com.axora.app.data.repository

import android.content.Context
import com.axora.app.data.local.dao.ChatDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStorage(
    private val chatDao: ChatDao,
    private val context: Context
) {

    suspend fun generatedMediaSize(): Long = withContext(Dispatchers.IO) {
        File(context.filesDir, "generated").listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun clearGeneratedMedia() = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "generated")
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        chatDao.clearMediaPaths(dir.absolutePath + "/")
    }
}