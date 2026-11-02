package com.client.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.client.app.ui.display.DisplayRateManager
import dagger.hilt.android.AndroidEntryPoint

private val ObsidianBlackScheme = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF09090B),
    secondary = Color(0xFF34A853),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF4285F4),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF0D0D0F),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF18181B),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainer = Color(0xFF111113),
    surfaceContainerHigh = Color(0xFF18181B),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    error = Color(0xFFEA4335),
    onError = Color(0xFFFFFFFF)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        DisplayRateManager.setHighRefreshRate(window, true)

        setContent {
            MaterialTheme(colorScheme = ObsidianBlackScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Gemini Live Ultra",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DisplayRateManager.setHighRefreshRate(window, true)
    }

    override fun onPause() {
        super.onPause()
        DisplayRateManager.setHighRefreshRate(window, false)
    }
}