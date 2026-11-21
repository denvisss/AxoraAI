package com.axora.app.util

import android.content.Context
import android.content.Intent
import com.axora.app.R
import com.axora.app.domain.model.ChatMessage

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
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.export_chooser))
        )
    }
}
