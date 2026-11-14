package com.axora.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.axora.app.AxoraApplication
import com.axora.app.ui.settings.SettingsScreen
import com.axora.app.ui.theme.AxoraTheme
import com.axora.app.ui.viewmodel.ChatViewModel
import com.axora.app.ui.viewmodel.ChatViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as AxoraApplication).repository
        val viewModel: ChatViewModel by viewModels { ChatViewModelFactory(repository) }
        val prefs = getSharedPreferences("axora_prefs", MODE_PRIVATE)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var darkTheme by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_theme", systemDark)) }

            BackHandler(enabled = showSettings) { showSettings = false }

            AxoraTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            repository = repository,
                            darkTheme = darkTheme,
                            onToggleDarkTheme = { enabled ->
                                darkTheme = enabled
                                prefs.edit().putBoolean("dark_theme", enabled).apply()
                            }
                        )
                    } else {
                        ChatScreen(
                            viewModel = viewModel,
                            onSettingsClick = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}