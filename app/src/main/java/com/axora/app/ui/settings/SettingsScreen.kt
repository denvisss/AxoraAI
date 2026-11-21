package com.axora.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.axora.app.BuildConfig
import com.axora.app.R
import com.axora.app.data.network.ProxyEntry
import com.axora.app.data.repository.SettingsRepository
import com.axora.app.domain.model.ProxyConfig
import com.axora.app.ui.theme.AxoraGradient
import com.axora.app.ui.viewmodel.SettingsViewModel
import com.axora.app.ui.viewmodel.UpdateStatus
import com.axora.app.ui.viewmodel.UpdateUiState
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val FEEDBACK_URL = "https://github.com/denvisss/AxoraAI/discussions/1"
private const val REPO_URL = "https://github.com/denvisss/AxoraAI"
private const val BOOSTY_URL = "https://boosty.to/denvisss/donate"
private const val GROQ_KEYS_URL = "https://console.groq.com/keys"
private const val CLOUDFLARE_SIGNUP_URL = "https://dash.cloudflare.com/sign-up"
private const val CLOUDFLARE_TOKENS_URL = "https://dash.cloudflare.com/profile/api-tokens"
private const val PROXY_AUTO_REFRESH_MS = 5 * 60 * 1000L
private const val PROXY_AUTO_TEST_TIMEOUT_MS = 6_000L
private const val PROXY_AUTO_TEST_CONCURRENCY = 6

private val BoostyIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Boosty",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color(0xFFF15F2C))) {
            moveTo(8.25f, 3f)
            lineTo(11.25f, 3f)
            lineTo(11.25f, 20f)
            lineTo(8.25f, 20f)
            close()
        }
        path(fill = SolidColor(Color(0xFFF15F2C)), pathFillType = PathFillType.EvenOdd) {
            moveTo(19f, 15f)
            arcTo(5f, 5f, 0f, false, true, 14f, 20f)
            arcTo(5f, 5f, 0f, false, true, 9f, 15f)
            arcTo(5f, 5f, 0f, false, true, 14f, 10f)
            arcTo(5f, 5f, 0f, false, true, 19f, 15f)
            close()
            moveTo(16.25f, 15f)
            arcTo(2.25f, 2.25f, 0f, false, true, 14f, 17.25f)
            arcTo(2.25f, 2.25f, 0f, false, true, 11.75f, 15f)
            arcTo(2.25f, 2.25f, 0f, false, true, 14f, 12.75f)
            arcTo(2.25f, 2.25f, 0f, false, true, 16.25f, 15f)
            close()
        }
    }.build()
}

