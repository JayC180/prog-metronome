package com.jayc180.rhythmengine.ui

import com.jayc180.rhythmengine.audio.AudioEngine
import com.jayc180.rhythmengine.audio.SoundInfo
import com.jayc180.rhythmengine.builder.TrackBuilder
import kotlinx.coroutines.flow.StateFlow

/**
 * shared app state holder for all platforms
 */
class AppViewModel(
    val builder:           TrackBuilder,
    val audio:             AudioEngine,
    val playheads:         StateFlow<Map<String, Int>>,
    val sounds:            StateFlow<List<SoundInfo>>, // not SoundEntry
    val onSettingsClick:   () -> Unit = {},
    private val _globalDefaultSoundId: String,
    private val onSetGlobalDefault:    (String) -> Unit,
) {
    val globalDefaultSoundId: String get() = _globalDefaultSoundId

    fun setGlobalDefault(soundId: String) {
        onSetGlobalDefault(soundId)
        builder.setDefaultSoundId(soundId)
    }
}