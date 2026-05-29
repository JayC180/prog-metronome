package com.jayc180.rhythmengine.audio

/**
 * Platform-agnostic sound model
 *
 * SoundRepository (androidMain) maps its internal SoundEntry/SoundSource
 * to this type before handing it to the UI.
 */
data class SoundInfo(
    val id:      String,   // resource key used in BeatNode and TrackItem
    val label:   String,   // display name (no prefix, underscores → spaces)
    val isUser:  Boolean,  // true = user-imported, false = bundled default
)