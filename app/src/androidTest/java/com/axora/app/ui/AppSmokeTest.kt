package com.axora.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.axora.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // Диалог «Что нового» может перекрыть экран после обновления — закрываем, если он есть.
    private fun dismissWhatsNewIfShown() {
        val closeNodes = composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.close)
        )
        if (closeNodes.fetchSemanticsNodes().isNotEmpty()) {
            closeNodes.onFirst().performClick()
        }
    }

    @Test
    fun mainScreenShowsEmptyState() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(
                composeRule.activity.getString(R.string.prompt_examples_title)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        dismissWhatsNewIfShown()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_send)
        ).assertDoesNotExist()
    }

    @Test
    fun settingsShowsProxyAndThemeControls() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.cd_settings)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        dismissWhatsNewIfShown()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_settings)
        ).performClick()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.proxy_title)
        ).assertExists()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.theme_title)
        ).assertExists()
        // «Как в системе» встречается дважды: подзаголовок и сегмент кнопки.
        composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.theme_system)
        ).fetchSemanticsNodes().isNotEmpty().let { exists -> check(exists) }
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.text_model_title)
        ).assertExists()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.backup_section)
        ).assertExists()
    }

    @Test
    fun proxyScreenOpensFromSettings() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.cd_settings)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        dismissWhatsNewIfShown()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_settings)
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.proxy_title)
        ).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.proxy_refresh)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.proxy_own_title)
        ).assertExists()
    }
}
