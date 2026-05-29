package com.jayc180.rhythmengine.audio

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Imports custom wav file into user sound dir
 */
object SoundImportHelper {

    /**
     * Copy a content URI into [destDir] as a WAV file.
     * Returns the new File on success, null on failure.
     * [destDir] should be SoundRepository.userSoundDir.
     */
    fun importFromUri(context: Context, uri: Uri, destDir: File): File? {
        return try {
            // Resolve display name from content resolver
            val fileName = resolveFileName(context, uri) ?: return null

            val safeName = if (fileName.endsWith(".wav", ignoreCase = true))
                fileName else "$fileName.wav"

            val destFile = File(destDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            destFile
        } catch (e: Exception) {
            android.util.Log.e("SoundImportHelper", "Import failed: ${e.message}")
            null
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
}