package com.jayc180.rhythmengine.persistence

import android.content.Context
import com.jayc180.rhythmengine.builder.TrackBuilderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ProjectStorage — handles all file I/O for .rhy project files.
 *
 * All projects (including session) live in one place:
 *   getExternalFilesDir()/projects/
 *
 * All files accessible; no private/public split
 *
 * Session auto-save:
 *   A special file "_session.rhy" is written every [SESSION_SAVE_INTERVAL_MS].
 *   On app launch, if this file exists, the user is offered to restore it.
 *   On clean stop (user saves manually), session file is deleted.
 */
class ProjectStorage(context: Context) {

    private val projectDir: File = File(
        context.getExternalFilesDir(null), "projects"
    ).also { it.mkdirs() }

    companion object {
        const val SESSION_FILENAME        = "_session.rhy"
        const val SESSION_SAVE_INTERVAL_MS = 30_000L   // auto-save every 30 sec
        const val FILE_EXTENSION          = "rhy"
    }

    data class ProjectInfo(
        val file:      File,
        val name:      String,
        val bpm:       Double?,
        val modifiedAt: Long,   // System.currentTimeMillis()
    )

    /**
     * List all saved projects, sorted by most recently modified.
     * Excludes the session file.
     */
    fun listProjects(): List<ProjectInfo> {
        return projectDir
            .listFiles { f -> f.extension == FILE_EXTENSION && f.name != SESSION_FILENAME }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file ->
                val content = safeRead(file) ?: return@mapNotNull null
                ProjectInfo(
                    file       = file,
                    name       = ProjectSerializer.peekName(content) ?: file.nameWithoutExtension,
                    bpm        = ProjectSerializer.peekBpm(content),
                    modifiedAt = file.lastModified(),
                )
            } ?: emptyList()
    }

    /**
     * Save a project with [name].
     * Filename is sanitized and timestamped to avoid collisions.
     * Returns the saved File, or null on failure.
     */
    fun save(state: TrackBuilderState, name: String): File? {
        val json     = ProjectSerializer.serialize(state, name)
        val filename = buildFilename(name)
        val file     = File(projectDir, filename)
        return if (safeWrite(file, json)) file else null
    }

    /**
     * Overwrite an existing project file in place.
     * Used by "Save" (not "Save As").
     */
    fun overwrite(state: TrackBuilderState, file: File, name: String): Boolean {
        val json = ProjectSerializer.serialize(state, name)
        return safeWrite(file, json)
    }

    fun delete(file: File): Boolean = file.delete()

    /**
     * Load a project from a File.
     * Returns null if the file can't be read or parsed.
     */
    fun load(file: File): TrackBuilderState? {
        val content = safeRead(file) ?: return null
        return ProjectSerializer.deserializeOrNull(content)
    }

    /**
     * Load a project from raw json
     */
    fun loadFromString(json: String): TrackBuilderState? =
        ProjectSerializer.deserializeOrNull(json)

    /**
     * Save the current state as the session file.
     * Called periodically from a coroutine
     */
    fun saveSession(state: TrackBuilderState) {
        val json = ProjectSerializer.serialize(state, "_session")
        safeWrite(File(projectDir, SESSION_FILENAME), json)
    }

    /**
     * Load the session file if it exists.
     * Returns null if no session or session is corrupt.
     */
    fun loadSession(): TrackBuilderState? {
        val file = File(projectDir, SESSION_FILENAME)
        if (!file.exists()) return null
        val content = safeRead(file) ?: return null
        return ProjectSerializer.deserializeOrNull(content)
    }

    val hasSession: Boolean get() = File(projectDir, SESSION_FILENAME).exists()

    fun clearSession() { File(projectDir, SESSION_FILENAME).delete() }

    /**
     * Start periodic session auto-save on [scope].
     * Collects the latest state from [stateFlow] every [SESSION_SAVE_INTERVAL_MS].
     * Returns the Job so caller can cancel it on stop/clear.
     */
    fun startAutoSave(
        scope: CoroutineScope,
        stateFlow: Flow<TrackBuilderState>,
    ): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            delay(SESSION_SAVE_INTERVAL_MS)
            val state = stateFlow.firstOrNull() ?: continue
            saveSession(state)
        }
    }

    fun readRaw(file: File): String? = safeRead(file)

    /**
     * Import a .rhy file from an arbitrary path
     * Copies it into the projects directory and returns the new File
     */
    fun importFile(sourceFile: File): File? {
        if (!sourceFile.exists()) return null
        val dest = File(projectDir, buildFilename(sourceFile.nameWithoutExtension))
        return try {
            sourceFile.copyTo(dest, overwrite = false)
            dest
        } catch (e: Exception) { null }
    }

    private fun safeWrite(file: File, content: String): Boolean = try {
        file.writeText(content, Charsets.UTF_8)
        true
    } catch (e: Exception) {
        android.util.Log.e("ProjectStorage", "Write failed: ${file.name} — ${e.message}")
        false
    }

    private fun safeRead(file: File): String? = try {
        file.readText(Charsets.UTF_8)
    } catch (e: Exception) {
        android.util.Log.e("ProjectStorage", "Read failed: ${file.name} — ${e.message}")
        null
    }

    private fun buildFilename(name: String): String {
        val safe      = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().take(40)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${safe}_${timestamp}.$FILE_EXTENSION"
    }
}