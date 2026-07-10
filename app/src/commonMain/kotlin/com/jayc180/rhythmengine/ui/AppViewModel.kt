package com.jayc180.rhythmengine.ui

import com.jayc180.rhythmengine.audio.AudioEngine
import com.jayc180.rhythmengine.audio.SoundInfo
import com.jayc180.rhythmengine.builder.TrackBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * shared app state holder for all platforms
 */
class AppViewModel(
    val builder:           TrackBuilder,
    val audio:             AudioEngine,
    val playheads:         StateFlow<Map<String, Int>>,
    val sounds:            StateFlow<List<SoundInfo>>,
    val onSettingsClick:   () -> Unit = {},
    private val _globalDefaultSoundId:    String,
    private val onSetGlobalDefault:       (String) -> Unit,
    private val onSetGlobalDefaultVolume: (Float)  -> Unit = {},
    initialBeatBlockSizeIndex:            Int = 1,
    private val onSetBeatBlockSizeIndex:  (Int) -> Unit = {},
    initialBeatStackedFractions:          Boolean = true,
    private val onSetBeatStackedFractions:(Boolean) -> Unit = {},
) {
    val globalDefaultSoundId: String get() = _globalDefaultSoundId
    val globalDefaultVolume:  Float  get() = builder.defaultVolume

    private val _beatBlockSizeIndex = MutableStateFlow(initialBeatBlockSizeIndex)
    val beatBlockSizeIndex: StateFlow<Int> get() = _beatBlockSizeIndex

    private val _beatStackedFractions = MutableStateFlow(initialBeatStackedFractions)
    val beatStackedFractions: StateFlow<Boolean> get() = _beatStackedFractions

    fun setGlobalDefault(soundId: String) {
        onSetGlobalDefault(soundId)
        builder.setDefaultSoundId(soundId)
    }

    fun setGlobalDefaultVolume(volume: Float) {
        onSetGlobalDefaultVolume(volume)
        builder.setDefaultVolume(volume)
    }

    fun setBeatBlockSizeIndex(v: Int) {
        _beatBlockSizeIndex.value = v.coerceIn(0, 2)
        onSetBeatBlockSizeIndex(v.coerceIn(0, 2))
    }

    fun setBeatStackedFractions(v: Boolean) {
        _beatStackedFractions.value = v
        onSetBeatStackedFractions(v)
    }
}
