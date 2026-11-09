package com.lunify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.lunify.ui.LunifyApp
import com.lunify.ui.theme.LunifyTheme

/**
 * ==============================================================================
 * Lunify - Main Activity Entrypoint (MainActivity.kt)
 * ==============================================================================
 *
 * Hey friend! Welcome to the primary activity entry point of Lunify.
 *
 * Here:
 * 1. `enableEdgeToEdge()`: Instructs Android to draw behind system bars (status bar
 *    and navigation bar), allowing our liquid glass background glows and floating
 *    navigation capsule to achieve seamless cinematic immersion.
 * 2. `LunifyTheme`: Enforces our obsidian dark palette and liquid glass providers.
 * 3. `LunifyApp`: Hosts the navigation graph, floating MiniPlayer, and Fullscreen
 *    Now Playing overlay.
 * ==============================================================================
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge window drawing
        enableEdgeToEdge()
        
        setContent {
            LunifyTheme {
                LunifyApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
