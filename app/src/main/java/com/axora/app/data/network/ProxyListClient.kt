package com.axora.app.data.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProxyEntry(
    val ip: String,
    val port: Int,
    val protocol: String,
    val anonymity: String?,
    val country: String?,
    val city: String?,
    val score: Int
)

class ProxyListClient(private val client: OkHttpClient, private val gson: Gson) {

    suspend fun load(protocol: String): List<ProxyEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$LIST_URL_BASE/$protocol/data.json").build()
        val json = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        normalize(parse(json))
    }

    fun parse(json: String): List<ProxyEntry> {
        val dtos = runCatching { gson.fromJson(json, Array<ProxyDto>::class.java) }.getOrNull() ?: return emptyList()
        return dtos.mapNotNull { dto ->
            val ip = dto.ip?.trim().orEmpty()
            val port = dto.port ?: return@mapNotNull null
            if (ip.isEmpty() || port !in 1..65535) return@mapNotNull null
            ProxyEntry(
                ip = ip,
                port = port,
                protocol = dto.protocol?.trim().orEmpty(),
                anonymity = dto.anonymity?.trim(),
                country = dto.geolocation?.country?.trim(),
                city = dto.geolocation?.city?.trim(),
                score = dto.score ?: 0
            )
        }
    }

    private data class ProxyDto(
        val ip: String?,
        val port: Int?,
        val protocol: String?,
        val anonymity: String?,
        val score: Int?,
        val geolocation: GeoDto?
    )

    private data class GeoDto(
        val country: String?,
        val city: String?
    )

    companion object {
        private const val LIST_URL_BASE =
            "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols"
        private const val DEFAULT_LIMIT = 150

        private fun anonymityRank(value: String?): Int = when (value?.lowercase()) {
            "elite" -> 0
            "anonymous" -> 1
            else -> 2
        }

        fun normalize(entries: List<ProxyEntry>, limit: Int = DEFAULT_LIMIT): List<ProxyEntry> =
            entries.asSequence()
                .filter { it.ip.isNotBlank() && it.port in 1..65535 }
                .distinctBy { "${it.ip}:${it.port}" }
                .sortedWith(
                    compareByDescending<ProxyEntry> { it.score }
                        .thenBy { anonymityRank(it.anonymity) }
                )
                .take(limit)
                .toList()
    }
}
