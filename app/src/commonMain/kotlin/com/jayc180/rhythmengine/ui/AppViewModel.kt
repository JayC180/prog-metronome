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
    private val _globalDefaultSoundId:      String,
    private val onSetGlobalDefault:         (String) -> Unit,
    private val onSetGlobalDefaultVolume:   (Float)  -> Unit = {},
    initialGlobalDefaultVolume:             Float    = 1.0f,
    initialBeatBlockSizeIndex:              Int = 1,
    private val onSetBeatBlockSizeIndex:    (Int) -> Unit = {},
    initialBeatStackedFractions:            Boolean = true,
    private val onSetBeatStackedFractions:  (Boolean) -> Unit = {},
    initialSubdivisionSoundId:              String  = "default",
    private val onSetSubdivisionSound:      (String) -> Unit = {},
    initialSubdivisionVolume:               Float   = 1.0f,
    private val onSetSubdivisionVolume:     (Float)  -> Unit = {},
) {
    val globalDefaultSoundId: String get() = _globalDefaultSoundId

    init {
        // propagate persisted prefs into the builder on startup
        builder.setSubdivisionSoundId(initialSubdivisionSoundId)
        builder.setSubdivisionVolume(initialSubdivisionVolume)
    }

    private val _globalDefaultVolume = MutableStateFlow(initialGlobalDefaultVolume)
    val globalDefaultVolume: StateFlow<Float> get() = _globalDefaultVolume

    private val _beatBlockSizeIndex = MutableStateFlow(initialBeatBlockSizeIndex)
    val beatBlockSizeIndex: StateFlow<Int> get() = _beatBlockSizeIndex

    private val _beatStackedFractions = MutableStateFlow(initialBeatStackedFractions)
    val beatStackedFractions: StateFlow<Boolean> get() = _beatStackedFractions

    private val _subdivisionSoundId = MutableStateFlow(initialSubdivisionSoundId)
    val subdivisionSoundId: StateFlow<String> get() = _subdivisionSoundId

    private val _subdivisionVolume = MutableStateFlow(initialSubdivisionVolume)
    val subdivisionVolume: StateFlow<Float> get() = _subdivisionVolume

    fun setGlobalDefault(soundId: String) {
        onSetGlobalDefault(soundId)
        builder.setDefaultSoundId(soundId)
    }

    fun setGlobalDefaultVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _globalDefaultVolume.value = v
        onSetGlobalDefaultVolume(v)
        builder.setDefaultVolume(v)
    }

    fun setBeatBlockSizeIndex(v: Int) {
        _beatBlockSizeIndex.value = v.coerceIn(0, 2)
        onSetBeatBlockSizeIndex(v.coerceIn(0, 2))
    }

    fun setBeatStackedFractions(v: Boolean) {
        _beatStackedFractions.value = v
        onSetBeatStackedFractions(v)
    }

    fun setSubdivisionSound(soundId: String) {
        _subdivisionSoundId.value = soundId
        onSetSubdivisionSound(soundId)
        builder.setSubdivisionSoundId(soundId)
    }

    fun setSubdivisionVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _subdivisionVolume.value = v
        onSetSubdivisionVolume(v)
        builder.setSubdivisionVolume(v)
    }

    fun enableBeatSubdiv(beatIndex: Int)  = builder.enableBeatSubdiv(beatIndex)
    fun disableBeatSubdiv(beatIndex: Int) = builder.disableBeatSubdiv(beatIndex)
    fun toggleSubbeat(beatIndex: Int, subbeatIndex: Int) = builder.toggleSubbeat(beatIndex, subbeatIndex)
    fun setSubbeatAll(beatIndex: Int, active: Boolean)   = builder.setSubbeatAll(beatIndex, active)
}
