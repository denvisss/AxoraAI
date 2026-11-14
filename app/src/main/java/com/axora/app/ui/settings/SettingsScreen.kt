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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.axora.app.BuildConfig
import com.axora.app.R
import com.axora.app.data.repository.SettingsRepository
import com.axora.app.ui.theme.AxoraGradient
import com.axora.app.ui.viewmodel.SettingsViewModel
import com.axora.app.ui.viewmodel.UpdateStatus
import com.axora.app.ui.viewmodel.UpdateUiState
import java.io.File

private const val MODEL_KLEIN = "black-forest-labs/flux.2-klein-4b"
private const val MODEL_KONTEXT = "black-forest-labs/flux.1-kontext-pro"
private const val MODEL_FLUX_PRO = "black-forest-labs/flux.2-pro"
private const val TEXT_MODEL_QWEN = "qwen/qwen3guard-gen-8b"
private const val FEEDBACK_URL = "https://github.com/denvisss/AxoraAI/discussions/1"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    onLanguageChange: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showConnection by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.refresh() }

    BackHandler(enabled = showConnection) { showConnection = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (showConnection) R.string.connection_title else R.string.settings_title
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (showConnection) showConnection = false else onBack() }) {
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
            targetState = showConnection,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 6 })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 6 })
            },
            label = "settingsLevel"
        ) { connection ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                if (connection) {
                    ConnectionSettings(
                        apiKey = state.apiKey,
                        onApiKeySave = viewModel::setApiKey,
                        imageModel = state.imageModel,
                        onImageModelChange = viewModel::setImageModel,
                        textModel = state.textModel,
                        onTextModelChange = viewModel::setTextModel,
                        keyStorageEncrypted = state.keyStorageEncrypted
                    )
                } else {
                    MainSettings(
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
                        onOpenConnection = { showConnection = true }
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
    onOpenConnection: () -> Unit
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
                    text = stringResource(R.string.connection_subtitle),
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
            title = stringResource(R.string.about_site),
            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://pollinations.ai".toUri()))
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
                tint = scheme.primary,
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
    apiKey: String,
    onApiKeySave: (String) -> Unit,
    imageModel: String,
    onImageModelChange: (String) -> Unit,
    textModel: String,
    onTextModelChange: (String) -> Unit,
    keyStorageEncrypted: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var keyDraft by remember(apiKey) { mutableStateOf(apiKey) }
    val currentDraft by rememberUpdatedState(keyDraft)
    val currentSave by rememberUpdatedState(onApiKeySave)
    DisposableEffect(Unit) {
        onDispose { currentSave(currentDraft) }
    }

    val imageModels = listOf(
        ModelInfo(
            SettingsRepository.FREE_IMAGE_MODEL,
            stringResource(R.string.image_model_free_title),
            null,
            stringResource(R.string.image_model_free_desc)
        ),
        ModelInfo(MODEL_KLEIN, "Klein 4B", stringResource(R.string.price_klein), stringResource(R.string.image_model_klein_desc)),
        ModelInfo(MODEL_KONTEXT, "Kontext Pro", stringResource(R.string.price_kontext), stringResource(R.string.image_model_kontext_desc)),
        ModelInfo(MODEL_FLUX_PRO, "FLUX.2 Pro", stringResource(R.string.price_flux_pro), stringResource(R.string.image_model_flux_pro_desc))
    )
    val textModels = listOf(
        ModelInfo(SettingsRepository.DEFAULT_TEXT_MODEL, stringResource(R.string.text_model_openai_title), null, stringResource(R.string.text_model_openai_desc)),
        ModelInfo(TEXT_MODEL_QWEN, stringResource(R.string.text_model_qwen_title), null, stringResource(R.string.text_model_qwen_desc))
    )
    val apiRequired = imageModel != SettingsRepository.FREE_IMAGE_MODEL || textModel != SettingsRepository.DEFAULT_TEXT_MODEL

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(Icons.Rounded.Image)
            Column {
                Text(
                    text = stringResource(R.string.image_model_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    text = stringResource(R.string.image_model_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        imageModels.forEachIndexed { index, info ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            ModelOption(
                info = info,
                selected = imageModel == info.id,
                onClick = { onImageModelChange(info.id) }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(Icons.Rounded.SmartToy)
            Column {
                Text(
                    text = stringResource(R.string.model_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    text = stringResource(R.string.model_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        textModels.forEachIndexed { index, info ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            ModelOption(
                info = info,
                selected = textModel == info.id,
                onClick = { onTextModelChange(info.id) }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TonalIcon(Icons.Rounded.Key)
            Column {
                Text(
                    text = stringResource(R.string.api_key_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Text(
                    text = stringResource(R.string.api_key_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (apiRequired) {
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(R.string.api_key_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onApiKeySave(keyDraft) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outlineVariant,
                    focusedContainerColor = scheme.surfaceContainerHighest,
                    unfocusedContainerColor = scheme.surfaceContainerHighest,
                    cursorColor = scheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.api_key_save_hint),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            val selectedImage = imageModels.firstOrNull { it.id == imageModel && it.price != null }
            if (selectedImage != null) {
                SummaryLine(
                    Icons.Rounded.Image,
                    stringResource(R.string.api_summary_image, selectedImage.title, selectedImage.price.orEmpty())
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (textModel == TEXT_MODEL_QWEN) {
                SummaryLine(Icons.Rounded.SmartToy, stringResource(R.string.api_summary_text))
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (keyDraft.isBlank()) {
                Text(
                    text = stringResource(R.string.api_key_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.tertiary
                )
                if (imageModel != SettingsRepository.FREE_IMAGE_MODEL) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.api_free_fallback),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://enter.pollinations.ai/keys".toUri()
                    )
                    context.startActivity(intent)
                },
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
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.get_api_key),
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
        } else {
            Surface(
                color = scheme.surfaceContainerHighest,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.api_not_required_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.api_not_required_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class ModelInfo(
    val id: String,
    val title: String,
    val price: String?,
    val description: String
)

@Composable
private fun ModelOption(info: ModelInfo, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val freeBg = if (dark) Color(0xFF1C3A2A) else Color(0xFFDFF3E4)
    val freeFg = if (dark) Color(0xFF8FDCA8) else Color(0xFF146C3A)
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
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (info.price == null) freeBg else scheme.secondaryContainer
                    ) {
                        Text(
                            text = info.price ?: stringResource(R.string.badge_free),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (info.price == null) freeFg else scheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
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