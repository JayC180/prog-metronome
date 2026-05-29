package com.jayc180.rhythmengine.ui.theme

/**
 * BackgroundConfig; commonMain
 * imagePath is a file path string; androidMain converts to Uri for Coil
 */
enum class BgFitMode {
    Fill,    // ContentScale.Crop, centered
    Move,    // ContentScale.Crop + graphicsLayer translate/scale
    Stretch, // ContentScale.FillBounds
}

data class BackgroundConfig(
    val imagePath: String?   = null,
    val fitMode:   BgFitMode = BgFitMode.Fill,
    val dim:       Float     = 0.4f,
    val panX:      Float     = 0.5f,   // 0=left edge, 1=right edge; Move mode only
    val panY:      Float     = 0.5f,   // 0=top  edge, 1=bottom edge
    val panScale:  Float     = 1f,     // 0.2–3.0 scale multiplier; Move mode only
) {
    val hasImage: Boolean get() = imagePath != null
}
