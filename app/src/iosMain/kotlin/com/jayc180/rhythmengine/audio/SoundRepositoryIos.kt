package com.jayc180.rhythmengine.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

/**
 * Sound discovery for iOS.
 * Bundled sounds: app bundle "raw/" folder reference (WAV files from res/raw/).
 * User sounds: WAV, MP3, or FLAC files in Documents/user_sounds/.
 */
@OptIn(ExperimentalForeignApi::class)
class SoundRepositoryIos(private val engine: AudioEngineIos) {

    private val _bundled = mutableListOf<SoundInfo>()
    private val _user    = mutableListOf<SoundInfo>()

    val sounds: List<SoundInfo>  get() = _bundled + _user
    val defaultSoundId: String   get() = _bundled.firstOrNull()?.id ?: "default"

    fun loadAll() {
        loadBundled()
        loadUser()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadBundled() {
        _bundled.clear()
        // WAVs live in the "raw" folder reference copied into the bundle
        val urls = NSBundle.mainBundle.URLsForResourcesWithExtension(
            "wav", subdirectory = "raw"
        ) as? List<NSURL> ?: return

        for (url in urls.sortedBy { it.lastPathComponent }) {
            val name = url.lastPathComponent ?: continue
            if (!name.startsWith("snd_")) continue
            val id    = name.removeSuffix(".wav")
            val label = id.removePrefix("snd_").replace('_', ' ')
            val path  = url.path ?: continue
            if (engine.loadSamplePath(id, path)) {
                _bundled += SoundInfo(id = id, label = label, isUser = false)
            }
        }
    }

    private fun loadUser() {
        _user.clear()
        val dir  = userSoundsDir ?: return
        val fm   = NSFileManager.defaultManager
        @Suppress("UNCHECKED_CAST")
        val files = fm.contentsOfDirectoryAtPath(dir, null) as? List<String> ?: return

        val supportedFiles = files.filter(::isSupportedAudioFile)
            .sorted()
            .distinctBy { audioFileStem(it).lowercase() }
        for (name in supportedFiles) {
            val id    = audioFileStem(name)
            val label = id.replace('_', ' ')
            val path  = "$dir/$name"
            if (engine.loadSamplePath(id, path)) {
                _user += SoundInfo(id = id, label = label, isUser = true)
            }
        }
    }

    fun importSound(sourcePath: String): SoundInfo? {
        val dir  = userSoundsDir ?: return null
        val name = sourcePath.substringAfterLast('/')
        if (!isSupportedAudioFile(name)) return null
        val dest = "$dir/$name"
        val fm   = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(dest)) fm.removeItemAtPath(dest, null)
        fm.copyItemAtPath(sourcePath, dest, null)
        val id    = audioFileStem(name)
        val label = id.replace('_', ' ')
        if (!engine.loadSamplePath(id, dest)) {
            fm.removeItemAtPath(dest, null)
            return null
        }
        removeAlternateFormats(dir, id, name)
        val info = SoundInfo(id = id, label = label, isUser = true)
        loadUser()
        return info
    }

    fun deleteUserSound(soundId: String) {
        val dir = userSoundsDir ?: return
        val fm = NSFileManager.defaultManager
        @Suppress("UNCHECKED_CAST")
        val files = fm.contentsOfDirectoryAtPath(dir, null) as? List<String> ?: emptyList()
        files.filter {
            isSupportedAudioFile(it) && audioFileStem(it).equals(soundId, ignoreCase = true)
        }
            .forEach { fm.removeItemAtPath("$dir/$it", null) }
        loadUser()
    }

    private fun removeAlternateFormats(dir: String, soundId: String, keepName: String) {
        val fm = NSFileManager.defaultManager
        @Suppress("UNCHECKED_CAST")
        val files = fm.contentsOfDirectoryAtPath(dir, null) as? List<String> ?: return
        files.filter {
            it != keepName && isSupportedAudioFile(it) &&
                audioFileStem(it).equals(soundId, ignoreCase = true)
        }.forEach { fm.removeItemAtPath("$dir/$it", null) }
    }

    private val userSoundsDir: String? get() {
        @Suppress("UNCHECKED_CAST")
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return null
        val dir = "$docs/user_sounds"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        return dir
    }
}
