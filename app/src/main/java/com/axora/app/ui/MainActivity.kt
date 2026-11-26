package com.axora.app.ui

import android.app.LocaleManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.lifecycleScope
import com.axora.app.AxoraApplication
import com.axora.app.BuildConfig
import com.axora.app.R
import com.axora.app.data.network.GitHubRelease
import com.axora.app.data.repository.SettingsRepository
import com.axora.app.data.repository.UpdateManager
import com.axora.app.ui.settings.SettingsScreen
import com.axora.app.ui.theme.AxoraTheme
import com.axora.app.ui.viewmodel.ChatViewModel
import com.axora.app.ui.viewmodel.ChatViewModelFactory
import com.axora.app.ui.viewmodel.SettingsViewModel
import com.axora.app.ui.viewmodel.SettingsViewModelFactory
import com.axora.app.ui.viewmodel.TemplateViewModel
import com.axora.app.ui.viewmodel.TemplateViewModelFactory
import com.axora.app.util.ImageImport
import com.axora.app.widget.QuickPromptWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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
            SettingsViewModelFactory(app.settingsRepository, app.mediaStorage, app.updateManager, app.chatDao, app.templateStore)
        }
        val templateViewModel: TemplateViewModel by viewModels { TemplateViewModelFactory(app.templateStore) }
        handleSharedImage(intent, chatViewModel)
        val openPromptFromWidget = intent?.action == QuickPromptWidgetProvider.ACTION_OPEN_PROMPT

        // Уведомление «генерация готова»: только когда приложение свёрнуто во время генерации.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { chatViewModel.appInForeground.set(true) }
            override fun onStop(owner: LifecycleOwner) { chatViewModel.appInForeground.set(false) }
        })
        lifecycleScope.launch {
            chatViewModel.completedInBackground.collect { completed ->
                if (completed) showCompletionNotification()
            }
        }

        // Разрешение уведомлений (Android 13+): запрос при первом запуске, чтобы
        // «генерация готова» работала без похода в настройки системы.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val systemDark = isSystemInDarkTheme()
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showGallery by rememberSaveable { mutableStateOf(false) }
            val darkTheme = settingsState.darkTheme ?: systemDark
            var whatsNew by remember { mutableStateOf<GitHubRelease?>(null) }

            LaunchedEffect(Unit) {
                if (BuildConfig.VERSION_CODE > app.settingsRepository.getLastWhatsNewVersionCode()) {
                    val release = app.updateManager.latestRelease()
                    if (release != null && UpdateManager.sameVersion(release.tagName, BuildConfig.VERSION_NAME)) {
                        whatsNew = release
                    }
                }
            }

            whatsNew?.let { release ->
                AlertDialog(
                    onDismissRequest = { whatsNew = null },
                    title = { Text(stringResource(R.string.whats_new_title, release.tagName)) },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = release.body?.replace("**", "").orEmpty(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            app.settingsRepository.setLastWhatsNewVersionCode(BuildConfig.VERSION_CODE)
                            whatsNew = null
                        }) { Text(stringResource(R.string.close)) }
                    }
                )
            }

            BackHandler(enabled = showSettings) { showSettings = false }
            BackHandler(enabled = showGallery) { showGallery = false }

            AxoraTheme(
                darkTheme = darkTheme,
                amoled = settingsState.amoled,
                dynamicColor = settingsState.dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            viewModel = settingsViewModel,
                            templateViewModel = templateViewModel,
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
                    } else if (showGallery) {
                        GalleryScreen(
                            viewModel = chatViewModel,
                            onBack = { showGallery = false }
                        )
                    } else {
                        ChatScreen(
                            viewModel = chatViewModel,
                            templateViewModel = templateViewModel,
                            onSettingsClick = { showSettings = true },
                            onGalleryClick = { showGallery = true },
                            requestInputFocus = openPromptFromWidget
                        )
                    }
                }
            }
        }
    }

    // Share target: «Поделиться → Axora» из галереи или браузера.
    private fun handleSharedImage(intent: Intent?, viewModel: ChatViewModel) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val file = ImageImport.copyToImported(this@MainActivity, uri, prefix = "shared") ?: return@launch
            viewModel.importImageFile(file)
        }
    }

    private fun showCompletionNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_generation_done))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "generation_done"
        const val NOTIFICATION_ID = 1001
    }
}