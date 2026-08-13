package com.drift.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Night-club-quiet palette, adapted from a wellness-app reference: deep
// violet-black chrome, a vivid violet→magenta accent, moon-white for glow
// and focus. The reference's photography was warm/golden (daytime wellness);
// Drift keeps the same glow-vignette *technique* but shifted cool/nocturnal,
// since this is a sleep app, not a morning one.
val Void = Color(0xFF150F24)          // background
val Panel = Color(0xFF211A38)         // cards / surfaces
val PanelDim = Color(0xFF1A1530)
val AccentViolet = Color(0xFF8B5CF6)  // primary accent (focus, progress, CTA)
val AccentMagenta = Color(0xFFD946EF) // gradient partner for glows/CTAs
val MoonWhite = Color(0xFFF5F3FF)     // glow highlight
val Moonlight = Color(0xFFEDEBF7)     // primary text
val MoonDim = Color(0xFF9B93B8)       // secondary text

@Composable
fun DriftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Void,
            surface = Panel,
            surfaceVariant = PanelDim,
            onSurface = Moonlight,
            onSurfaceVariant = MoonDim,
            primary = AccentViolet,
            onPrimary = Void,
            secondary = AccentMagenta,
            onBackground = Moonlight,
        ),
    ) {
        // tv-material3 only sets LocalContentColor inside a Surface, and its
        // default is black — so any Text we draw straight onto the background
        // (screen headings, the mix-picker overlay) would be invisible.
        CompositionLocalProvider(LocalContentColor provides Moonlight, content = content)
    }
}
