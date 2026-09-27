package com.jayc180.rhythmengine.audio

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Imports a supported custom audio file into the user sound directory.
 */
object SoundImportHelper {

    /**
     * Copy a WAV, MP3, or FLAC content URI into [destDir].
     * Returns the new File on success, null on failure.
     * [destDir] should be SoundRepository.userSoundDir.
     */
    fun importFromUri(context: Context, uri: Uri, destDir: File): File? {
        var tempFile: File? = null
        return try {
            // Resolve display name from content resolver
            val displayName = resolveFileName(context, uri) ?: return null
            val baseName = displayName.substringAfterLast('/').substringAfterLast('\\')
            val extension = supportedAudioExtension(baseName)
                ?: extensionForMimeType(context.contentResolver.getType(uri))
                ?: return null
            val safeName = if (supportedAudioExtension(baseName) != null)
                baseName else "$baseName.$extension"

            val destFile = File(destDir, safeName)
            val stagingFile = File(destDir, "$safeName.importing").also { it.delete() }
            tempFile = stagingFile

            context.contentResolver.openInputStream(uri)?.use { input ->
                stagingFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMPORTED_AUDIO_BYTES) {
                            throw IllegalArgumentException("Audio file exceeds import size limit")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: return null

            if (destFile.exists() && !destFile.delete()) return null
            if (!stagingFile.renameTo(destFile)) return null
            tempFile = null
            destFile
        } catch (e: Exception) {
            android.util.Log.e("SoundImportHelper", "Import failed: ${e.message}")
            null
        } finally {
            tempFile?.delete()
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        // Try content resolver display name first
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) {
                return cursor.getString(nameIdx)
            }
        }
        // fall back to last path seg
        return uri.lastPathSegment
    }

    private fun extensionForMimeType(mimeType: String?): String? = when (mimeType?.lowercase()) {
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "wav"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/flac", "audio/x-flac" -> "flac"
        else -> null
    }
}
