package com.imageai.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imageai.app.data.repository.ChatRepository
import com.imageai.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    repository: ChatRepository,
    darkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    var apiKey by remember { mutableStateOf(repository.getApiKey()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Настройки",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = if (darkTheme) FigmaWhite else FigmaBlack
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = if (darkTheme) FigmaWhite else FigmaBlack
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (darkTheme) FigmaDarkBg else FigmaWhite,
                    titleContentColor = if (darkTheme) FigmaWhite else FigmaBlack,
                    navigationIconContentColor = if (darkTheme) FigmaWhite else FigmaBlack
                )
            )
        },
        containerColor = if (darkTheme) FigmaDarkBg else FigmaWhite
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // API ключ
            _buildSection(
                title = "API",
                darkTheme = darkTheme
            ) {
                _buildCardRow(
                    icon = Icons.Default.Key,
                    title = "API ключ",
                    subtitle = "enter.pollinations.ai/keys — бесплатно",
                    darkTheme = darkTheme
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("sk_... или pk_...", fontSize = 12.sp, color = FigmaPlaceholder)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FigmaPrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = if (darkTheme) FigmaDarkCard else FigmaInputBg,
                            unfocusedContainerColor = if (darkTheme) FigmaDarkCard else FigmaInputBg,
                            cursorColor = FigmaPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Настройки
            _buildSection(
                title = "Приложение",
                darkTheme = darkTheme
            ) {
                _buildToggleRow(
                    icon = Icons.Default.WbSunny,
                    title = "Тёмная тема",
                    subtitle = "Переключить оформление",
                    checked = darkTheme,
                    onCheckedChange = onToggleDarkTheme,
                    darkTheme = darkTheme
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "ImageAI • Pollinations API",
                fontSize = 11.sp,
                color = FigmaPlaceholder,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    LaunchedEffect(apiKey) {
        repository.setApiKey(apiKey)
    }
}

@Composable
private fun _buildSection(
    title: String,
    darkTheme: Boolean,
    items: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (darkTheme) Color(0xFF8E8E93) else FigmaTextSecondary,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
        )
        items()
    }
}

@Composable
private fun _buildCardRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (darkTheme) FigmaDarkCard else FigmaWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = FigmaPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (darkTheme) FigmaWhite else FigmaBlack
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = FigmaPlaceholder
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun _buildToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    darkTheme: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (darkTheme) FigmaDarkCard else FigmaWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FigmaPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (darkTheme) FigmaWhite else FigmaBlack
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = FigmaPlaceholder
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FigmaPrimary,
                    checkedTrackColor = FigmaPrimary.copy(alpha = 0.3f),
                    uncheckedThumbColor = FigmaPlaceholder,
                    uncheckedTrackColor = if (darkTheme) FigmaDarkBg else FigmaLightBg
                )
            )
        }
    }
}
