package com.axora.app.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.axora.app.AxoraApplication
import com.axora.app.BuildConfig
import com.axora.app.data.repository.SettingsRepository
import com.axora.app.ui.settings.SettingsScreen
import com.axora.app.ui.theme.AxoraTheme
import com.axora.app.ui.viewmodel.ChatViewModel
import com.axora.app.ui.viewmodel.ChatViewModelFactory
import com.axora.app.ui.viewmodel.SettingsViewModel
import com.axora.app.ui.viewmodel.SettingsViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsRepository.wrapLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AxoraApplication
        lifecycleScope.launch { app.updateManager.autoCheckIfDue(BuildConfig.VERSION_NAME) }
        val chatViewModel: ChatViewModel by viewModels { ChatViewModelFactory(app.repository, this) }
        val settingsViewModel: SettingsViewModel by viewModels {
            SettingsViewModelFactory(app.settingsRepository, app.mediaStorage, app.updateManager)
        }

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val systemDark = isSystemInDarkTheme()
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val darkTheme = settingsState.darkTheme ?: systemDark

            BackHandler(enabled = showSettings) { showSettings = false }

            AxoraTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            viewModel = settingsViewModel,
                            onLanguageChange = { language ->
                                settingsViewModel.setAppLanguage(language)
                                if (Build.VERSION.SDK_INT >= 33) {
                                    getSystemService(LocaleManager::class.java).applicationLocales =
                                        LocaleList.forLanguageTags(language)
                                } else {
                                    recreate()
                                }
                            }
                        )
                    } else {
                        ChatScreen(
                            viewModel = chatViewModel,
                            onSettingsClick = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}