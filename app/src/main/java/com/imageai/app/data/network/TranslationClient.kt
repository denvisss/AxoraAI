package com.imageai.app.data.network

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object TranslationClient {

    private var _apiKey: String = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun init(apiKey: String = "") {
        _apiKey = apiKey
    }

    private const val TEXT_URL = "https://text.pollinations.ai/openai"

    fun translateToEnglish(russianText: String, apiKey: String = ""): String {
        val key = apiKey.ifEmpty { _apiKey }

        // Быстрая проверка: короткая строка с английскими словами
        if (isLikelyEnglish(russianText)) {
            return russianText
        }

        val json = buildString {
            append("""{"model":"openai","messages":[{"role":"system","content":"You are a professional translator. Translate ONLY the following text to English. Return ONLY the English translation. Do not add any explanation, quotes, or extra text:"},{"role":"user","content":"$russianText"}]}""")
        }

        val request = Request.Builder()
            .url(TEXT_URL)
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .apply {
                if (key.isNotBlank()) {
                    addHeader("Authorization", "Bearer $key")
                }
            }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return russianText
                }
                response.body?.string()?.extractTranslation() ?: russianText
            }
        } catch (_: Exception) {
            russianText
        }
    }

    private fun isLikelyEnglish(text: String): Boolean {
        val firstWord = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return false
        val englishWords = setOf(
            "a", "an", "the", "is", "it", "in", "on", "at", "to", "for",
            "of", "and", "or", "with", "that", "this", "what", "how",
            "create", "draw", "generate", "make", "show", "design", "picture",
            "image", "photo", "cat", "dog", "tree", "house", "car", "sky",
            "man", "woman", "boy", "girl", "child", "bird", "fish", "flower"
        )
        return englishWords.contains(firstWord)
    }

    private fun String.extractTranslation(): String {
        try {
            val json = this.trim()

            // Try OpenAI format: {"choices": [{"message": {"content": "..."}}]}
            val contentIdx = json.indexOf("\"content\"")
            if (contentIdx != -1) {
                val start = json.indexOf('"', contentIdx) + 1
                var end = start
                while (end < json.length) {
                    if (json[end] == '"' && json[end - 1] != '\\') break
                    end++
                }
                if (end > start) {
                    return json.substring(start, end)
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\t", " ")
                        .trim()
                }
            }

            // Try Pollinations format: {"message": "..."}
            val msgIdx = json.indexOf("\"message\"")
            if (msgIdx != -1) {
                val colonIdx = json.indexOf(':', msgIdx)
                val start = json.indexOf('"', colonIdx) + 1
                var end = start
                while (end < json.length) {
                    if (json[end] == '"' && json[end - 1] != '\\') break
                    end++
                }
                if (end > start) {
                    return json.substring(start, end)
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\t", " ")
                        .trim()
                }
            }

            return this
        } catch (_: Exception) {
            return this
        }
    }
}
