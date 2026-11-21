package com.axora.app.ui.components

import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.axora.app.R
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import com.axora.app.ui.copyToClipboard
import com.axora.app.ui.theme.AxoraGradient
import java.io.File

@Composable
fun getGenerationTypeName(type: GenerationType): String = stringResource(
    when (type) {
        GenerationType.IMAGE -> R.string.type_image
        GenerationType.VIDEO -> R.string.type_video
        GenerationType.AUDIO -> R.string.type_audio
        GenerationType.TEXT -> R.string.type_text
    }
)

fun getGenerationIcon(type: GenerationType) = when (type) {
    GenerationType.IMAGE -> Icons.Rounded.Image
    GenerationType.VIDEO -> Icons.Rounded.VideoLibrary
    GenerationType.AUDIO -> Icons.Rounded.AudioFile
    GenerationType.TEXT -> Icons.Rounded.TextFields
}

// ========== TopBar ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    onExportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClearChatClick: () -> Unit,
    onGalleryClick: () -> Unit = {},
    galleryVisible: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        actions = {
            if (galleryVisible) {
                IconButton(onClick = onGalleryClick) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.cd_gallery),
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onExportClick) {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = stringResource(R.string.cd_export_chat),
                    tint = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClearChatClick) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.cd_clear_chat),
                    tint = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = scheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = scheme.surface,
            titleContentColor = scheme.onSurface,
            actionIconContentColor = scheme.onSurfaceVariant
        )
    )
}

@Composable
fun generationTypeColor(type: GenerationType): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (type) {
        GenerationType.IMAGE -> if (dark) Color(0xFFA78BFA) else Color(0xFF6D4AE0)
        GenerationType.TEXT -> if (dark) Color(0xFF4DB6AC) else Color(0xFF00796B)
        GenerationType.VIDEO -> if (dark) Color(0xFF7EA6F0) else Color(0xFF3E6FD9)
        GenerationType.AUDIO -> if (dark) Color(0xFFF08BB4) else Color(0xFFC2417F)
    }
}

@Composable
fun GenerationTabs(
    selectedType: GenerationType,
    onTypeSelected: (GenerationType) -> Unit,
    unavailableTypes: Set<GenerationType> = emptySet()
) {
    val scheme = MaterialTheme.colorScheme
    val tabs = listOf(GenerationType.IMAGE, GenerationType.TEXT, GenerationType.VIDEO, GenerationType.AUDIO)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
    ) {
        val tabWidth = ((maxWidth - 48.dp) / 4).coerceAtLeast(88.dp * fontScale)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { type ->
                val selected = type == selectedType
                val unavailable = type in unavailableTypes
                val accent = generationTypeColor(type)
                val shape = RoundedCornerShape(16.dp)
                Surface(
                    shape = shape,
                    color = if (selected) accent.copy(alpha = 0.14f) else scheme.surfaceContainerHigh,
                    border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.55f)) else null,
                    modifier = Modifier
                        .width(tabWidth)
                        .clip(shape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onTypeSelected(type) }
                        )
                ) {
                    Box {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = getGenerationIcon(type),
                                contentDescription = null,
                                tint = when {
                                    unavailable -> accent.copy(alpha = 0.4f)
                                    selected -> accent
                                    else -> accent.copy(alpha = 0.8f)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = getGenerationTypeName(type),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = when {
                                    unavailable -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    selected -> accent
                                    else -> scheme.onSurfaceVariant
                                },
                                maxLines = 1
                            )
                        }
                        if (unavailable) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = scheme.tertiaryContainer,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.tab_soon),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ========== Message Bubbles ==========

