package com.axora.app.data.repository

import com.axora.app.data.network.GitHubAsset
import com.axora.app.data.network.GitHubRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun newerVersionIsDetected() {
        assertTrue(UpdateManager.isNewerVersion("v1.3.0", "1.2.0"))
        assertTrue(UpdateManager.isNewerVersion("v1.10.0", "1.9.0"))
        assertTrue(UpdateManager.isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(UpdateManager.isNewerVersion("1.3.1", "1.3.0"))
    }

    @Test
    fun sameOrOlderVersionIsNotNewer() {
        assertFalse(UpdateManager.isNewerVersion("v1.3.0", "1.3.0"))
        assertFalse(UpdateManager.isNewerVersion("v1.2.9", "1.3.0"))
        assertFalse(UpdateManager.isNewerVersion("v1.3", "1.3.0"))
    }

    @Test
    fun malformedVersionIsNotNewer() {
        assertFalse(UpdateManager.isNewerVersion("latest", "1.3.0"))
        assertFalse(UpdateManager.isNewerVersion("v1.3.0-rc1", "1.2.0"))
        assertFalse(UpdateManager.isNewerVersion("v1.4.0", "broken"))
    }

    @Test
    fun selectPicksNewestStableRelease() {
        val apk = GitHubAsset(name = "Axora-1.4.0.apk", downloadUrl = "https://example.com/a.apk")
        val releases = listOf(
            release("v1.5.0", draft = true, assets = listOf(apk)),
            release("v1.4.0", prerelease = true, assets = listOf(apk)),
            release("v1.3.0", assets = listOf(apk)),
            release("v1.2.0", assets = listOf(apk))
        )
        // Черновики и prerelease обновлением не считаются — остаётся v1.3.0.
        assertEquals("v1.3.0", UpdateManager.select(releases, "1.2.0")?.tagName)
    }

    @Test
    fun selectSkipsReleasesWithoutApkAndOlderOnes() {
        val noApk = GitHubAsset(name = "notes.txt", downloadUrl = "https://example.com/n.txt")
        val releases = listOf(
            release("v2.0.0", assets = listOf(noApk)),
            release("v1.2.0", assets = listOf(GitHubAsset("a.apk", "https://example.com/a.apk")))
        )
        assertNull(UpdateManager.select(releases, "1.3.0"))
    }

    @Test
    fun autoCheckIsDueOnlyAfter24Hours() {
        val now = 1_700_000_000_000L
        assertTrue(UpdateManager.isCheckDue(0L, now))
        assertTrue(UpdateManager.isCheckDue(now - UpdateManager.CHECK_INTERVAL_MS, now))
        assertFalse(UpdateManager.isCheckDue(now - UpdateManager.CHECK_INTERVAL_MS + 1, now))
        assertFalse(UpdateManager.isCheckDue(now, now))
    }

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<GitHubAsset> = emptyList()
    ) = GitHubRelease(tagName = tag, draft = draft, prerelease = prerelease, assets = assets)
}