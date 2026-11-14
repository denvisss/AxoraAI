package com.axora.app.data.repository

import android.content.Context
import com.axora.app.R
import com.axora.app.data.network.GitHubApiService
import com.axora.app.data.network.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class UpdateManager(
    private val gitHubApi: GitHubApiService,
    private val client: OkHttpClient,
    private val context: Context
) {

    suspend fun findUpdate(currentVersion: String): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            select(gitHubApi.releases(), currentVersion)
        } catch (e: Exception) {
            throw IOException(strings().getString(R.string.update_error_check), e)
        }
    }

    suspend fun download(release: GitHubRelease, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val asset = release.assets.first { it.name.endsWith(".apk") }
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            val file = File(dir, "axora-update.apk")
            try {
                val response = client.newCall(Request.Builder().url(asset.downloadUrl).build()).execute()
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    response.close()
                    throw IOException("HTTP ${response.code}")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (asset.size > 0) onProgress((downloaded * 100 / asset.size).toInt())
                        }
                    }
                }
                val expected = asset.digest?.removePrefix("sha256:")
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (expected != null && actual != expected) {
                    throw ChecksumMismatchException()
                }
            } catch (e: Exception) {
                file.delete()
                val message = if (e is ChecksumMismatchException) {
                    R.string.update_error_checksum
                } else {
                    R.string.update_error_download
                }
                throw IOException(strings().getString(message), e)
            }
            file
        }

    private fun strings(): Context = SettingsRepository.wrapLocale(context)

    private class ChecksumMismatchException : Exception()

    companion object {
        fun select(releases: List<GitHubRelease>, currentVersion: String): GitHubRelease? =
            releases.firstOrNull { release ->
                !release.draft &&
                    isNewerVersion(release.tagName, currentVersion) &&
                    release.assets.any { it.name.endsWith(".apk") }
            }

        fun isNewerVersion(tag: String, current: String): Boolean {
            val remote = parse(tag) ?: return false
            val local = parse(current) ?: return false
            for (i in 0 until maxOf(remote.size, local.size)) {
                val r = remote.getOrElse(i) { 0 }
                val l = local.getOrElse(i) { 0 }
                if (r != l) return r > l
            }
            return false
        }

        private fun parse(version: String): List<Int>? {
            val parts = version.trim().removePrefix("v").split('.')
            return parts.map { it.toIntOrNull() ?: return null }
        }
    }
}