@Composable
fun MessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    onClick: () -> Unit,
    onImageClick: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onEditImage: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.isUser()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val bubbleColor = if (isUser) scheme.primaryContainer else scheme.surfaceContainerHigh
    val textColor = if (isUser) scheme.onPrimaryContainer else scheme.onSurface
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (message.text.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(bubbleShape)
                    .background(bubbleColor, bubbleShape)
                    .clickable { onClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.textContent ?: message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        }

        when (message.generationType) {
            GenerationType.IMAGE -> if (message.isImageMessage()) {
                Spacer(Modifier.height(6.dp))
                ImagePreview(message.imageUrl!!, onImageClick)
            }
            GenerationType.VIDEO -> if (message.isVideoMessage()) {
                Spacer(Modifier.height(6.dp))
                VideoPlayer(
                    file = File(message.videoUrl!!),
                    modifier = Modifier
                        .width(260.dp)
                        .height(200.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }
            GenerationType.AUDIO -> if (message.isAudioMessage()) {
                Spacer(Modifier.height(6.dp))
                AudioPlayer(file = File(message.audioUrl!!))
            }
            GenerationType.TEXT -> {}
        }

        if (!isUser && message.hasMedia()) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.action_save),
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (message.isImageMessage()) {
                    IconButton(
                        onClick = onEditImage,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.action_edit_image),
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (message.originalPrompt != null) {
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.action_regenerate),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (!isUser && message.generationType != GenerationType.TEXT && message.originalPrompt != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = message.originalPrompt,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (!isUser && message.isImageMessage()) {
            TextButton(
                onClick = { copyToClipboard(context, message.translatedPrompt ?: message.originalPrompt ?: message.text) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.copy_prompt),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary
                )
            }
        }

        if (message.isError) {
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(
                onClick = onRetry,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.retry), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ImagePreview(path: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val file = File(path)
    if (file.exists()) {
        val density = LocalDensity.current
        val widthPx = with(density) { 260.dp.roundToPx() }
        val heightPx = with(density) { 320.dp.roundToPx() }
        val model = ImageRequest.Builder(context)
            .data(file)
            .size(widthPx, heightPx)
            .crossfade(true)
            .build()
        Image(
            painter = rememberAsyncImagePainter(model),
            contentDescription = stringResource(R.string.cd_generated_image),
            modifier = Modifier
                .width(260.dp)
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { onClick() },
            contentScale = ContentScale.Fit
        )
    } else {
        MissingFilePlaceholder()
    }
}

@Composable
private fun MissingFilePlaceholder() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(260.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.ImageNotSupported,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.file_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VideoPlayer(file: File, modifier: Modifier = Modifier) {
    if (!file.exists()) {
        MissingFilePlaceholder()
        return
    }
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(file.absolutePath)
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { seekTo(1) }
            }
        },
        modifier = modifier,
        onRelease = { it.stopPlayback() }
    )
}

@Composable
private fun AudioPlayer(file: File) {
    if (!file.exists()) {
        MissingFilePlaceholder()
        return
    }
    val path = file.absolutePath
    var isPlaying by remember(path) { mutableStateOf(false) }
    var ready by remember(path) { mutableStateOf(false) }
    val player = remember(path) { MediaPlayer() }
    val scheme = MaterialTheme.colorScheme

    DisposableEffect(player) {
        runCatching {
            player.setDataSource(path)
            player.setOnPreparedListener { ready = true }
            player.setOnCompletionListener { isPlaying = false }
            player.setOnErrorListener { _, _, _ ->
                ready = false
                isPlaying = false
                true
            }
            player.prepareAsync()
        }
        onDispose { player.release() }
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.width(260.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = ready,
                onClick = {
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.start()
                        isPlaying = true
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                    tint = scheme.primary
                )
            }
            Text(
                stringResource(R.string.audio_label),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface
            )
        }
    }
}

@Composable
fun LoadingIndicator(onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = scheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.generating),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                stringResource(R.string.cancel_generation),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary
            )
        }
    }
}

@Composable
fun OfflineBanner() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FormatChips(
    selected: ImageFormat,
    onSelect: (ImageFormat) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImageFormat.entries.forEach { format ->
            FilterChip(
                selected = selected == format,
                onClick = { onSelect(format) },
                label = { Text(format.label, style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyChatPlaceholder(
    text: String,
    type: GenerationType,
    examples: List<String> = emptyList(),
    onExampleClick: (String) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(AxoraGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getGenerationIcon(type),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant
        )
        if (examples.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                examples.forEach { example ->
                    SuggestionChip(
                        onClick = { onExampleClick(example) },
                        label = {
                            Text(
                                text = example,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = scheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = scheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}