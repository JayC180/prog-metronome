package com.jayc180.rhythmengine

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jayc180.rhythmengine.audio.AudioEngineAndroid
import com.jayc180.rhythmengine.audio.SoundEntry
import com.jayc180.rhythmengine.audio.SoundImportHelper
import com.jayc180.rhythmengine.audio.SoundInfo
import com.jayc180.rhythmengine.audio.SoundRepository
import com.jayc180.rhythmengine.audio.SoundSource
import com.jayc180.rhythmengine.builder.TrackBuilder
import com.jayc180.rhythmengine.model.SoundConfig
import com.jayc180.rhythmengine.model.SoundMap
import com.jayc180.rhythmengine.scheduler.Transport
import com.jayc180.rhythmengine.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RhythmViewModel(application: Application) : AndroidViewModel(application) {

    val audioEngine     = AudioEngineAndroid()
    val soundRepository = SoundRepository(application)

    private val _soundEntries = MutableStateFlow<List<SoundEntry>>(emptyList())
    private val _sounds       = MutableStateFlow<List<SoundInfo>>(emptyList())
    val sounds: StateFlow<List<SoundInfo>> = _sounds.asStateFlow()

    private val _soundsLoaded = MutableStateFlow(false)
    val soundsLoaded: StateFlow<Boolean> = _soundsLoaded.asStateFlow()

    sealed class ImportState {
        object Idle    : ImportState()
        object Loading : ImportState()
        data class Success(val label: String) : ImportState()
        data class Error(val message: String) : ImportState()
    }
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _playheads = MutableStateFlow<Map<String, Int>>(emptyMap())
    val playheads: StateFlow<Map<String, Int>> = _playheads.asStateFlow()

    private val _globalDefaultSoundId = MutableStateFlow("default")
    val globalDefaultSoundId: String get() = _globalDefaultSoundId.value

    val themeManager = ThemeManager(application)

    private val transport = Transport(soundMap = SoundMap())

    val builder = TrackBuilder(
        initialBpm = 120.0,
        transport  = transport,
        scope      = viewModelScope,
    )

    private val prefs by lazy {
        application.getSharedPreferences("rhythm_prefs", Context.MODE_PRIVATE)
    }

    init {
        audioEngine.open()
        wireAudio()
        wirePlayhead()
        wirePlaybackService()
        loadSoundsAsync()
        builder.pairedBracketDelete =
            prefs.getBoolean("paired_bracket_delete", true)
    }

    private fun wireAudio() {
        transport.onAudioEvent = { event ->
            event.soundId?.let { id -> audioEngine.trigger(id, event.volume, event.absoluteNanos) }
        }
    }

    private fun wirePlayhead() {
        viewModelScope.launch {
            transport.firedEvents.collect { event ->
                _playheads.value = _playheads.value + (event.trackId to event.trackItemIndex)
            }
        }
    }

    // keep notification in sync with builder state
    private fun wirePlaybackService() {
        viewModelScope.launch {
            builder.state.collect { state ->
//                android.util.Log.d("RhythmDebug",
//                    "wirePlaybackService: isPlaying=${state.isPlaying}")
                PlaybackService.update(getApplication(), state.isPlaying)
            }
        }
    }

    private fun loadSoundsAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            val discovered = soundRepository.discoverSounds()
            _soundEntries.value = discovered
            _sounds.value = discovered.toSoundInfoList()

            val defaultId = soundRepository.defaultSoundId(discovered)
            _globalDefaultSoundId.value = defaultId
            builder.setDefaultSoundId(defaultId)

            soundRepository.loadAll(discovered, audioEngine)
            transport.updateSoundMap(SoundMap(discovered.map { entry ->
                SoundConfig(soundId = entry.id, resourceUri = entry.id)
            }))
            _soundsLoaded.value = true
        }
    }

    fun setGlobalDefaultSound(soundId: String) {
        _globalDefaultSoundId.value = soundId
        builder.setDefaultSoundId(soundId)
    }

    fun importSoundFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportState.Loading
            val file = SoundImportHelper.importFromUri(
                context = getApplication(),
                uri     = uri,
                destDir = soundRepository.userSoundDir,
            )
            if (file == null) {
                _importState.value = ImportState.Error("Could not read file — make sure it's a WAV")
                return@launch
            }
            val entry = SoundEntry(
                id     = file.nameWithoutExtension,
                label  = file.nameWithoutExtension.replace('_', ' '),
                source = SoundSource.UserFile(file),
            )
            val failed = soundRepository.loadAll(listOf(entry), audioEngine)
            if (entry.id in failed) {
                file.delete()
                _importState.value = ImportState.Error("File loaded but couldn't be parsed as WAV")
                return@launch
            }
            val newEntries = _soundEntries.value + entry
            _soundEntries.value = newEntries
            _sounds.value = newEntries.toSoundInfoList()
            transport.updateSoundMap(SoundMap(newEntries.map { e ->
                SoundConfig(soundId = e.id, resourceUri = e.id)
            }))
            _importState.value = ImportState.Success(entry.label)
            kotlinx.coroutines.delay(3000)
            _importState.value = ImportState.Idle
        }
    }

    fun deleteUserSound(soundId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = _soundEntries.value.firstOrNull { it.id == soundId } ?: return@launch
            if (entry.source !is SoundSource.UserFile) return@launch
            if (soundRepository.deleteUserSound(entry)) {
                val newEntries = _soundEntries.value.filter { it.id != soundId }
                _soundEntries.value = newEntries
                _sounds.value = newEntries.toSoundInfoList()
                if (_globalDefaultSoundId.value == soundId) {
                    val newDefault = newEntries.firstOrNull {
                        it.source is SoundSource.Bundled
                    }?.id ?: "default"
                    setGlobalDefaultSound(newDefault)
                }
                transport.updateSoundMap(SoundMap(newEntries.map { e ->
                    SoundConfig(soundId = e.id, resourceUri = e.id)
                }))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        builder.stop()
        audioEngine.stopAll()
        audioEngine.close()
    }

    private fun List<SoundEntry>.toSoundInfoList(): List<SoundInfo> =
        map { entry ->
            SoundInfo(
                id     = entry.id,
                label  = entry.label,
                isUser = entry.source is SoundSource.UserFile,
            )
        }

    fun savePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}