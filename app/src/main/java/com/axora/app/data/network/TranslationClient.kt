package com.axora.app.data.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TranslationClient(
    private val client: OkHttpClient,
    private val gson: Gson
) {

    suspend fun translateToEnglish(text: String, groqApiKey: String?): String =
        withContext(Dispatchers.IO) {
            if (!CYRILLIC.containsMatchIn(text)) return@withContext text
            val key = groqApiKey?.takeIf { it.isNotBlank() } ?: return@withContext text

            val request = ChatMessageRequest(
                model = TEXT_MODEL,
                messages = listOf(
                    Message(role = "system", content = SYSTEM_PROMPT),
                    Message(role = "user", content = text)
                ),
                temperature = 0.3,
                max_tokens = 1024,
                // gpt-oss — reasoning-модель: без low-режима лимит токенов уходит на «размышления»
                // и content приходит пустым.
                reasoning_effort = "low"
            )

            val httpRequest = Request.Builder()
                .url(GROQ_CHAT_URL)
                .header("Authorization", "Bearer $key")
                .post(gson.toJson(request).toRequestBody(JSON))
                .build()

            try {
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) return@withContext text
                    val raw = response.body?.string() ?: return@withContext text
                    parseTranslation(raw) ?: text
                }
            } catch (_: Exception) {
                text
            }
        }

    private fun parseTranslation(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) {
            // Пустой content (лимит токенов ушёл на reasoning) — не отправляем сырой JSON в промпт.
            val parsed = runCatching {
                gson.fromJson(trimmed, ChatMessageResponse::class.java)
                    ?.choices?.firstOrNull()?.message?.content?.trim()
            }.getOrNull()
            return parsed?.takeIf { it.isNotEmpty() }
        }
        return trimmed
    }

    companion object {
        private const val GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val TEXT_MODEL = "openai/gpt-oss-120b"
        private val JSON = "application/json".toMediaType()
        private val CYRILLIC = Regex("[А-Яа-яЁё]")
        private const val SYSTEM_PROMPT =
            "Translate the following text to English. ONLY return the translation, nothing else. " +
                "Do not add any explanation, quotes, or formatting."
    }
}
