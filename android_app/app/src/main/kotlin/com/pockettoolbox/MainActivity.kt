package com.pockettoolbox.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pockettoolbox.core.designsystem.PocketToolboxTheme
import com.pockettoolbox.core.designsystem.ToolboxPalette

private const val APPEARANCE_PREFERENCES = "appearance"
private const val PALETTE_PREFERENCE = "background_palette"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val preferences = remember { getSharedPreferences(APPEARANCE_PREFERENCES, MODE_PRIVATE) }
            var palette by remember {
                mutableStateOf(
                    ToolboxPalette.fromStorageKey(preferences.getString(PALETTE_PREFERENCE, null)),
                )
            }
            PocketToolboxTheme(palette = palette) {
                PocketToolboxApp(
                    palette = palette,
                    onPaletteChange = { selected ->
                        palette = selected
                        preferences.edit().putString(PALETTE_PREFERENCE, selected.storageKey).apply()
                    },
                )
            }
        }
    }
}
