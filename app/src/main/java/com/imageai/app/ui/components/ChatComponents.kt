package com.imageai.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.imageai.app.domain.model.ChatMessage
import com.imageai.app.ui.theme.*
import java.io.File

// ========== TopBar ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    onSettingsClick: () -> Unit,
    darkTheme: Boolean
) {
    TopAppBar(
        title = {
            Card(
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (darkTheme) FigmaDarkCard else FigmaWhite
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Robot icon
                    Text(
                        text = "🤖",
                        fontSize = 18.sp
                    )
                    Text(
                        text = "PollinationsAI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (darkTheme) FigmaWhite else FigmaBlack
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = if (darkTheme) FigmaDarkGray else FigmaGray,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (darkTheme) FigmaDarkBg else FigmaWhite,
            titleContentColor = if (darkTheme) FigmaWhite else FigmaBlack,
            actionIconContentColor = if (darkTheme) FigmaDarkGray else FigmaGray
        )
    )
}

// ========== Message Bubbles ==========

@Composable
fun MessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    onClick: () -> Unit = {}
) {
    val isUser = message.isUser()
    val isAssistantImage = !isUser && message.isImageMessage()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .align(if (isUser) Alignment.TopEnd else Alignment.TopStart)
                .widthIn(max = 300.dp)
        ) {
            // Assistant message with image
            if (isAssistantImage) {
                // Текст с иконкой копирования
                Box {
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = FigmaBlack,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(FigmaAssistantBubble, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .align(Alignment.Center)
                            .clickable(onClick = { /* показать диалог */ })
                    )
                    // Иконка копирования справа
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .clickable(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("prompt", message.translatedPrompt ?: message.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            })
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Копировать",
                            tint = FigmaPlaceholder,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Image
                val file = message.imageFile ?: message.imageUrl?.let { File(it) }
                if (file != null && file.exists()) {
                    val context = LocalContext.current
                    val model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build()
                    Image(
                        painter = rememberAsyncImagePainter(model),
                        contentDescription = "Generated image",
                        modifier = Modifier
                            .width(250.dp)
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { /* показать диалог */ }
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onClick() })
                            },
                        contentScale = ContentScale.Crop
                    )
                } else if (message.imageUrl != null && message.imageUrl!!.isNotEmpty()) {
                    val context = LocalContext.current
                    val model = ImageRequest.Builder(context)
                        .data(message.imageUrl)
                        .crossfade(true)
                        .build()
                    Image(
                        painter = rememberAsyncImagePainter(model),
                        contentDescription = "Generated image",
                        modifier = Modifier
                            .width(250.dp)
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { /* показать диалог */ }
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onClick() })
                            },
                        contentScale = ContentScale.Crop
                    )
                }

                // Prompt text
                if (message.originalPrompt != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.originalPrompt,
                        fontSize = 12.sp,
                        color = FigmaPlaceholder,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            } else {
                // Text-only message (user or assistant without image)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isUser) FigmaUserBubble else FigmaAssistantBubble,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .align(if (isUser) Alignment.End else Alignment.Start)
                ) {
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = FigmaBlack
                    )
                }
            }

            // Error retry
            if (message.text.contains("Ошибка")) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B6B)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    elevation = null
                ) {
                    Text("Повторить", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

// ========== Loading Indicator ==========

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = FigmaPrimary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Генерация...",
            fontSize = 12.sp,
            color = FigmaPlaceholder
        )
    }
}

// ========== Empty State ==========

@Composable
fun EmptyChatPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color(0xFFD0D0D0)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Опишите изображение,\nкоторое хотите создать",
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            color = FigmaPlaceholder,
            lineHeight = 20.sp
        )
    }
}

// ========== Error Banner ==========

@Composable
fun ErrorBanner(
    error: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = error,
            fontSize = 12.sp,
            color = Color(0xFFFF6B6B)
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = FigmaPlaceholder,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
