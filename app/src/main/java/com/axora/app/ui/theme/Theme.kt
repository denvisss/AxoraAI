package com.axora.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AxoraLightScheme = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF17106B),
    inversePrimary = AxoraLavender,
    secondary = Color(0xFF6E56CF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E0FF),
    onSecondaryContainer = Color(0xFF25105C),
    tertiary = AxoraBlue,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE6FF),
    onTertiaryContainer = Color(0xFF001A41),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F7FC),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFF7F7FC),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFE6E4F0),
    onSurfaceVariant = Color(0xFF48465A),
    surfaceTint = Color(0xFF5B5BD6),
    inverseSurface = Color(0xFF303038),
    inverseOnSurface = Color(0xFFF2F0F7),
    outline = Color(0xFF79768A),
    outlineVariant = Color(0xFFCAC7DC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7F7FC),
    surfaceContainer = Color(0xFFEFEFF7),
    surfaceContainerHigh = Color(0xFFE9E8F2),
    surfaceContainerHighest = Color(0xFFE3E2EC),
    surfaceContainerLow = Color(0xFFF3F2FA),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

private val AxoraDarkScheme = darkColorScheme(
    primary = Color(0xFFC3B8FF),
    onPrimary = Color(0xFF2C1F6E),
    primaryContainer = Color(0xFF4A3D9E),
    onPrimaryContainer = Color(0xFFE4DFFF),
    inversePrimary = Color(0xFF5B5BD6),
    secondary = Color(0xFFC8C0EA),
    onSecondary = Color(0xFF302A55),
    secondaryContainer = Color(0xFF47406E),
    onSecondaryContainer = Color(0xFFE4DFFF),
    tertiary = Color(0xFFA9C4FF),
    onTertiary = Color(0xFF002E69),
    tertiaryContainer = Color(0xFF004494),
    onTertiaryContainer = Color(0xFFDCE6FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE6E4F0),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE6E4F0),
    surfaceVariant = Color(0xFF48465A),
    onSurfaceVariant = Color(0xFFCAC7DC),
    surfaceTint = Color(0xFFC3B8FF),
    inverseSurface = Color(0xFFE6E4F0),
    inverseOnSurface = Color(0xFF303038),
    outline = Color(0xFF948FA0),
    outlineVariant = Color(0xFF48465A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3942),
    surfaceContainer = Color(0xFF201F28),
    surfaceContainerHigh = Color(0xFF2B2933),
    surfaceContainerHighest = Color(0xFF36343E),
    surfaceContainerLow = Color(0xFF1B1A22),
    surfaceContainerLowest = Color(0xFF0D0D12)
)

@Composable
fun AxoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AxoraDarkScheme else AxoraLightScheme

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