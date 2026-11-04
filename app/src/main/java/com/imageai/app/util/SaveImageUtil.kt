package com.imageai.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SaveImageUtil {

    /**
     * Сохраняет изображение в галерею телефона
     */
    fun saveToGallery(context: Context, imageFile: File): Boolean {
        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return false

            // Название файла: ImageAI_YYYYMMDD_HHMMSS.jpg
            val fileName = "ImageAI_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
            val imagesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            ).absolutePath

            val file = File(imagesDir, fileName)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            // Уведомляем MediaStore что файл появился
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(file)
            context.sendBroadcast(intent)

            Toast.makeText(context, "Сохранено в $imagesDir", Toast.LENGTH_LONG).show()
            return true

        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    /**
     * Делится изображением (открывает меню "поделиться")
     */
    fun shareImage(context: Context, imageFile: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return

            val imagesDir = context.cacheDir.absolutePath
            val file = File(imagesDir, "share_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            }

            val uri = android.net.Uri.fromFile(file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Поделиться"))

        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка шаринга", Toast.LENGTH_SHORT).show()
        }
    }
}
