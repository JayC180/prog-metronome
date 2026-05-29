package com.jayc180.rhythmengine.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * BackgroundLayer for iOS — same 4-layer structure as Android.
 * Uses a file:// URL for Coil 3 (no java.io.File on Kotlin/Native).
 */
@Composable
fun BackgroundLayer(
    theme:    ThemeConfig,
    bgConfig: BackgroundConfig,
    content:  @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: theme base colour
        Box(modifier = Modifier.fillMaxSize().background(theme.bg0))

        // Layers 2 + 3: image + dim
        if (bgConfig.hasImage) {
            AsyncImage(
                model              = "file://${bgConfig.imagePath}",
                contentDescription = null,
                contentScale       = when (bgConfig.fitMode) {
                    BgFitMode.Fill, BgFitMode.Move -> ContentScale.Crop
                    BgFitMode.Stretch              -> ContentScale.FillBounds
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (bgConfig.fitMode == BgFitMode.Move)
                            Modifier.graphicsLayer {
                                scaleX       = bgConfig.panScale
                                scaleY       = bgConfig.panScale
                                translationX = (bgConfig.panX - 0.5f) * size.width
                                translationY = (bgConfig.panY - 0.5f) * size.height
                            }
                        else Modifier
                    ),
            )
            if (bgConfig.dim > 0f) {
                Box(modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = bgConfig.dim)))
            }
        }

        // Layer 4: app UI
        content()
    }
}
