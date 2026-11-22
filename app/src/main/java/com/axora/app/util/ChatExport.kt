package com.axora.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.axora.app.R
import com.axora.app.domain.model.ChatMessage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Формирование и отправка текстового экспорта переписки вкладки (чат + настройки).
object ChatExport {

    /** Текст экспорта или null, если показывать нечего. */
    fun buildBody(context: Context, messages: List<ChatMessage>): String? {
        val visible = messages.filter { !it.isError }
        if (visible.isEmpty()) return null
        val userLabel = context.getString(R.string.export_user_label)
        val assistantLabel = context.getString(R.string.export_assistant_label)
        return visible.joinToString("\n\n") { message ->
            val role = if (message.isUser()) userLabel else assistantLabel
            val content = when {
                message.hasMedia() -> (message.originalPrompt ?: message.text).let { prompt ->
                    val marker = context.getString(
                        when {
                            message.isImageMessage() -> R.string.export_image_marker
                            message.isVideoMessage() -> R.string.export_video_marker
                            else -> R.string.export_audio_marker
                        }
                    )
                    "$marker $prompt"
                }
                else -> message.textContent ?: message.text
            }
            "$role:\n$content"
        }
    }

    fun share(context: Context, body: String) {
        shareIntent(context, Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        })
    }

    // ===== Markdown =====

    /** Markdown-экспорт: роль — заголовок, медиа — курсивные маркеры, медиафайлы не вкладываются. */
    fun buildMarkdown(context: Context, messages: List<ChatMessage>): String? {
        val visible = messages.filter { !it.isError }
        if (visible.isEmpty()) return null
        val userLabel = context.getString(R.string.export_user_label)
        val assistantLabel = context.getString(R.string.export_assistant_label)
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return visible.joinToString("\n\n") { message ->
            val role = if (message.isUser()) userLabel else assistantLabel
            val content = when {
                message.hasMedia() -> {
                    val marker = context.getString(
                        when {
                            message.isImageMessage() -> R.string.export_image_marker
                            message.isVideoMessage() -> R.string.export_video_marker
                            else -> R.string.export_audio_marker
                        }
                    )
                    val prompt = message.originalPrompt ?: message.text
                    val attachment = listOfNotNull(message.imageUrl, message.videoUrl, message.audioUrl)
                        .map { File(it).name }
                        .joinToString(", ")
                    "_$marker${if (attachment.isNotEmpty()) " ($attachment)" else ""}: ${prompt}_"
                }
                else -> (message.textContent ?: message.text)
            }
            "**$role** · ${df.format(Date(message.timestamp))}\n\n$content"
        }
    }

    fun shareMarkdown(context: Context, markdown: String) {
        shareIntent(context, Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_TEXT, markdown)
        })
    }

    // ===== PDF =====

    /** PDF через android.graphics.pdf.PdfDocument: простой текстовый рендер, картинки — уменьшенные превью. */
    fun buildPdf(context: Context, messages: List<ChatMessage>): File? {
        val visible = messages.filter { !it.isError }
        if (visible.isEmpty()) return null
        val userLabel = context.getString(R.string.export_user_label)
        val assistantLabel = context.getString(R.string.export_assistant_label)
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - 2 * margin
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            color = Color.BLACK
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = 10f
            color = Color.DKGRAY
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = 8f
            color = Color.GRAY
        }
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun finishPage() {
            doc.finishPage(page)
        }
        fun newPage() {
            finishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }
        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin) newPage()
        }
        fun drawLines(text: String, paint: Paint, lineHeight: Float) {
            // Наивный перенос: режем по ширине, затем по явным переводам строки.
            text.split("\n").forEach { rawLine ->
                val words = rawLine.split(" ")
                var current = StringBuilder()
                words.forEach { word ->
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(candidate) > contentWidth && current.isNotEmpty()) {
                        ensureSpace(lineHeight)
                        canvas.drawText(current.toString(), margin, y, paint)
                        y += lineHeight
                        current = StringBuilder(word)
                    } else {
                        current = StringBuilder(candidate)
                    }
                }
                if (current.isNotEmpty()) {
                    ensureSpace(lineHeight)
                    canvas.drawText(current.toString(), margin, y, paint)
                    y += lineHeight
                }
            }
        }

        visible.forEach { message ->
            val role = if (message.isUser()) userLabel else assistantLabel
            ensureSpace(16f)
            drawLines("$role · ${df.format(Date(message.timestamp))}", titlePaint, 14f)
            y += 2f
            if (message.hasMedia()) {
                val marker = context.getString(
                    when {
                        message.isImageMessage() -> R.string.export_image_marker
                        message.isVideoMessage() -> R.string.export_video_marker
                        else -> R.string.export_audio_marker
                    }
                )
                drawLines("$marker: ${message.originalPrompt ?: message.text}", bodyPaint, 13f)
                if (message.isImageMessage()) {
                    message.imageUrl?.let { path ->
                        val bitmap = decodeScaled(path)
                        if (bitmap != null) {
                            val maxImageWidth = contentWidth * 0.6f
                            val scale = minOf(1f, maxImageWidth / bitmap.width)
                            val targetW = bitmap.width * scale
                            val targetH = bitmap.height * scale
                            ensureSpace(targetH + 8f)
                            val left = margin
                            canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, y, left + targetW, y + targetH), imagePaint)
                            y += targetH + 8f
                            bitmap.recycle()
                        }
                    }
                }
            } else {
                drawLines(message.textContent ?: message.text, bodyPaint, 13f)
            }
            y += 10f
        }
        finishPage()

        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val out = File(dir, "axora_chat_${System.currentTimeMillis()}.pdf")
        return try {
            out.outputStream().use { doc.writeTo(it) }
            out
        } catch (_: Exception) {
            out.delete()
            null
        } finally {
            doc.close()
        }
    }

    private fun decodeScaled(path: String, maxSide: Int = 512): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        shareIntent(
            context,
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    private fun shareIntent(context: Context, intent: Intent) {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.export_chooser))
        )
    }
}
