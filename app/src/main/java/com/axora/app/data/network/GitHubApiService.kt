package com.axora.app.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    val size: Long = 0L,
    val digest: String? = null
)

interface GitHubApiService {
    @GET("repos/denvisss/AxoraAI/releases")
    suspend fun releases(@Query("per_page") perPage: Int = 20): List<GitHubRelease>
}