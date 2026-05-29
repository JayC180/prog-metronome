package com.jayc180.rhythmengine

import com.jayc180.rhythmengine.audio.AudioEngineIos
import com.jayc180.rhythmengine.audio.SoundInfo
import com.jayc180.rhythmengine.audio.SoundRepositoryIos
import com.jayc180.rhythmengine.builder.TrackBuilder
import com.jayc180.rhythmengine.model.SoundConfig
import com.jayc180.rhythmengine.model.SoundMap
import com.jayc180.rhythmengine.persistence.ProjectSerializer
import com.jayc180.rhythmengine.persistence.ProjectStorageIos
import com.jayc180.rhythmengine.scheduler.Transport
import com.jayc180.rhythmengine.ui.AppViewModel
import com.jayc180.rhythmengine.ui.theme.ThemeManagerIos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RhythmViewModelIos {

    val scope           = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val audioEngine     = AudioEngineIos()
    val soundRepository = SoundRepositoryIos(audioEngine)
    val themeManager    = ThemeManagerIos()
    val storage         = ProjectStorageIos()

    private val _sounds             = MutableStateFlow<List<SoundInfo>>(emptyList())
    val sounds: StateFlow<List<SoundInfo>> = _sounds.asStateFlow()

    private val _playheads          = MutableStateFlow<Map<String, Int>>(emptyMap())
    val playheads: StateFlow<Map<String, Int>> = _playheads.asStateFlow()

    private val _globalDefaultSoundId = MutableStateFlow("default")
    val globalDefaultSoundId: String get() = _globalDefaultSoundId.value

    // ── Project state ──────────────────────────────────────────────────────────

    private val _projectName   = MutableStateFlow("Untitled")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _currentPath   = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    private val _hasSession    = MutableStateFlow(false)
    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    private val _projects      = MutableStateFlow<List<ProjectStorageIos.ProjectInfo>>(emptyList())
    val projects: StateFlow<List<ProjectStorageIos.ProjectInfo>> = _projects.asStateFlow()

    private val _importState   = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    sealed class ImportState {
        object Idle    : ImportState()
        object Loading : ImportState()
        data class Success(val label: String) : ImportState()
        data class Error(val msg: String)     : ImportState()
    }

    // ── Transport / builder ───────────────────────────────────────────────────

    private val transport = Transport(soundMap = SoundMap())

    val builder = TrackBuilder(
        initialBpm = 120.0,
        transport  = transport,
        scope      = scope,
    )

    init {
        audioEngine.open()
        wireAudio()
        wirePlayhead()
        loadSoundsAsync()
        _hasSession.value = storage.hasSession
        storage.startAutoSave(scope, builder.state)
        val prefs = platform.Foundation.NSUserDefaults.standardUserDefaults
        if (prefs.objectForKey("paired_bracket_delete") != null)
            builder.pairedBracketDelete = prefs.boolForKey("paired_bracket_delete")
    }

    private fun wireAudio() {
        transport.onAudioEvent = { event ->
            event.soundId?.let { id -> audioEngine.trigger(id, event.volume, event.absoluteNanos) }
        }
    }

    private fun wirePlayhead() {
        scope.launch {
            transport.firedEvents.collect { event ->
                _playheads.value = _playheads.value + (event.trackId to event.trackItemIndex)
            }
        }
    }

    private fun loadSoundsAsync() {
        scope.launch {
            soundRepository.loadAll()
            val list      = soundRepository.sounds
            val defaultId = soundRepository.defaultSoundId
            _sounds.value               = list
            _globalDefaultSoundId.value = defaultId
            builder.setDefaultSoundId(defaultId)
            transport.updateSoundMap(SoundMap(list.map { SoundConfig(soundId = it.id, resourceUri = it.id) }))
        }
    }

    // ── Sound management ──────────────────────────────────────────────────────

    fun setPairedBracketDelete(value: Boolean) {
        builder.pairedBracketDelete = value
        platform.Foundation.NSUserDefaults.standardUserDefaults.setBool(value, "paired_bracket_delete")
    }

    fun setGlobalDefaultSound(soundId: String) {
        _globalDefaultSoundId.value = soundId
        builder.setDefaultSoundId(soundId)
    }

    fun deleteUserSound(soundId: String) {
        scope.launch {
            soundRepository.deleteUserSound(soundId)
            val list = soundRepository.sounds
            _sounds.value = list
            if (globalDefaultSoundId == soundId) {
                val newDefault = list.firstOrNull { !it.isUser }?.id ?: "default"
                setGlobalDefaultSound(newDefault)
            }
            transport.updateSoundMap(SoundMap(list.map { SoundConfig(soundId = it.id, resourceUri = it.id) }))
        }
    }

    fun importSoundFromPath(path: String) {
        _importState.value = ImportState.Loading
        scope.launch {
            val info = soundRepository.importSound(path)
            if (info != null) {
                val list = soundRepository.sounds
                _sounds.value = list
                transport.updateSoundMap(SoundMap(list.map { SoundConfig(soundId = it.id, resourceUri = it.id) }))
                _importState.value = ImportState.Success(info.label)
            } else {
                _importState.value = ImportState.Error("Import failed — make sure the file is a .wav")
            }
            delay(3000L)
            _importState.value = ImportState.Idle
        }
    }

    // ── Project management ────────────────────────────────────────────────────

    fun saveAs(name: String) {
        scope.launch {
            val path = storage.save(builder.state.value, name)
            if (path != null) {
                _currentPath.value = path
                _projectName.value = name
                storage.clearSession()
                _hasSession.value = false
                refreshProjects()
            }
        }
    }

    fun save() {
        val path = _currentPath.value
        val name = _projectName.value
        if (path == null) { saveAs(name); return }
        scope.launch {
            storage.overwrite(builder.state.value, path, name)
            storage.clearSession()
            _hasSession.value = false
        }
    }

    fun loadProject(path: String) {
        scope.launch {
            val state = storage.load(path) ?: return@launch
            val name  = storage.safeRead(path)?.let { ProjectSerializer.peekName(it) }
                ?: path.substringAfterLast('/').removeSuffix(".rhy")
            builder.restoreState(state)
            _currentPath.value = path
            _projectName.value = name
            storage.clearSession()
            _hasSession.value = false
        }
    }

    fun importProjectFromPath(path: String) {
        scope.launch {
            val json  = storage.safeRead(path) ?: return@launch
            val state = storage.loadFromString(json) ?: return@launch
            builder.restoreState(state)
            _currentPath.value = null
            _projectName.value = ProjectSerializer.peekName(json) ?: "Imported"
        }
    }

    fun newProject() {
        builder.restoreState(
            com.jayc180.rhythmengine.builder.TrackBuilderState(
                tracks           = listOf(com.jayc180.rhythmengine.builder.TrackBuilder.newTrackDraft(0)),
                activeTrackIndex = 0,
                bpm              = 120.0,
            )
        )
        _currentPath.value = null
        _projectName.value = "Untitled"
    }

    fun deleteProject(info: ProjectStorageIos.ProjectInfo) {
        scope.launch {
            storage.delete(info.path)
            if (_currentPath.value == info.path) _currentPath.value = null
            refreshProjects()
        }
    }

    fun refreshProjects() {
        scope.launch {
            _projects.value = storage.listProjects()
        }
    }

    fun restoreSession() {
        scope.launch {
            val state = storage.loadSession() ?: return@launch
            builder.restoreState(state)
            _projectName.value = "Restored session"
            _hasSession.value  = false
        }
    }

    fun discardSession() {
        storage.clearSession()
        _hasSession.value = false
    }

    // ── Compose bridge ────────────────────────────────────────────────────────

    fun buildAppViewModel(onSettingsClick: () -> Unit) = AppViewModel(
        builder               = builder,
        audio                 = audioEngine,
        playheads             = playheads,
        sounds                = sounds,
        onSettingsClick       = onSettingsClick,
        _globalDefaultSoundId = globalDefaultSoundId,
        onSetGlobalDefault    = { id -> setGlobalDefaultSound(id) },
    )

    fun dispose() {
        builder.stop()
        audioEngine.stopAll()
        audioEngine.close()
        scope.cancel()
    }
}
