package com.jayc180.rhythmengine.persistence

import com.jayc180.rhythmengine.builder.TrackBuilderState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import platform.Foundation.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
class ProjectStorageIos {

    val projectDir: String = run {
        @Suppress("UNCHECKED_CAST")
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: ""
        "$docs/projects".also {
            NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null)
        }
    }

    companion object {
        const val SESSION_FILENAME         = "_session.rhy"
        const val SESSION_SAVE_INTERVAL_MS = 30_000L
        const val FILE_EXTENSION           = "rhy"
    }

    data class ProjectInfo(
        val path:       String,
        val name:       String,
        val bpm:        Double?,
        val modifiedAt: Long,
    )

    fun listProjects(): List<ProjectInfo> {
        @Suppress("UNCHECKED_CAST")
        val files = (NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(projectDir, null) as? List<String>)
            ?.filter { it.endsWith(".$FILE_EXTENSION") && it != SESSION_FILENAME }
            ?: return emptyList()

        return files.mapNotNull { filename ->
            val path    = "$projectDir/$filename"
            val content = safeRead(path) ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val modDate = (NSFileManager.defaultManager
                .attributesOfItemAtPath(path, null)
                ?.get(NSFileModificationDate) as? NSDate)
                ?.timeIntervalSince1970?.toLong()?.times(1000L) ?: 0L
            ProjectInfo(
                path       = path,
                name       = ProjectSerializer.peekName(content) ?: filename.removeSuffix(".$FILE_EXTENSION"),
                bpm        = ProjectSerializer.peekBpm(content),
                modifiedAt = modDate,
            )
        }.sortedByDescending { it.modifiedAt }
    }

    fun save(state: TrackBuilderState, name: String): String? {
        val json     = ProjectSerializer.serialize(state, name)
        val filename = buildFilename(name)
        val path     = "$projectDir/$filename"
        return if (safeWrite(path, json)) path else null
    }

    fun overwrite(state: TrackBuilderState, path: String, name: String): Boolean {
        val json = ProjectSerializer.serialize(state, name)
        return safeWrite(path, json)
    }

    fun delete(path: String) =
        NSFileManager.defaultManager.removeItemAtPath(path, null)

    fun load(path: String): TrackBuilderState? {
        val content = safeRead(path) ?: return null
        return ProjectSerializer.deserializeOrNull(content)
    }

    fun loadFromString(json: String): TrackBuilderState? =
        ProjectSerializer.deserializeOrNull(json)

    fun saveSession(state: TrackBuilderState) {
        val json = ProjectSerializer.serialize(state, "_session")
        safeWrite("$projectDir/$SESSION_FILENAME", json)
    }

    fun loadSession(): TrackBuilderState? {
        val content = safeRead("$projectDir/$SESSION_FILENAME") ?: return null
        return ProjectSerializer.deserializeOrNull(content)
    }

    val hasSession: Boolean
        get() = NSFileManager.defaultManager.fileExistsAtPath("$projectDir/$SESSION_FILENAME")

    fun clearSession() {
        NSFileManager.defaultManager.removeItemAtPath("$projectDir/$SESSION_FILENAME", null)
    }

    fun startAutoSave(scope: CoroutineScope, stateFlow: Flow<TrackBuilderState>): Job =
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(SESSION_SAVE_INTERVAL_MS)
                val state = stateFlow.firstOrNull() ?: continue
                saveSession(state)
            }
        }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    fun safeRead(path: String): String? {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        return NSString.stringWithContentsOfFile(
            path, encoding = NSUTF8StringEncoding, error = null
        ) as? String
    }

    fun safeWrite(path: String, content: String): Boolean {
        val bytes = content.encodeToByteArray()
        return try {
            bytes.usePinned { pinned ->
                val f = fopen(path, "wb") ?: return false
                fwrite(pinned.addressOf(0), 1UL, bytes.size.toULong(), f)
                fclose(f)
            }
            true
        } catch (_: Exception) { false }
    }

    private fun buildFilename(name: String): String {
        val safe      = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().take(40)
        val formatter = NSDateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        val ts = formatter.stringFromDate(NSDate())
        return "${safe}_${ts}.$FILE_EXTENSION"
    }
}
