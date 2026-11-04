package com.imageai.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Figma-цвета
val FigmaWhite = Color(0xFFFFFFFF)
val FigmaLightBg = Color(0xFFF2F2F2)
val FigmaInputBg = Color(0xFFF0F0F0)
val FigmaBlack = Color(0xFF000000)
val FigmaGray = Color(0xFF666666)
val FigmaDarkGray = Color(0xFF444444)
val FigmaTextSecondary = Color(0xFF8E8E93)
val FigmaDarkBg = Color(0xFF1A1A1A)
val FigmaDarkCard = Color(0xFF2A2A2A)
val FigmaPrimary = Color(0xFF6B21A8)
val FigmaUserBubble = Color(0xFFF3E8FF)
val FigmaUserBubbleDark = Color(0xFF4A1C7A)
val FigmaAssistantBubble = FigmaLightBg
val FigmaAssistantBubbleDark = FigmaDarkCard
val FigmaPlaceholder = Color(0xFF999999)
val FigmaDivider = Color(0xFFE5E5E5)
val FigmaDividerDark = Color(0xFF3A3A3A)

val LightColorScheme = lightColorScheme(
    primary = FigmaPrimary,
    onPrimary = FigmaWhite,
    surface = FigmaWhite,
    onSurface = FigmaBlack
)

val DarkColorScheme = darkColorScheme(
    primary = FigmaPrimary,
    onPrimary = FigmaWhite,
    surface = FigmaDarkBg,
    onSurface = FigmaWhite
)

@Composable
fun ImageAATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
