package com.pockettoolbox.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ToolboxPalette(val storageKey: String) {
    Fresh("fresh"),
    Warm("warm"),
    White("white");

    companion object {
        fun fromStorageKey(value: String?): ToolboxPalette = entries.firstOrNull {
            it.storageKey == value
        } ?: Fresh
    }
}

private fun toolboxColorScheme(palette: ToolboxPalette) = lightColorScheme(
    primary = ToolboxColors.Navy,
    onPrimary = Color.White,
    primaryContainer = ToolboxColors.TealSoft,
    onPrimaryContainer = ToolboxColors.Navy,
    secondary = ToolboxColors.Teal,
    onSecondary = ToolboxColors.NavyDeep,
    background = when (palette) {
        ToolboxPalette.Fresh -> ToolboxColors.Background
        ToolboxPalette.Warm -> Color(0xFFFAF7F1)
        ToolboxPalette.White -> Color.White
    },
    onBackground = ToolboxColors.Navy,
    surface = when (palette) {
        ToolboxPalette.Fresh -> ToolboxColors.Surface
        ToolboxPalette.Warm -> Color(0xFFFFFEFB)
        ToolboxPalette.White -> Color(0xFFF7F9FA)
    },
    onSurface = ToolboxColors.Navy,
    surfaceVariant = when (palette) {
        ToolboxPalette.Fresh -> Color(0xFFEDF3F4)
        ToolboxPalette.Warm -> Color(0xFFF2ECE3)
        ToolboxPalette.White -> Color(0xFFEEF2F3)
    },
    onSurfaceVariant = ToolboxColors.TextMuted,
    outline = ToolboxColors.Border,
    error = ToolboxColors.Error,
)

@Composable
fun PocketToolboxTheme(
    palette: ToolboxPalette = ToolboxPalette.Fresh,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = toolboxColorScheme(palette),
        content = content,
    )
}
