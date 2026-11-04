package com.imageai.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imageai.app.domain.model.ChatUiState
import com.imageai.app.ui.components.*
import com.imageai.app.ui.theme.*
import com.imageai.app.ui.viewmodel.ChatViewModel
import com.imageai.app.util.VoiceInputHelper
import com.imageai.app.util.SaveImageUtil
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    darkTheme: Boolean,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var voiceHelper by remember { mutableStateOf<VoiceInputHelper?>(null) }
    var isListeningState by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Диалог сохранения изображения
    var showSaveDialog by remember { mutableStateOf<Pair<Long, String>?>(null) }

    // Показать диалог с кнопками сохранения и копирования
    fun showSaveAndCopyDialog(message: com.imageai.app.domain.model.ChatMessage) {
        showSaveDialog = Pair(message.id, message.imageUrl ?: "")
    }

    // Закрыть диалог
    fun dismissSaveDialog() {
        showSaveDialog = null
    }

    // Сохранить изображение в галерею
    fun saveImageToGallery(imageUrl: String) {
        val imageFile = java.io.File(imageUrl)
        if (imageFile.exists()) {
            SaveImageUtil.saveToGallery(context, imageFile)
        }
        dismissSaveDialog()
    }

    // Поделиться изображением
    fun shareImage(imageUrl: String) {
        val imageFile = java.io.File(imageUrl)
        if (imageFile.exists()) {
            SaveImageUtil.shareImage(context, imageFile)
        }
        dismissSaveDialog()
    }

    // Копировать текст в буфер обмена
    fun copyText(text: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("prompt", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    fun startVoiceInput() {
        isListeningState = true
        val helper = VoiceInputHelper(context as Activity)
        helper.initialize(object : VoiceInputHelper.VoiceInputListener {
            override fun onRecordingStarted() { isListeningState = true }
            override fun onSpeechResult(text: String) {
                isListeningState = false
                voiceHelper = null
                if (text.isNotBlank()) viewModel.handleVoiceResult(text)
            }
            override fun onError(message: String) {
                isListeningState = false
                voiceHelper = null
                viewModel.handleVoiceError(message)
            }
            override fun onRecordingStopped() { isListeningState = false }
        })
        voiceHelper = helper
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                onSettingsClick = onSettingsClick,
                darkTheme = darkTheme
            )
        },
        containerColor = if (darkTheme) FigmaDarkBg else FigmaWhite
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Messages
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    EmptyChatPlaceholder()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = false,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(uiState.messages) { message ->
                            MessageBubble(
                                message = message,
                                onRetry = { viewModel.sendMessage(inputText) },
                                onClick = { showSaveAndCopyDialog(message) }
                            )
                        }
                        if (uiState.isGenerating) {
                            item { LoadingIndicator() }
                        }
                    }
                }
            }

            // Error
            uiState.error?.let { error ->
                ErrorBanner(error = error, onDismiss = { viewModel.clearError() })
            }

            // Диалог сохранения изображения
            showSaveDialog?.let { (msgId, imageUrl) ->
                AlertDialog(
                    onDismissRequest = { dismissSaveDialog() },
                    title = { Text("Сохранить изображение") },
                    text = { Text("Выберите действие:") },
                    confirmButton = {
                        TextButton(onClick = { saveImageToGallery(imageUrl) }) {
                            Icon(Icons.Default.Download, "Сохранить", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("В галерею")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { shareImage(imageUrl) }) {
                                Icon(Icons.Default.Share, "Поделиться", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Поделиться")
                            }
                            TextButton(onClick = { dismissSaveDialog() }) {
                                Text("Отмена")
                            }
                        }
                    }
                )
            }

            // Divider
            HorizontalDivider(
                color = if (darkTheme) FigmaDividerDark else FigmaDivider,
                thickness = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // Bottom input bar
            BottomInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                onVoiceClick = { startVoiceInput() },
                isListeningState = isListeningState,
                isGenerating = uiState.isGenerating,
                darkTheme = darkTheme
            )
        }
    }
}

@Composable
fun BottomInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    isListeningState: Boolean,
    isGenerating: Boolean,
    darkTheme: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (darkTheme) FigmaDarkBg else FigmaWhite,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left: Plus button
            IconButton(
                onClick = { /* TODO */ },
                enabled = !isListeningState && !isGenerating,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить",
                    tint = if (isListeningState || isGenerating) Color.Gray else FigmaGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Input field
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Что сгенерировать?",
                        fontSize = 15.sp,
                        color = FigmaPlaceholder
                    )
                },
                maxLines = 4,
                enabled = !isListeningState && !isGenerating,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = if (darkTheme) FigmaDarkCard else FigmaInputBg,
                    unfocusedContainerColor = if (darkTheme) FigmaDarkCard else FigmaInputBg,
                    cursorColor = FigmaPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 15.sp,
                    color = if (darkTheme) FigmaWhite else FigmaBlack
                )
            )

            // Right: Send or Mic
            if (isListeningState) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.Red, RoundedCornerShape(5.dp))
                    )
                }
            } else if (value.isNotBlank()) {
                IconButton(
                    onClick = onSend,
                    enabled = !isGenerating,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = if (isGenerating) Color.Gray else FigmaPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onVoiceClick,
                    enabled = !isGenerating,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Голосовой ввод",
                        tint = if (isGenerating) Color.Gray else FigmaGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
