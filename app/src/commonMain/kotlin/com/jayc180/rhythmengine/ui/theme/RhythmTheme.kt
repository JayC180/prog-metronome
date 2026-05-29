package com.jayc180.rhythmengine.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalPanelAlpha = staticCompositionLocalOf { 1.0f }

object RhythmType {
    val trackName = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val beatValue = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val toolBtn   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val numpad    = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
    val label     = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val bpmLabel  = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal)
    val bpmValue  = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun RhythmTheme(
    theme:      ThemeConfig = BuiltInThemes.ObsidianDark,
    panelAlpha: Float       = theme.defaultPanelAlpha,
    content:    @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTheme      provides theme,
        LocalPanelAlpha provides panelAlpha,
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background   = theme.bg0,
                surface      = theme.bg1,
                primary      = theme.accent,
                onPrimary    = theme.bg0,
                onBackground = theme.textPrimary,
                onSurface    = theme.textPrimary,
            ),
            content = content,
        )
    }
}

// alpha for panels...or maybe not?
val surfaceBg1: Color
    @Composable get() = RhythmColors.bg1.copy(alpha = LocalPanelAlpha.current)

val surfaceBg2: Color
    @Composable get() = RhythmColors.bg2.copy(alpha = LocalPanelAlpha.current)

val surfaceBg3: Color
    @Composable get() = RhythmColors.bg3.copy(alpha = LocalPanelAlpha.current)