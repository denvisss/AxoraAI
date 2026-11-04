package com.imageai.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.imageai.app.ui.settings.SettingsScreen
import com.imageai.app.ui.theme.FigmaDarkBg
import com.imageai.app.ui.theme.FigmaWhite
import com.imageai.app.ui.theme.ImageAATheme
import com.imageai.app.ui.viewmodel.ChatViewModel
import com.imageai.app.data.local.createRepository
import com.imageai.app.ui.viewmodel.ChatViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                registerForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { }.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        val repository = createRepository(applicationContext)
        val viewModel: ChatViewModel by viewModels {
            ChatViewModelFactory(repository)
        }

        setContent {
            var showSettings by mutableStateOf(false)
            var darkThemeEnabled by mutableStateOf(false)

            val systemDarkTheme = isSystemInDarkTheme()

            ImageAATheme(darkTheme = systemDarkTheme) {
                val surfaceColor = if (systemDarkTheme) FigmaDarkBg else FigmaWhite
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = surfaceColor
                ) {
                    if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            repository = repository,
                            darkTheme = darkThemeEnabled,
                            onToggleDarkTheme = { enabled ->
                                darkThemeEnabled = enabled
                            }
                        )
                    } else {
                        ChatScreen(
                            viewModel = viewModel,
                            darkTheme = darkThemeEnabled,
                            onSettingsClick = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
