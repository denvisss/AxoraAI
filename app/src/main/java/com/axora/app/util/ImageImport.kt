package com.axora.app.util

import android.content.Context
import android.net.Uri
import java.io.File

// Выбранное/полученное изображение копируется в filesDir/imported: content-URI живёт недолго.
object ImageImport {

    fun copyToImported(context: Context, uri: Uri, prefix: String = "imported"): File? = runCatching {
        val dir = File(context.filesDir, "imported").apply { mkdirs() }
        val out = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        if (out.length() == 0L) {
            out.delete()
            return null
        }
        out
    }.getOrNull()
}
