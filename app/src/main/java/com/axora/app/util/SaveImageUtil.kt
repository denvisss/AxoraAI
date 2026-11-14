package com.axora.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.axora.app.R
import com.axora.app.domain.model.GenerationType
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SaveImageUtil {

    fun saveToGallery(context: Context, file: File, type: GenerationType): Boolean {
        if (!file.exists()) return false
        val (collection, subdir) = when (type) {
            GenerationType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            GenerationType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            GenerationType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            GenerationType.TEXT -> return false
        }
        val resolver = context.contentResolver
        var uri: Uri? = null
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Axora_$timestamp.${file.extension}")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType(file, type))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$subdir/Axora")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            uri = resolver.insert(collection, values) ?: throw IOException("MediaStore недоступен")
            val out = resolver.openOutputStream(uri) ?: throw IOException("не удалось открыть файл")
            out.use { output -> file.inputStream().use { it.copyTo(output) } }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            Toast.makeText(context, context.getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            Toast.makeText(context, context.getString(R.string.save_error, e.message.orEmpty()), Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun share(context: Context, file: File, type: GenerationType) {
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                this.type = mimeType(file, type)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.share_error), Toast.LENGTH_SHORT).show()
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
