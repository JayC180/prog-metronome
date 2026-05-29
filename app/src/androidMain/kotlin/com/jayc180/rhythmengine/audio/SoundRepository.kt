package com.jayc180.rhythmengine.audio

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Sound source — sound file source
 * Bundled = read-only res/raw defaults
 * User = user-imported, stored in app's files dir
 */
sealed class SoundSource {
    data class Bundled(val rawName: String) : SoundSource()
    data class UserFile(val file: File) : SoundSource()
}

data class SoundEntry(
    val id:     String,       // unique key used in BeatNode and SoundConfig
    val label:  String,       // display name
    val source: SoundSource,
)

/**
 * SoundRepository — discovers all available sounds and exposes them to the UI
 * Default sound, then bundled sound, then user imported; alphabetically
 */
class SoundRepository(private val context: Context) {
    val userSoundDir: File by lazy {
        File(context.filesDir, "user_sounds").also { it.mkdirs() }
    }

    // called at startup
    fun discoverSounds(): List<SoundEntry> {
        val bundled = discoverBundled()
        val user    = discoverUserFiles()
        return bundled + user
    }

    // fall back to "default"?
    fun defaultSoundId(sounds: List<SoundEntry>): String =
        sounds.firstOrNull { it.source is SoundSource.Bundled }?.id ?: "default"

    fun loadAll(sounds: List<SoundEntry>, audioEngine: AudioEngineAndroid): List<String> {
        val failed = mutableListOf<String>()
        sounds.forEach { entry ->
            val ok = when (val src = entry.source) {
                is SoundSource.Bundled -> {
                    val resId = context.resources.getIdentifier(src.rawName, "raw", context.packageName)
                    if (resId == 0) {
                        Log.e("SoundRepository", "res/raw/${src.rawName} not found")
                        false
                    } else {
                        val afd = context.resources.openRawResourceFd(resId)
                        val result = audioEngine.loadSampleFd(
                            entry.id,
                            afd.parcelFileDescriptor.fd,
                            afd.startOffset,
                            afd.length,
                        )
                        afd.close()
                        result
                    }
                }
                is SoundSource.UserFile -> {
                    audioEngine.loadSampleFile(entry.id, src.file.absolutePath)
                }
            }
            if (ok==false) {
                Log.w("SoundRepository", "Failed to load: ${entry.id}")
                failed += entry.id
            } else {
                Log.d("SoundRepository", "Loaded: ${entry.id} (${entry.label})")
            }
        }
        return failed
    }

    /**
     * Copies imported wav file into usr sound dir
     * Returns new SoundEntry, null if fail
     */
    fun importUserSound(sourceFile: File): SoundEntry? {
        if (!sourceFile.exists() || !sourceFile.name.endsWith(".wav", ignoreCase = true)) return null
        val dest = File(userSoundDir, sourceFile.name)
        return try {
            sourceFile.copyTo(dest, overwrite = true)
            SoundEntry(
                id     = dest.nameWithoutExtension,
                label  = dest.nameWithoutExtension.replace('_', ' '),
                source = SoundSource.UserFile(dest),
            )
        } catch (e: Exception) {
            android.util.Log.e("SoundRepository", "Import failed: ${e.message}")
            null
        }
    }

    fun deleteUserSound(entry: SoundEntry): Boolean {
        val src = entry.source as? SoundSource.UserFile ?: return false
        return src.file.delete().also {
            if (it) Log.d("SoundRepository", "Deleted: ${entry.id}")
        }
    }

    /**
     * Auto-detect bundled WAV files via R.raw reflection.
     * Finds all fields in the R.raw class whose names don't start with underscore,
     * sorted alphabetically.
     */
    private fun discoverBundled(): List<SoundEntry> {
        return try {
            val rawClass = Class.forName("${context.packageName}.R\$raw")

            rawClass.fields
                .filter { it.name.startsWith("snd_") }
                .sortedBy { it.name }
                .map { field ->
                    val name = field.name

                    SoundEntry(
                        id = name,
                        label = name.removePrefix("snd_").replace('_', ' '),
                        source = SoundSource.Bundled(name),
                    )
                }
        } catch (e: Exception) {
            Log.e("SoundRepository", "Failed to discover bundled sounds: ${e.message}")
            emptyList()
        }
    }

    private fun discoverUserFiles(): List<SoundEntry> {
        return userSoundDir
            .listFiles { f -> f.extension.equals("wav", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.map { file ->
                SoundEntry(
                    id     = file.nameWithoutExtension,
                    label  = file.nameWithoutExtension.replace('_', ' '),  // no prefix to strip
                    source = SoundSource.UserFile(file),
                )
            } ?: emptyList()
    }
}