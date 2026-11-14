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

    suspend fun translateToEnglish(text: String, apiKey: String? = null): String =
        withContext(Dispatchers.IO) {
            if (!CYRILLIC.containsMatchIn(text)) return@withContext text

            val request = ChatMessageRequest(
                model = "openai",
                messages = listOf(
                    Message(role = "system", content = SYSTEM_PROMPT),
                    Message(role = "user", content = text)
                ),
                temperature = 0.3,
                max_tokens = 500
            )

            val httpRequest = Request.Builder()
                .url(if (apiKey.isNullOrBlank()) CLASSIC_TEXT_URL else KEYED_TEXT_URL)
                .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
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
            val parsed = runCatching {
                gson.fromJson(trimmed, ChatMessageResponse::class.java)
                    ?.choices?.firstOrNull()?.message?.content?.trim()
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        return trimmed
    }

    companion object {
        private const val CLASSIC_TEXT_URL = "https://text.pollinations.ai/"
        private val JSON = "application/json".toMediaType()
        private val CYRILLIC = Regex("[А-Яа-яЁё]")
        private const val SYSTEM_PROMPT =
            "Translate the following text to English. ONLY return the translation, nothing else. " +
                "Do not add any explanation, quotes, or formatting."
    }
}
