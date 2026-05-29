package com.jayc180.rhythmengine.audio

class AudioEngineDesktop : AudioEngine {
    override fun open(): Boolean = true
    override fun close() {}
    override fun trigger(soundId: String, volume: Float, expectedFireNanos: Long) {}
    override fun stopAll() {}
    override val activeVoiceCount: Int get() = 0
}