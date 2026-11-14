package com.axora.app.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.axora.app.BuildConfig
import com.axora.app.R
import com.axora.app.data.repository.ChatRepository
import com.axora.app.ui.theme.AxoraGradient
import kotlinx.coroutines.launch

private const val MODEL_KLEIN = "black-forest-labs/flux.2-klein-4b"
private const val MODEL_KONTEXT = "black-forest-labs/flux.1-kontext-pro"
private const val MODEL_FLUX_PRO = "black-forest-labs/flux.2-pro"
private const val TEXT_MODEL_OPENAI = "openai"
private const val TEXT_MODEL_QWEN = "qwen/qwen3guard-gen-8b"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    repository: ChatRepository,
    darkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    var apiKey by remember { mutableStateOf(repository.getApiKey()) }
    var textModel by remember { mutableStateOf(repository.getTextModel()) }
    var imageModel by remember { mutableStateOf(repository.getImageModel()) }
    var showConnection by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

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
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it },
                        imageModel = imageModel,
                        onImageModelChange = { imageModel = it },
                        textModel = textModel,
                        onTextModelChange = { textModel = it }
                    )
                } else {
                    MainSettings(
                        repository = repository,
                        darkTheme = darkTheme,
                        onToggleDarkTheme = onToggleDarkTheme,
                        onOpenConnection = { showConnection = true }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    LaunchedEffect(apiKey) {
        repository.setApiKey(apiKey)
    }

    LaunchedEffect(textModel) {
        repository.setTextModel(textModel)
    }

    LaunchedEffect(imageModel) {
        repository.setImageModel(imageModel)
    }
}

@Composable
private fun MainSettings(
    repository: ChatRepository,
    darkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onOpenConnection: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var autoSave by remember { mutableStateOf(repository.isAutoSaveEnabled()) }
    var autoTranslate by remember { mutableStateOf(repository.isAutoTranslateEnabled()) }
    var mediaSize by remember { mutableLongStateOf(repository.generatedMediaSize()) }
    var showClearMediaDialog by remember { mutableStateOf(false) }
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
            checked = darkTheme,
            onCheckedChange = onToggleDarkTheme
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionLabel(stringResource(R.string.section_generation))
    SettingsCard {
        SwitchRow(
            icon = Icons.Rounded.SaveAlt,
            title = stringResource(R.string.auto_save_title),
            subtitle = stringResource(R.string.auto_save_subtitle),
            checked = autoSave,
            onCheckedChange = {
                autoSave = it
                repository.setAutoSaveEnabled(it)
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))
        SwitchRow(
            icon = Icons.Rounded.Translate,
            title = stringResource(R.string.auto_translate_title),
            subtitle = stringResource(R.string.auto_translate_subtitle),
            checked = autoTranslate,
            onCheckedChange = {
                autoTranslate = it
                repository.setAutoTranslateEnabled(it)
            }
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
            icon = Icons.Rounded.Public,
            title = stringResource(R.string.about_site),
            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pollinations.ai")))
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
                        scope.launch {
                            repository.clearGeneratedMedia()
                            mediaSize = repository.generatedMediaSize()
                            Toast.makeText(context, R.string.storage_cleared, Toast.LENGTH_SHORT).show()
                        }
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
    onClick: () -> Unit
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
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
    onApiKeyChange: (String) -> Unit,
    imageModel: String,
    onImageModelChange: (String) -> Unit,
    textModel: String,
    onTextModelChange: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    val imageModels = listOf(
        ModelInfo(
            ChatRepository.FREE_IMAGE_MODEL,
            stringResource(R.string.image_model_free_title),
            null,
            stringResource(R.string.image_model_free_desc)
        ),
        ModelInfo(MODEL_KLEIN, "Klein 4B", stringResource(R.string.price_klein), stringResource(R.string.image_model_klein_desc)),
        ModelInfo(MODEL_KONTEXT, "Kontext Pro", stringResource(R.string.price_kontext), stringResource(R.string.image_model_kontext_desc)),
        ModelInfo(MODEL_FLUX_PRO, "FLUX.2 Pro", stringResource(R.string.price_flux_pro), stringResource(R.string.image_model_flux_pro_desc))
    )
    val textModels = listOf(
        ModelInfo(TEXT_MODEL_OPENAI, stringResource(R.string.text_model_openai_title), null, stringResource(R.string.text_model_openai_desc)),
        ModelInfo(TEXT_MODEL_QWEN, stringResource(R.string.text_model_qwen_title), null, stringResource(R.string.text_model_qwen_desc))
    )
    val apiRequired = imageModel != ChatRepository.FREE_IMAGE_MODEL || textModel != TEXT_MODEL_OPENAI

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
                value = apiKey,
                onValueChange = onApiKeyChange,
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outlineVariant,
                    focusedContainerColor = scheme.surfaceContainerHighest,
                    unfocusedContainerColor = scheme.surfaceContainerHighest,
                    cursorColor = scheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
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
            if (apiKey.isBlank()) {
                Text(
                    text = stringResource(R.string.api_key_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.tertiary
                )
                if (imageModel != ChatRepository.FREE_IMAGE_MODEL) {
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
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://enter.pollinations.ai/keys")
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