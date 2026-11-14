package com.axora.app.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.axora.app.R
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationType
import com.axora.app.ui.components.*
import com.axora.app.ui.theme.AxoraGradient
import com.axora.app.ui.theme.AxoraBlue
import com.axora.app.ui.theme.AxoraViolet
import com.axora.app.ui.viewmodel.ChatViewModel
import com.axora.app.util.SaveImageUtil
import com.axora.app.util.VoiceInputHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoonVideo = stringResource(R.string.coming_soon_video)
    val comingSoonAudio = stringResource(R.string.coming_soon_audio)
    val isOffline = rememberIsOffline()

    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var editImageMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editImageText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ChatMessage?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    var isListening by remember { mutableStateOf(false) }
    val activity = context as Activity
    val voiceHelper = remember { VoiceInputHelper(activity) }

    val noMicPermission = stringResource(R.string.no_mic_permission)
    val noStoragePermission = stringResource(R.string.no_storage_permission)
    val voiceUnavailable = stringResource(R.string.voice_unavailable)

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, noMicPermission, Toast.LENGTH_SHORT).show()
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val msg = pendingSave
        pendingSave = null
        if (granted && msg != null) performSave(context, scope, msg)
        else Toast.makeText(context, noStoragePermission, Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(voiceHelper) {
        voiceHelper.initialize(object : VoiceInputHelper.VoiceInputListener {
            override fun onRecordingStarted() { isListening = true }
            override fun onSpeechResult(text: String) {
                isListening = false
                viewModel.handleVoiceResult(text)
            }
            override fun onError(message: String) {
                isListening = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onRecordingStopped() { isListening = false }
        })
        onDispose { voiceHelper.destroy() }
    }

    fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!voiceHelper.isAvailable()) {
            Toast.makeText(context, voiceUnavailable, Toast.LENGTH_SHORT).show()
            return
        }
        isListening = true
        voiceHelper.startListening()
    }

    fun stopVoiceInput() {
        voiceHelper.stopListening()
        isListening = false
    }

    fun saveMedia(message: ChatMessage) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = message
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        performSave(context, scope, message)
    }

    val imageMessages = uiState.messages.filter { it.isImageMessage() }

    val lastMessageId = uiState.messages.lastOrNull()?.id
    val bannerVisible = uiState.error != null && uiState.errorType == uiState.selectedType
    LaunchedEffect(lastMessageId, uiState.isGenerating, uiState.selectedType, bannerVisible) {
        val count = uiState.messages.size + if (uiState.isGenerating) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            Column {
                ChatTopBar(
                    onSettingsClick = onSettingsClick,
                    onClearChatClick = { showClearDialog = true }
                )
                GenerationTabs(
                    selectedType = uiState.selectedType,
                    unavailableTypes = setOf(GenerationType.VIDEO, GenerationType.AUDIO),
                    onTypeSelected = { type ->
                        when (type) {
                            GenerationType.VIDEO -> scope.launch { snackbarHostState.showSnackbar(comingSoonVideo) }
                            GenerationType.AUDIO -> scope.launch { snackbarHostState.showSnackbar(comingSoonAudio) }
                            else -> viewModel.setGenerationType(type)
                        }
                    }
                )
            }
        },
        containerColor = scheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    EmptyChatPlaceholder(
                        text = stringResource(
                            when (uiState.selectedType) {
                                GenerationType.IMAGE -> R.string.empty_image
                                GenerationType.VIDEO -> R.string.empty_video
                                GenerationType.AUDIO -> R.string.empty_audio
                                GenerationType.TEXT -> R.string.empty_text
                            }
                        ),
                        type = uiState.selectedType
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                onRetry = { viewModel.retry(message) },
                                onClick = { actionMessage = message },
                                onImageClick = {
                                    val index = imageMessages.indexOf(message)
                                    if (index >= 0) viewerIndex = index
                                },
                                onDownload = { saveMedia(message) },
                                onShare = { shareMedia(context, message) },
                                onEditImage = {
                                    editImageMessage = message
                                    editImageText = ""
                                },
                                onRegenerate = { viewModel.regenerate(message) }
                            )
                        }
                        if (uiState.isGenerating) {
                            item { LoadingIndicator(onCancel = { viewModel.cancelGeneration() }) }
                        }
                    }
                }
            }

            if (isOffline) {
                OfflineBanner()
            }

            if (uiState.errorType == uiState.selectedType) {
                uiState.error?.let { error ->
                    ErrorBanner(error = error, onDismiss = { viewModel.clearError() })
                }
            }

            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    shape = RoundedCornerShape(18.dp),
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RocketLaunch,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (uiState.selectedType == GenerationType.IMAGE) {
                FormatChips(
                    selected = uiState.imageFormat,
                    onSelect = viewModel::setImageFormat
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                AxoraBlue.copy(alpha = 0.45f),
                                AxoraViolet.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            )

            BottomInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                onStartVoice = { startVoiceInput() },
                onStopVoice = { stopVoiceInput() },
                isGenerating = uiState.isGenerating,
                isListening = isListening
            )
        }
    }

    actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            title = { Text(getGenerationTypeName(message.generationType)) },
            text = {
                Column {
                    if (message.hasMedia()) {
                        DialogAction(Icons.Rounded.Download, stringResource(R.string.action_save)) {
                            saveMedia(message); actionMessage = null
                        }
                        DialogAction(Icons.Rounded.Share, stringResource(R.string.action_share)) {
                            shareMedia(context, message); actionMessage = null
                        }
                        if (message.originalPrompt != null) {
                            DialogAction(Icons.Rounded.Refresh, stringResource(R.string.action_regenerate)) {
                                viewModel.regenerate(message); actionMessage = null
                            }
                        }
                    }
                    if (message.text.isNotBlank()) {
                        DialogAction(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy)) {
                            copyToClipboard(context, message.textContent ?: message.text)
                            actionMessage = null
                        }
                    }
                    if (message.isImageMessage()) {
                        DialogAction(Icons.Rounded.AutoAwesome, stringResource(R.string.action_edit_image)) {
                            editImageMessage = message
                            editImageText = ""
                            actionMessage = null
                        }
                    }
                    if (message.isUser()) {
                        DialogAction(Icons.Rounded.Edit, stringResource(R.string.action_edit)) {
                            editMessage = message
                            editText = message.text
                            actionMessage = null
                        }
                    }
                    DialogAction(Icons.Rounded.Delete, stringResource(R.string.action_delete)) {
                        viewModel.deleteMessage(message.id)
                        actionMessage = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionMessage = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_chat_title)) },
            text = { Text(stringResource(R.string.clear_chat_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearChat(); showClearDialog = false }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    editMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { editMessage = null },
            title = { Text(stringResource(R.string.edit_title)) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.editMessage(message.id, editText)
                    editMessage = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editMessage = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    editImageMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { editImageMessage = null },
            title = { Text(stringResource(R.string.edit_image_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editImageText,
                        onValueChange = { editImageText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.edit_image_hint)) },
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.edit_image_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editImage(message, editImageText)
                        editImageMessage = null
                    },
                    enabled = editImageText.isNotBlank()
                ) { Text(stringResource(R.string.action_edit)) }
            },
            dismissButton = {
                TextButton(onClick = { editImageMessage = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    viewerIndex?.let { index ->
        ImageViewerDialog(
            messages = imageMessages,
            initialIndex = index,
            onDismiss = { viewerIndex = null },
            onSave = { saveMedia(it) },
            onShare = { shareMedia(context, it) }
        )
    }
}

@Composable
private fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var offline by remember { mutableStateOf(!connectivityManager.isOnline()) }
    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { offline = false }
            override fun onLost(network: Network) { offline = !connectivityManager.isOnline() }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return offline
}

private fun ConnectivityManager.isOnline(): Boolean {
    val network = activeNetwork ?: return false
    return getNetworkCapabilities(network)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}

private fun mediaFile(context: Context, message: ChatMessage): File? {
    val path = message.imageUrl ?: message.videoUrl ?: message.audioUrl ?: return null
    return File(path).takeIf { it.exists() } ?: run {
        Toast.makeText(context, context.getString(R.string.file_unavailable), Toast.LENGTH_SHORT).show()
        null
    }
}

private fun performSave(context: Context, scope: kotlinx.coroutines.CoroutineScope, message: ChatMessage) {
    val file = mediaFile(context, message) ?: return
    scope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) { SaveImageUtil.saveToGallery(context, file, message.generationType) }
        }
        if (result.isSuccess) {
            Toast.makeText(context, R.string.saved_to_gallery, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.save_error, result.exceptionOrNull()?.message.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private fun shareMedia(context: Context, message: ChatMessage) {
    val file = mediaFile(context, message) ?: return
    if (!SaveImageUtil.share(context, file, message.generationType)) {
        Toast.makeText(context, R.string.share_error, Toast.LENGTH_SHORT).show()
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Axora", text))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

@Composable
private fun DialogAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(label)
    }
}

@Composable
fun BottomInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    isGenerating: Boolean,
    isListening: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = scheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VoiceButton(
                isListening = isListening,
                isGenerating = isGenerating,
                onStartVoice = onStartVoice,
                onStopVoice = onStopVoice
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.input_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .alpha(if (isGenerating) 0.5f else 1f)
                            .clip(CircleShape)
                            .background(AxoraGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                },
                maxLines = 4,
                enabled = !isGenerating,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = scheme.surfaceContainerHigh,
                    unfocusedContainerColor = scheme.surfaceContainerHigh,
                    cursorColor = scheme.primary
                ),
                shape = RoundedCornerShape(28.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface)
            )

            AnimatedVisibility(
                visible = value.isNotBlank(),
                enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.7f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f)
            ) {
                IconButton(
                    onClick = onSend,
                    enabled = !isGenerating,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AxoraGradient)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.cd_send),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceButton(
    isListening: Boolean,
    isGenerating: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val startVoice by rememberUpdatedState(onStartVoice)
    val stopVoice by rememberUpdatedState(onStopVoice)

    val pulse = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .semantics {
                // TalkBack не умеет «удерживать» — двойной тап работает как переключатель.
                onClick {
                    if (isGenerating) return@onClick false
                    if (isListening) stopVoice() else startVoice()
                    true
                }
            }
            .pointerInput(isGenerating) {
                if (isGenerating) return@pointerInput
                detectTapGestures(
                    onPress = {
                        startVoice()
                        try {
                            awaitRelease()
                        } finally {
                            stopVoice()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    val s = if (isListening) pulseScale else 1f
                    scaleX = s
                    scaleY = s
                }
                .alpha(if (isGenerating) 0.4f else 1f)
                .clip(CircleShape)
                .background(AxoraGradient),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(if (isListening) AxoraGradient else SolidColor(scheme.surface)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = stringResource(R.string.cd_mic),
                    tint = if (isListening) Color.White else scheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}