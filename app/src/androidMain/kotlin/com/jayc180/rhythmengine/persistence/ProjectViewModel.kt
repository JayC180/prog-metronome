package com.jayc180.rhythmengine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jayc180.rhythmengine.builder.TrackBuilderState
import com.jayc180.rhythmengine.persistence.ProjectStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * ProjectViewModel — sits alongside RhythmViewModel and handles all
 * project file lifecycle: save, load, session restore, auto-save.
 *
 * Kept separate from RhythmViewModel so audio and persistence concerns
 * don't mix. Both ViewModels are created in MainActivity and passed to App().
 *
 * Session flow:
 *   App start → check hasSession → offer restore or start fresh
 *   While editing → auto-save every 30s (background)
 *   User saves → writeNamedFile, clearSession
 *   User loads → readFile, restore state into TrackBuilder
 */
class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    val storage = ProjectStorage(application)

    // null for unsaved new project
    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile.asStateFlow()

    private val _projectName = MutableStateFlow("Untitled")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _hasSession = MutableStateFlow(storage.hasSession)
    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectStorage.ProjectInfo>>(emptyList())
    val projects: StateFlow<List<ProjectStorage.ProjectInfo>> = _projects.asStateFlow()

    sealed class StorageEvent {
        data class SavedOk(val file: File)   : StorageEvent()
        data class SaveFailed(val msg: String) : StorageEvent()
        data class LoadedOk(val state: TrackBuilderState) : StorageEvent()
        data class LoadFailed(val msg: String) : StorageEvent()
    }

    private val _events = MutableSharedFlow<StorageEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StorageEvent> = _events.asSharedFlow()

    private var autoSaveJob: Job? = null

    /**
     * Start session auto-save. Called from MainActivity after the builder is set up
     * Pass the builder's state flow so the latest state is always saved
     */
    fun startAutoSave(stateFlow: Flow<TrackBuilderState>) {
        autoSaveJob?.cancel()
        autoSaveJob = storage.startAutoSave(viewModelScope, stateFlow)
    }

    // prob dont need it
    fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    /** Load the session file. Emits LoadedOk or LoadFailed. */
    fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = storage.loadSession()
            if (state != null) {
                _events.emit(StorageEvent.LoadedOk(state))
                _projectName.value = "Restored session"
            } else {
                _events.emit(StorageEvent.LoadFailed("Session file is corrupt"))
            }
        }
    }

    fun discardSession() {
        storage.clearSession()
        _hasSession.value = false
    }

    /**
     * Save with [name]. Creates a new file each time.
     * After save, the session file is cleared (work is now persisted).
     */
    fun saveAs(state: TrackBuilderState, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = storage.save(state, name)
            if (file != null) {
                _currentFile.value  = file
                _projectName.value  = name
                storage.clearSession()
                _hasSession.value   = false
                _events.emit(StorageEvent.SavedOk(file))
                refreshProjectList()
            } else {
                _events.emit(StorageEvent.SaveFailed("Could not write file"))
            }
        }
    }

    /**
     * Overwrite the current file (Save, not Save As).
     * Falls back to saveAs if no current file.
     */
    fun save(state: TrackBuilderState) {
        val file = _currentFile.value
        val name = _projectName.value
        if (file == null) {
            saveAs(state, name)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = storage.overwrite(state, file, name)
            if (ok) {
                storage.clearSession()
                _hasSession.value = false
                _events.emit(StorageEvent.SavedOk(file))
            } else {
                _events.emit(StorageEvent.SaveFailed("Could not overwrite file"))
            }
        }
    }

    fun load(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = storage.load(file)
            if (state != null) {
                _currentFile.value = file
                _projectName.value = ProjectStorage.run {
                    // peek name for display
                    file.readText()
                        .let { ProjectSerializer_peekName(it) }
                        ?: file.nameWithoutExtension
                }
                _events.emit(StorageEvent.LoadedOk(state))
            } else {
                _events.emit(StorageEvent.LoadFailed("File is corrupt or unsupported version"))
            }
        }
    }

    fun loadFromRaw(json: String) {
        viewModelScope.launch {
            val state = storage.loadFromString(json)
            if (state != null) {
                _currentFile.value = null
                _projectName.value = "Imported"
                _events.emit(StorageEvent.LoadedOk(state))
            } else {
                _events.emit(StorageEvent.LoadFailed("Invalid .rhy file"))
            }
        }
    }

    fun deleteProject(info: ProjectStorage.ProjectInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.delete(info.file)
            if (_currentFile.value == info.file) _currentFile.value = null
            refreshProjectList()
        }
    }

    fun refreshProjectList() {
        viewModelScope.launch(Dispatchers.IO) {
            _projects.value = storage.listProjects()
        }
    }

    // long ass func name
    private fun ProjectSerializer_peekName(json: String): String? =
        com.jayc180.rhythmengine.persistence.ProjectSerializer.peekName(json)
}