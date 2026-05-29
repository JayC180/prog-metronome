package com.jayc180.rhythmengine.audio

import audio_engine.*
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class AudioEngineIos : AudioEngine {

    override fun open(): Boolean  = re_audio_open()
    override fun close()          { re_audio_close() }
    override fun stopAll()        { re_audio_stop_all() }
    override val activeVoiceCount: Int get() = re_audio_voice_count()

    override fun trigger(soundId: String, volume: Float, expectedFireNanos: Long) =
        re_audio_trigger(soundId, volume, expectedFireNanos)

    /** Load a WAV from a filesystem path directly into the C++ sample store. */
    fun loadSamplePath(soundId: String, filePath: String): Boolean =
        re_audio_load_sample_path(soundId, filePath)
}
