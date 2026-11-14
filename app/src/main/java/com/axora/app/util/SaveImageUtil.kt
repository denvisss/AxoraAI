package com.axora.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.axora.app.domain.model.GenerationType
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SaveImageUtil {

    /** Blocking I/O — call from Dispatchers.IO. Throws IOException on failure. */
    fun saveToGallery(context: Context, file: File, type: GenerationType) {
        if (!file.exists()) throw IOException("file missing")
        val (collection, subdir) = when (type) {
            GenerationType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            GenerationType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            GenerationType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            GenerationType.TEXT -> throw IOException("text is not savable")
        }
        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Axora_$timestamp.${file.extension}")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType(file, type))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$subdir/Axora")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            uri = resolver.insert(collection, values) ?: throw IOException("MediaStore unavailable")
            val out = resolver.openOutputStream(uri) ?: throw IOException("cannot open output stream")
            out.use { output -> file.inputStream().use { it.copyTo(output) } }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            throw if (e is IOException) e else IOException(e.message ?: "save failed", e)
        }
    }

    fun share(context: Context, file: File, type: GenerationType): Boolean {
        if (!file.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                this.type = mimeType(file, type)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(com.axora.app.R.string.action_share)))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun mimeType(file: File, type: GenerationType): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
            ?: when (type) {
                GenerationType.IMAGE -> "image/jpeg"
                GenerationType.VIDEO -> "video/mp4"
                GenerationType.AUDIO -> "audio/mpeg"
                GenerationType.TEXT -> "text/plain"
            }
}