package com.axora.app.data.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Живой каталог моделей: списки с серверов Groq и Cloudflare.
// Если запрос не удался — вызывающий код падает обратно на статический список.
class ModelCatalogClient(
    private val client: OkHttpClient,
    private val gson: Gson
) {

    /** ID моделей, доступных на аккаунте Cloudflare (_workers_ai). */
    suspend fun cloudflareImageModels(accountId: String, token: String): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/$accountId/ai/models/search?task=Text-to-Image")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val raw = response.body?.string() ?: error("Empty body")
                val parsed = gson.fromJson(raw, CloudflareModelsResponse::class.java)
                    ?: error("Bad JSON")
                parsed.result
                    ?.filter { it.availability?.contains("public") == true }
                    ?.mapNotNull { it.name }
                    .orEmpty()
            }
        }

    /** ID моделей Groq (chat completions). */
    suspend fun groqTextModels(apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val raw = response.body?.string() ?: error("Empty body")
                val parsed = gson.fromJson(raw, GroqModelsResponse::class.java)
                    ?: error("Bad JSON")
                parsed.data.orEmpty().mapNotNull { it.id }
            }
        }
}

data class CloudflareModelsResponse(val result: List<CloudflareModelInfo>?)

data class CloudflareModelInfo(
    val name: String? = null,
    val availability: List<String>? = null,
    val task: CloudflareModelTask? = null
)

data class CloudflareModelTask(val name: String? = null)

data class GroqModelsResponse(val data: List<GroqModelInfo>?)

data class GroqModelInfo(val id: String? = null)