private enum class SettingsLevel { Main, Connection, Proxy }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    onLanguageChange: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var level by rememberSaveable { mutableStateOf(SettingsLevel.Main) }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.refresh() }

    BackHandler(enabled = level != SettingsLevel.Main) { level = SettingsLevel.Main }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when (level) {
                                SettingsLevel.Connection -> R.string.connection_title
                                SettingsLevel.Proxy -> R.string.proxy_title
                                SettingsLevel.Main -> R.string.settings_title
                            }
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (level != SettingsLevel.Main) level = SettingsLevel.Main else onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        },
        containerColor = scheme.background
    ) { paddingValues ->
        AnimatedContent(
            targetState = level,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 6 })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 6 })
            },
            label = "settingsLevel"
        ) { currentLevel ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                when (currentLevel) {
                    SettingsLevel.Connection -> ConnectionSettings(
                        groqApiKey = state.groqApiKey,
                        onGroqKeySave = viewModel::setGroqApiKey,
                        cfAccountId = state.cfAccountId,
                        onCfAccountIdSave = viewModel::setCfAccountId,
                        cfApiToken = state.cfApiToken,
                        onCfApiTokenSave = viewModel::setCfApiToken,
                        keyStorageEncrypted = state.keyStorageEncrypted
                    )
                    SettingsLevel.Proxy -> ProxySection(
                        proxy = state.proxy,
                        onSetProxy = viewModel::setProxy,
                        onLoadList = viewModel::loadProxyList,
                        onTest = viewModel::testProxy
                    )
                    SettingsLevel.Main -> MainSettings(
                        autoSave = state.autoSave,
                        onAutoSaveChange = viewModel::setAutoSave,
                        autoTranslate = state.autoTranslate,
                        onAutoTranslateChange = viewModel::setAutoTranslate,
                        darkTheme = state.darkTheme,
                        onToggleDarkTheme = viewModel::setDarkTheme,
                        language = state.language,
                        onLanguageChange = onLanguageChange,
                        update = state.update,
                        updateTag = state.availableUpdateTag,
                        onCheckUpdate = viewModel::checkForUpdate,
                        onDownloadUpdate = viewModel::downloadUpdate,
                        mediaSize = state.mediaSize,
                        onClearMedia = viewModel::clearMedia,
                        allKeysConnected = state.groqApiKey.isNotBlank() &&
                            state.cfAccountId.isNotBlank() &&
                            state.cfApiToken.isNotBlank(),
                        onOpenConnection = { level = SettingsLevel.Connection },
                        onOpenProxy = { level = SettingsLevel.Proxy }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MainSettings(
    autoSave: Boolean,
    onAutoSaveChange: (Boolean) -> Unit,
    autoTranslate: Boolean,
    onAutoTranslateChange: (Boolean) -> Unit,
    darkTheme: Boolean?,
    onToggleDarkTheme: (Boolean) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    update: UpdateUiState,
    updateTag: String,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    mediaSize: Long,
    onClearMedia: () -> Unit,
    allKeysConnected: Boolean,
    onOpenConnection: () -> Unit,
    onOpenProxy: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    var showClearMediaDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.about_share)
    val shareText = stringResource(R.string.about_share_text)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AxoraGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Text(
                text = stringResource(R.string.footer),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SectionLabel(stringResource(R.string.section_connection))
    Surface(
        onClick = onOpenConnection,
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(Icons.Rounded.Key)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.connection_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (allKeysConnected) R.string.connection_all_set else R.string.connection_subtitle
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        onClick = onOpenProxy,
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(Icons.Rounded.Lan)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.proxy_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    text = stringResource(R.string.proxy_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionLabel(stringResource(R.string.section_app))
    SettingsCard {
        SwitchRow(
            icon = Icons.Rounded.WbSunny,
            title = stringResource(R.string.dark_theme_title),
            subtitle = stringResource(R.string.dark_theme_subtitle),
            checked = darkTheme ?: systemDark,
            onCheckedChange = onToggleDarkTheme
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            onClick = { showLanguageDialog = true },
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TonalIcon(Icons.Rounded.Language)
                    Column {
                        Text(
                            text = stringResource(R.string.language_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                if (language == SettingsRepository.DEFAULT_LANGUAGE) {
                                    R.string.language_ru
                                } else {
                                    R.string.language_en
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionLabel(stringResource(R.string.section_generation))
    SettingsCard {
        SwitchRow(
            icon = Icons.Rounded.SaveAlt,
            title = stringResource(R.string.auto_save_title),
            subtitle = stringResource(R.string.auto_save_subtitle),
            checked = autoSave,
            onCheckedChange = onAutoSaveChange
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))
        SwitchRow(
            icon = Icons.Rounded.Translate,
            title = stringResource(R.string.auto_translate_title),
            subtitle = stringResource(R.string.auto_translate_subtitle),
            checked = autoTranslate,
            onCheckedChange = onAutoTranslateChange
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionLabel(stringResource(R.string.section_storage))
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TonalIcon(Icons.Rounded.CleaningServices)
                Column {
                    Text(
                        text = stringResource(R.string.storage_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = if (mediaSize > 0) {
                            stringResource(R.string.storage_used, Formatter.formatFileSize(context, mediaSize))
                        } else {
                            stringResource(R.string.storage_empty)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = { showClearMediaDialog = true },
                enabled = mediaSize > 0
            ) {
                Text(
                    text = stringResource(R.string.storage_clear),
                    color = if (mediaSize > 0) scheme.error else scheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionLabel(stringResource(R.string.section_about))
    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(Icons.Rounded.Info)
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinkRow(
            icon = Icons.Rounded.SystemUpdateAlt,
            title = stringResource(R.string.update_title),
            subtitle = (if (update.status == UpdateStatus.Available) update.version else updateTag)
                .takeIf { it.isNotEmpty() }
                ?.let { stringResource(R.string.update_available, it) },
            trailingIcon = Icons.Rounded.ChevronRight,
            onClick = {
                val status = update.status
                if (status == UpdateStatus.Idle || status == UpdateStatus.UpToDate || status == UpdateStatus.Error) {
                    onCheckUpdate()
                }
                showUpdateDialog = true
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinkRow(
            icon = Icons.Rounded.Public,
            title = stringResource(R.string.about_repo),
            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri()))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinkRow(
            icon = Icons.Rounded.Share,
            title = shareTitle,
            trailingIcon = Icons.Rounded.ChevronRight,
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, shareTitle))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinkRow(
            icon = Icons.Rounded.Forum,
            title = stringResource(R.string.about_feedback),
            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, FEEDBACK_URL.toUri()))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinkRow(
            icon = BoostyIcon,
            iconTint = Color(0xFFF15F2C),
            title = stringResource(R.string.about_support),
            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, BOOSTY_URL.toUri()))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_license),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
    }

    if (showClearMediaDialog) {
        AlertDialog(
            onDismissRequest = { showClearMediaDialog = false },
            title = { Text(stringResource(R.string.storage_clear_title)) },
            text = { Text(stringResource(R.string.storage_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearMediaDialog = false
                        onClearMedia()
                        Toast.makeText(context, R.string.storage_cleared, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.storage_clear), color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMediaDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column {
                    LanguageOption(
                        title = stringResource(R.string.language_ru),
                        selected = language == SettingsRepository.DEFAULT_LANGUAGE,
                        onClick = {
                            showLanguageDialog = false
                            onLanguageChange(SettingsRepository.DEFAULT_LANGUAGE)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LanguageOption(
                        title = stringResource(R.string.language_en),
                        selected = language == SettingsRepository.LANGUAGE_EN,
                        onClick = {
                            showLanguageDialog = false
                            onLanguageChange(SettingsRepository.LANGUAGE_EN)
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(stringResource(R.string.update_title)) },
            text = {
                when (update.status) {
                    UpdateStatus.Checking, UpdateStatus.Idle -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(stringResource(R.string.update_checking))
                    }
                    UpdateStatus.UpToDate -> Text(
                        stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME)
                    )
                    UpdateStatus.Available -> Column {
                        Text(stringResource(R.string.update_available, update.version))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.update_available_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    UpdateStatus.Downloading -> Column {
                        Text(
                            if (update.progress > 0) {
                                stringResource(R.string.update_download, update.progress)
                            } else {
                                stringResource(R.string.update_download_indeterminate)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { update.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    UpdateStatus.Ready -> Text(stringResource(R.string.update_ready))
                    UpdateStatus.Error -> Text(
                        update.error ?: stringResource(R.string.update_error_check)
                    )
                }
            },
            confirmButton = {
                when (update.status) {
                    UpdateStatus.Available -> TextButton(onClick = onDownloadUpdate) {
                        Text(stringResource(R.string.update_action_download))
                    }
                    UpdateStatus.Ready -> TextButton(
                        onClick = {
                            update.filePath?.let { installUpdate(context, File(it)) }
                        }
                    ) {
                        Text(stringResource(R.string.update_install))
                    }
                    UpdateStatus.Error -> TextButton(onClick = onCheckUpdate) {
                        Text(stringResource(R.string.update_retry))
                    }
                    else -> {}
                }
            },
            dismissButton = {
                if (update.status != UpdateStatus.Checking && update.status != UpdateStatus.Downloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text(
                            stringResource(
                                if (update.status == UpdateStatus.UpToDate) R.string.update_ok else R.string.update_later
                            )
                        )
                    }
                }
            }
        )
    }
}

private fun installUpdate(context: android.content.Context, file: File) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(context, R.string.update_allow_sources, Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun LanguageOption(title: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primary.copy(alpha = 0.10f) else scheme.surfaceContainerHighest,
        border = if (selected) {
            BorderStroke(1.5.dp, scheme.primary.copy(alpha = 0.65f))
        } else {
            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.outlineVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(icon)
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    trailingIcon: ImageVector,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.primary
                    )
                }
            }
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ConnectionSettings(
    groqApiKey: String,
    onGroqKeySave: (String) -> Unit,
    cfAccountId: String,
    onCfAccountIdSave: (String) -> Unit,
    cfApiToken: String,
    onCfApiTokenSave: (String) -> Unit,
    keyStorageEncrypted: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var groqDraft by remember(groqApiKey) { mutableStateOf(groqApiKey) }
    var accountDraft by remember(cfAccountId) { mutableStateOf(cfAccountId) }
    var tokenDraft by remember(cfApiToken) { mutableStateOf(cfApiToken) }
    val currentGroqDraft by rememberUpdatedState(groqDraft)
    val currentAccountDraft by rememberUpdatedState(accountDraft)
    val currentTokenDraft by rememberUpdatedState(tokenDraft)
    val saveGroqKey by rememberUpdatedState(onGroqKeySave)
    val saveAccountId by rememberUpdatedState(onCfAccountIdSave)
    val saveCfToken by rememberUpdatedState(onCfApiTokenSave)
    DisposableEffect(Unit) {
        onDispose {
            saveGroqKey(currentGroqDraft)
            saveAccountId(currentAccountDraft)
            saveCfToken(currentTokenDraft)
        }
    }

    if (!keyStorageEncrypted) {
        Surface(
            color = scheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = scheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.key_storage_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onErrorContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    SettingsCard {
        ProviderHeader(
            icon = Icons.Rounded.SmartToy,
            title = stringResource(R.string.provider_groq_title),
            subtitle = stringResource(R.string.provider_groq_subtitle),
            connected = groqApiKey.isNotBlank()
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsField(
            label = stringResource(R.string.groq_key_label),
            value = groqDraft,
            placeholder = stringResource(R.string.groq_key_placeholder),
            hideText = true,
            onValueChange = { groqDraft = it }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.api_key_save_hint),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        SummaryLine(Icons.Rounded.SmartToy, stringResource(R.string.groq_limits))
        Spacer(modifier = Modifier.height(12.dp))
        GradientButton(
            icon = Icons.Rounded.Key,
            text = stringResource(R.string.get_groq_key),
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, GROQ_KEYS_URL.toUri()))
            }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    SettingsCard {
        ProviderHeader(
            icon = Icons.Rounded.Image,
            title = stringResource(R.string.provider_cf_title),
            subtitle = stringResource(R.string.provider_cf_subtitle),
            connected = cfAccountId.isNotBlank() && cfApiToken.isNotBlank()
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsField(
            label = stringResource(R.string.cf_account_label),
            value = accountDraft,
            placeholder = stringResource(R.string.cf_account_hint),
            hideText = false,
            onValueChange = { accountDraft = it }
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsField(
            label = stringResource(R.string.cf_token_label),
            value = tokenDraft,
            placeholder = stringResource(R.string.cf_token_placeholder),
            hideText = true,
            onValueChange = { tokenDraft = it }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.api_key_save_hint),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        SummaryLine(Icons.Rounded.Image, stringResource(R.string.cf_limits))
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, CLOUDFLARE_SIGNUP_URL.toUri()))
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cf_signup),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                }
            }
            Surface(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, CLOUDFLARE_TOKENS_URL.toUri()))
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AxoraGradient)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.get_cf_token),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxySection(
    proxy: ProxyConfig?,
    onSetProxy: (ProxyConfig?) -> Unit,
    onLoadList: suspend (String) -> List<ProxyEntry>,
    onTest: suspend (ProxyConfig, Long) -> Result<Long>
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    var protocol by rememberSaveable { mutableStateOf(ProxyConfig.SCHEMES.first()) }
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var entries by remember(protocol) { mutableStateOf<List<ProxyEntry>?>(null) }
    var loading by remember(protocol) { mutableStateOf(true) }
    var loadError by remember(protocol) { mutableStateOf(false) }
    var visibleCount by rememberSaveable(protocol) { mutableIntStateOf(10) }
    val results = remember(protocol) { mutableStateMapOf<String, Long>() }
    val failed = remember(protocol) { mutableStateSetOf<String>() }
    val testingKeys = remember(protocol) { mutableStateSetOf<String>() }

    var ownScheme by rememberSaveable { mutableStateOf(ProxyConfig.SCHEMES.first()) }
    var hostDraft by rememberSaveable { mutableStateOf("") }
    var portDraft by rememberSaveable { mutableStateOf("") }
    val portValue = portDraft.trim().toIntOrNull()
    val ownValid = hostDraft.isNotBlank() && portValue != null && portValue in 1..65535

    var testingCurrent by remember { mutableStateOf(false) }
    var currentTest by remember { mutableStateOf<Result<Long>?>(null) }

    LaunchedEffect(protocol, refreshKey) {
        loading = true
        loadError = false
        entries = try {
            onLoadList(protocol)
        } catch (_: Exception) {
            loadError = true
            null
        }
        loading = false
    }

    LaunchedEffect(protocol, refreshKey, entries) {
        val list = entries ?: return@LaunchedEffect
        results.clear()
        failed.clear()
        testingKeys.clear()
        val semaphore = Semaphore(PROXY_AUTO_TEST_CONCURRENCY)
        coroutineScope {
            list.forEach { entry ->
                launch {
                    semaphore.withPermit {
                        val key = "${entry.ip}:${entry.port}"
                        testingKeys.add(key)
                        val config = ProxyConfig(
                            enabled = true,
                            scheme = entry.protocol.ifBlank { protocol },
                            host = entry.ip,
                            port = entry.port
                        )
                        onTest(config, PROXY_AUTO_TEST_TIMEOUT_MS)
                            .onSuccess { results[key] = it }
                            .onFailure { failed.add(key) }
                        testingKeys.remove(key)
                    }
                }
            }
        }
    }

    LaunchedEffect(protocol) {
        while (true) {
            delay(PROXY_AUTO_REFRESH_MS)
            refreshKey++
        }
    }

    val connect: (ProxyEntry) -> Unit = { entry ->
        val key = "${entry.ip}:${entry.port}"
        val config = ProxyConfig(
            enabled = true,
            scheme = entry.protocol.ifBlank { protocol },
            host = entry.ip,
            port = entry.port
        )
        if (key !in testingKeys) {
            scope.launch {
                val latency = results[key] ?: run {
                    testingKeys.add(key)
                    val attempt = onTest(config, PROXY_AUTO_TEST_TIMEOUT_MS)
                    testingKeys.remove(key)
                    attempt.getOrNull()?.also { results[key] = it } ?: run {
                        failed.add(key)
                        return@launch
                    }
                }
                onSetProxy(config)
                Toast.makeText(
                    context,
                    resources.getString(R.string.proxy_connected_toast, latency),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    SettingsCard {
        ProviderHeader(
            icon = Icons.Rounded.Lan,
            title = stringResource(R.string.proxy_title),
            subtitle = stringResource(R.string.proxy_subtitle),
            connected = proxy?.enabled == true
        )
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = scheme.tertiaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = scheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.proxy_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onTertiaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SwitchRow(
            icon = Icons.Rounded.Lan,
            title = stringResource(R.string.proxy_enable),
            subtitle = stringResource(R.string.proxy_enable_subtitle),
            checked = proxy?.enabled == true,
            onCheckedChange = { enabled ->
                proxy?.let { onSetProxy(it.copy(enabled = enabled)) }
            }
        )
        if (proxy != null) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TonalIcon(Icons.Rounded.Public)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = proxy.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            if (proxy.enabled) R.string.proxy_enable_subtitle else R.string.proxy_disabled_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        scope.launch {
                            testingCurrent = true
                            currentTest = null
                            currentTest = onTest(proxy, 10_000L)
                            testingCurrent = false
                        }
                    },
                    enabled = !testingCurrent
                ) {
                    Text(stringResource(R.string.proxy_test))
                }
                TextButton(onClick = {
                    currentTest = null
                    onSetProxy(null)
                }) {
                    Text(
                        text = stringResource(R.string.proxy_remove),
                        color = scheme.error
                    )
                }
            }
            val testResult = currentTest
            when {
                testingCurrent -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.proxy_testing),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                testResult != null -> testResult.fold(
                    onSuccess = { ms ->
                        Text(
                            text = stringResource(R.string.proxy_ok, ms),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.primary
                        )
                    },
                    onFailure = {
                        Text(
                            text = stringResource(R.string.proxy_fail),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error
                        )
                    }
                )
            }
        } else {
            Text(
                text = stringResource(R.string.proxy_none_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    SettingsCard {
        Text(
            text = stringResource(R.string.proxy_own_title),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProxyConfig.SCHEMES.forEach { candidate ->
                FilterChip(
                    selected = ownScheme == candidate,
                    onClick = { ownScheme = candidate },
                    label = { Text(candidate.uppercase()) }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsField(
                label = stringResource(R.string.proxy_host_label),
                value = hostDraft,
                placeholder = stringResource(R.string.proxy_host_placeholder),
                hideText = false,
                onValueChange = { hostDraft = it },
                modifier = Modifier.weight(1f)
            )
            SettingsField(
                label = stringResource(R.string.proxy_port_label),
                value = portDraft,
                placeholder = stringResource(R.string.proxy_port_placeholder),
                hideText = false,
                keyboardType = KeyboardType.Number,
                onValueChange = { portDraft = it },
                modifier = Modifier.weight(0.6f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(
            onClick = {
                val port = portDraft.trim().toIntOrNull() ?: return@FilledTonalButton
                onSetProxy(
                    ProxyConfig(
                        enabled = proxy?.enabled ?: true,
                        scheme = ownScheme,
                        host = hostDraft.trim(),
                        port = port
                    )
                )
                hostDraft = ""
                portDraft = ""
                Toast.makeText(context, R.string.proxy_saved, Toast.LENGTH_SHORT).show()
            },
            enabled = ownValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.proxy_save))
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(Icons.Rounded.Public)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.proxy_free_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    text = stringResource(R.string.proxy_free_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refreshKey++ }, enabled = !loading) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.proxy_refresh),
                    tint = scheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProxyConfig.SCHEMES.forEach { candidate ->
                FilterChip(
                    selected = protocol == candidate,
                    onClick = { protocol = candidate },
                    label = { Text(candidate.uppercase()) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val list = entries
        val okList = list.orEmpty()
            .filter { results.containsKey("${it.ip}:${it.port}") }
            .sortedBy { results["${it.ip}:${it.port}"] }
        val pendingList = list.orEmpty().filter {
            val key = "${it.ip}:${it.port}"
            !results.containsKey(key) && key !in failed
        }
        val display = okList + pendingList
        when {
            loadError -> Text(
                text = stringResource(R.string.proxy_load_fail),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error
            )
            list == null && loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.proxy_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            list == null -> {}
            else -> {
                Text(
                    text = stringResource(R.string.proxy_count, list.size, okList.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                if (testingKeys.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.proxy_auto_testing),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                display.take(visibleCount).forEach { entry ->
                    val key = "${entry.ip}:${entry.port}"
                    ProxyRow(
                        entry = entry,
                        fallbackProtocol = protocol,
                        testing = key in testingKeys,
                        latency = results[key],
                        onConnect = { connect(entry) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (display.size > visibleCount) {
                    TextButton(onClick = { visibleCount += 20 }) {
                        Text(stringResource(R.string.proxy_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyRow(
    entry: ProxyEntry,
    fallbackProtocol: String,
    testing: Boolean,
    latency: Long?,
    onConnect: (ProxyConfig) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = {
            onConnect(
                ProxyConfig(
                    enabled = true,
                    scheme = entry.protocol.ifBlank { fallbackProtocol },
                    host = entry.ip,
                    port = entry.port
                )
            )
        },
        enabled = !testing,
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.ip}:${entry.port}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                val geo = listOfNotNull(
                    entry.country?.takeIf { it.isNotBlank() && it != "Unknown" },
                    entry.city?.takeIf { it.isNotBlank() && it != "Unknown" },
                    entry.anonymity?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (geo.isNotEmpty()) {
                    Text(
                        text = geo,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            when {
                testing -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                latency != null -> {
                    val dark = scheme.surface.luminance() < 0.5f
                    val pingColor = when {
                        latency < 300 -> if (dark) Color(0xFF8FDCA8) else Color(0xFF146C3A)
                        latency < 1000 -> if (dark) Color(0xFFF2C979) else Color(0xFF8A5A00)
                        else -> scheme.error
                    }
                    Text(
                        text = stringResource(R.string.proxy_latency_ms, latency),
                        style = MaterialTheme.typography.labelLarge,
                        color = pingColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderHeader(icon: ImageVector, title: String, subtitle: String, connected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TonalIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusChip(connected)
    }
}

@Composable
private fun StatusChip(connected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val bg = if (connected) {
        if (dark) Color(0xFF1C3A2A) else Color(0xFFDFF3E4)
    } else {
        if (dark) Color(0xFF3A2E1C) else Color(0xFFF7ECD8)
    }
    val fg = if (connected) {
        if (dark) Color(0xFF8FDCA8) else Color(0xFF146C3A)
    } else {
        if (dark) Color(0xFFF2C979) else Color(0xFF8A5A00)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.Key,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = stringResource(
                    if (connected) R.string.status_connected else R.string.status_needs_setup
                ),
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    placeholder: String,
    hideText: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        },
        singleLine = true,
        visualTransformation = if (hideText) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (hideText) KeyboardType.Password else keyboardType,
            imeAction = ImeAction.Done
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outlineVariant,
            focusedContainerColor = scheme.surfaceContainerHighest,
            unfocusedContainerColor = scheme.surfaceContainerHighest,
            cursorColor = scheme.primary
        ),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun GradientButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AxoraGradient)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(icon: ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun TonalIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}