package com.jayc180.rhythmengine.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * BackgroundLayer — androidMain.
 * Renders theme bg0 -> optional image -> dim overlay -> content
 *
 * img box calculated based on visible area so no top status bar
 */
@Composable
fun BackgroundLayer(
    theme:    ThemeConfig,
    bgConfig: BackgroundConfig,
    content:  @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1: theme base
        Box(modifier = Modifier.fillMaxSize().background(theme.bg0))

        // 2+3: image + dim
        if (bgConfig.hasImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .clipToBounds(),
            ) {
                AsyncImage(
                    model              = java.io.File(bgConfig.imagePath!!),
                    contentDescription = null,
                    contentScale       = when (bgConfig.fitMode) {
                        BgFitMode.Fill    -> ContentScale.Crop
                        BgFitMode.Move    -> ContentScale.Crop
                        BgFitMode.Stretch -> ContentScale.FillBounds
                    },
                    alignment = Alignment.Center,
                    modifier  = Modifier
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = bgConfig.dim)),
                    )
                }
            }
        }

        // app
        content()
    }
}
