package com.jayc180.rhythmengine.audio

interface AudioEngine {
    fun open(): Boolean
    fun close()
    fun trigger(soundId: String, volume: Float, expectedFireNanos: Long)
    fun stopAll()

    val activeVoiceCount: Int
}