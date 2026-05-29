package com.jayc180.rhythmengine.audio

import android.content.Context
import android.content.res.AssetManager
import com.jayc180.rhythmengine.model.SoundMap

/**
 * Kotlin-side AudioEngine — wraps the C++ JNI bridge.
 *
 * Supports loading sounds from either:
 *   - Android assets (assets/ folder)  → loadSounds(assets, soundMap)
 *   - res/raw resources                → loadSoundsFromRaw(context, soundMap)
 *
 * SoundConfig.resourceUri is interpreted as:
 *   - asset path  when using loadSounds()       e.g. "sounds/kick.wav"
 *   - raw res name when using loadSoundsFromRaw() e.g. "kick"  (no extension, no R.raw.)
 */
class AudioEngineAndroid : AudioEngine {
    override fun open(): Boolean = nativeOpen()
    override fun close() = nativeClose()

    // load from assets dir, resourceUri is relative path
    fun loadSounds(assets: AssetManager, soundMap: SoundMap): List<String> {
        val failed = mutableListOf<String>()
        soundMap.all().forEach { config ->
            if (!nativeLoadSample(assets, config.soundId, config.resourceUri))
                failed += config.soundId
        }
        return failed
    }

    /**
     * Load from res/raw
     * res/raw files are opened via a file descriptor (c++ layer)
     */
    fun loadSoundsFromRaw(context: Context, soundMap: SoundMap): List<String> {
        val failed = mutableListOf<String>()
        soundMap.all().forEach { config ->
            val resName = config.resourceUri

            // try target context 1st then instrumentation ctx
            val targetCtx = try {
                context.createPackageContext(context.packageName, 0)
            } catch (e: Exception) { context }

            val resId = targetCtx.resources.getIdentifier(resName, "raw", targetCtx.packageName)
            if (resId == 0) {
                android.util.Log.e("AudioEngine",
                    "res/raw/$resName not found in package ${targetCtx.packageName}. " +
                            "Make sure $resName.wav exists in src/main/res/raw/")
                failed += config.soundId
                return@forEach
            }

            val afd = targetCtx.resources.openRawResourceFd(resId)
            val ok  = nativeLoadSampleFd(
                config.soundId,
                afd.parcelFileDescriptor.fd,
                afd.startOffset,
                afd.length
            )
            afd.close()
            if (!ok) {
                android.util.Log.e("AudioEngine", "Failed to parse WAV for $resName")
                failed += config.soundId
            } else {
                android.util.Log.i("AudioEngine", "Loaded res/raw/$resName as '${config.soundId}'")
            }
        }
        return failed
    }

    fun loadSound(assets: AssetManager, soundId: String, assetPath: String): Boolean =
        nativeLoadSample(assets, soundId, assetPath)

    fun loadSampleFd(soundId: String, fd: Int, offset: Long, length: Long): Boolean =
        nativeLoadSampleFd(soundId, fd, offset, length)

    fun loadSampleFile(soundId: String, filePath: String): Boolean =
        nativeLoadSamplePath(soundId, filePath)

    // trigger snd from transport's dispatcher
    override fun trigger(soundId: String, volume: Float, expectedFireNanos: Long) =
        nativeTriggerSound(soundId, volume, expectedFireNanos)

    override fun stopAll() = nativeStopAll()

    override val activeVoiceCount: Int get() = nativeActiveVoiceCount()

    private external fun nativeOpen(): Boolean
    private external fun nativeClose()
    private external fun nativeLoadSample(assets: AssetManager, soundId: String, assetPath: String): Boolean
    private external fun nativeLoadSampleFd(soundId: String, fd: Int, offset: Long, length: Long): Boolean
    private external fun nativeTriggerSound(soundId: String, volume: Float, expectedFireNanos: Long)
    private external fun nativeStopAll()
    private external fun nativeActiveVoiceCount(): Int
    private external fun nativeLoadSamplePath(soundId: String, filePath: String): Boolean

    companion object {
        init { System.loadLibrary("rhythmengine") }
    }
}