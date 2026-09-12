package com.pockettoolbox.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ToolboxColors.Navy,
    onPrimary = Color.White,
    primaryContainer = ToolboxColors.TealSoft,
    onPrimaryContainer = ToolboxColors.Navy,
    secondary = ToolboxColors.Teal,
    onSecondary = ToolboxColors.NavyDeep,
    background = ToolboxColors.Background,
    onBackground = ToolboxColors.Navy,
    surface = ToolboxColors.Surface,
    onSurface = ToolboxColors.Navy,
    surfaceVariant = Color(0xFFEDF3F4),
    onSurfaceVariant = ToolboxColors.TextMuted,
    outline = ToolboxColors.Border,
    error = ToolboxColors.Error,
)

private val DarkColors = darkColorScheme(
    primary = ToolboxColors.TealBright,
    onPrimary = ToolboxColors.NavyDeep,
    primaryContainer = ToolboxColors.NavySoft,
    onPrimaryContainer = Color(0xFFDFFFFA),
    secondary = ToolboxColors.TealBright,
    background = ToolboxColors.NavyDeep,
    onBackground = Color(0xFFE8F6F7),
    surface = Color(0xFF0B293A),
    onSurface = Color(0xFFE8F6F7),
    surfaceVariant = Color(0xFF163747),
    onSurfaceVariant = Color(0xFFB8CDD3),
    outline = Color(0xFF385563),
    error = Color(0xFFFFB3BC),
)

@Composable
fun PocketToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

