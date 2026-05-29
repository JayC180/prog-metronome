package com.jayc180.rhythmengine.builder

import com.jayc180.rhythmengine.scheduler.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class TrackBuilder(
    initialBpm:            Double = 120.0,
    private val transport: Transport? = null,
    private val scope:     CoroutineScope? = null,
) {
    private val _state = MutableStateFlow(
        TrackBuilderState(
            tracks           = listOf(newTrackDraft(0)),
            activeTrackIndex = 0,
            bpm              = initialBpm,
        )
    )
    val state: StateFlow<TrackBuilderState> = _state.asStateFlow()

    private val _lastBuildResults = MutableStateFlow<List<com.jayc180.rhythmengine.scheduler.InterpretResult>>(emptyList())
    val lastBuildResults: StateFlow<List<com.jayc180.rhythmengine.scheduler.InterpretResult>> = _lastBuildResults.asStateFlow()

    private val _lastBuildErrors = MutableStateFlow<List<String>>(emptyList())
    val lastBuildErrors: StateFlow<List<String>> = _lastBuildErrors.asStateFlow()

    private var _defaultSoundId: String = "default"
    val defaultSoundId: String get() = _defaultSoundId
    fun setDefaultSoundId(id: String) { _defaultSoundId = id }

    private val current get() = _state.value
    private fun emit(s: TrackBuilderState) { _state.value = s }

    private val _pairedBracketDelete = MutableStateFlow(true)
    val pairedBracketDeleteFlow: StateFlow<Boolean> = _pairedBracketDelete.asStateFlow()
    var pairedBracketDelete: Boolean
        get() = _pairedBracketDelete.value
        set(value) { _pairedBracketDelete.value = value }

    // insertion helpers

    private fun insertionPoint(track: TrackDraft): Int =
        current.cursorIndex?.let { it + 1 } ?: track.items.size

    private fun insertItem(item: TrackItem) {
        val track = current.activeTrack ?: return
        val idx   = insertionPoint(track)
        val newItems = track.items.toMutableList().also { it.add(idx, item) }
        val newTracks = current.tracks.toMutableList()
        newTracks[current.activeTrackIndex] = track.copy(items = newItems)
        emit(current.copy(tracks = newTracks, cursorIndex = idx))
    }

    // cursor
    fun setCursor(index: Int?) {
        val track   = current.activeTrack ?: return
        val clamped = index?.coerceIn(0, track.items.lastIndex)
        // exit edit mode when cursor moves
        val newMode = if (current.isEditMode) InputMode.Normal else current.inputMode
        emit(current.copy(cursorIndex = clamped, inputMode = newMode))
    }

    fun moveCursorPrev() {
        val track = current.activeTrack ?: return
        val idx   = current.cursorIndex
        val newMode = if (current.isEditMode) InputMode.Normal else current.inputMode
        emit(current.copy(
            cursorIndex = when { idx == null -> track.items.lastIndex; idx <= 0 -> null; else -> idx - 1 },
            inputMode   = newMode,
        ))
    }

    fun moveCursorNext() {
        val track = current.activeTrack ?: return
        val idx   = current.cursorIndex
        val newMode = if (current.isEditMode) InputMode.Normal else current.inputMode
        emit(current.copy(
            cursorIndex = when { idx == null -> null; idx >= track.items.lastIndex -> null; else -> idx + 1 },
            inputMode   = newMode,
        ))
    }

    // track managementstuff

    fun addTrack() {
        val idx = current.tracks.size
        emit(current.copy(tracks = current.tracks + newTrackDraft(idx),
            activeTrackIndex = idx, cursorIndex = null, inputMode = InputMode.Normal))
    }

    fun copyTrack() {
        val source = current.activeTrack ?: return
        val idx  = current.tracks.size
        val base = newTrackDraft(idx)   // new id + label "Track N"
        val copy = base.copy(items = source.items, denom = source.denom, defaultSoundId = source.defaultSoundId)
        emit(current.copy(tracks = current.tracks + copy,
            activeTrackIndex = idx, cursorIndex = null, inputMode = InputMode.Normal))
    }

    fun deleteTrack(index: Int) {
        if (current.tracks.size <= 1) return
        val newTracks = current.tracks.toMutableList().also { it.removeAt(index) }
        // renumber tracks whose labels r still auto-generated "Track N"
        val renumbered = newTracks.mapIndexed { i, track ->
            if (track.label.matches(Regex("Track \\d+"))) track.copy(label = "Track ${i + 1}")
            else track
        }
        val newActive = current.activeTrackIndex.coerceAtMost(renumbered.lastIndex)
        emit(current.copy(tracks = renumbered, activeTrackIndex = newActive, cursorIndex = null))
    }

    fun setActiveTrack(index: Int) {
        if (index !in current.tracks.indices) return
        emit(current.copy(activeTrackIndex = index, cursorIndex = null, inputMode = InputMode.Normal))
    }

    fun setTrackMuted(index: Int, muted: Boolean)   = updateTrack(index) { it.copy(muted = muted) }
    fun setTrackSoloed(index: Int, soloed: Boolean) = updateTrack(index) { it.copy(soloed = soloed) }
    fun setTrackLabel(index: Int, label: String)    = updateTrack(index) { it.copy(label = label) }

    fun setBpm(bpm: Double) {
        if (bpm <= 0) return
        emit(current.copy(bpm = bpm.coerceIn(1.0, 999.0)))
    }

    fun toggleDenomMode() {
        if (!current.canEdit) return
        emit(current.copy(inputMode = when (current.inputMode) {
            is InputMode.Denom  -> InputMode.Normal
            is InputMode.Normal -> InputMode.Denom
            else -> return
        }))
    }

    fun setDenom(n: Int) {
        if (n <= 0) return
        updateActiveTrack { it.copy(denom = n) }
        emit(current.copy(inputMode = InputMode.Normal))
    }

    // per track
    fun setTrackDefaultSound(trackIndex: Int, soundId: String) {
        updateTrack(trackIndex) { it.copy(defaultSoundId = soundId) }
    }

    // maybe put in use?
    fun clearTrackDefaultSound(trackIndex: Int) {
        updateTrack(trackIndex) { it.copy(defaultSoundId = null) }
    }

    // use track default if set
    fun enterBeat(numerator: Int) {
        if (!current.canEnterBeats || numerator <= 0) return
        val track = current.activeTrack ?: return
        val soundId = track.defaultSoundId ?: _defaultSoundId // global fallback
        insertItem(TrackItem.Beat(
            displayNum   = numerator,
            displayDenom = track.denom,
            soundId      = soundId,
        ))
    }

    // update updateBeatAt for an existing beat:
    fun setBeatSound(beatIndex: Int, soundId: String) {
        val track = current.activeTrack ?: return
        val item  = track.items.getOrNull(beatIndex) as? TrackItem.Beat ?: return
        val newItems = track.items.toMutableList().also {
            it[beatIndex] = item.copy(soundId = soundId)
        }
        updateActiveTrack { it.copy(items = newItems) }
    }

    fun openBracket() {
        if (!current.canOpenBracket) return
        insertItem(TrackItem.BracketOpen)
    }

    fun closeBracket() {
        if (!current.canCloseBracket) return
        insertItem(TrackItem.BracketClose)
    }

    fun toggleRepeatMode() {
        if (!current.canEdit) return
        emit(current.copy(inputMode = when (current.inputMode) {
            is InputMode.RepeatCount -> InputMode.Normal
            is InputMode.Normal      -> { if (!current.canSetRepeat) return; InputMode.RepeatCount() }
            else -> return
        }))
    }

    // imm commit digit 1-9
    fun repeatDigit(n: Int) {
        if (current.inputMode !is InputMode.RepeatCount || n < 1 || n > 9) return
        commitRepeat(n)
    }

    // when > 9
    fun setRepeatCustom(n: Int) {
        if (n < 1) return
        commitRepeat(n)
    }

    // only when canSetInfiniteRepeat
    // called from RepeatDialog when inf used
    // if in edit mode on repeat item, replace
    // if in RepeatCount mode, insert new
    fun setRepeatInfinite() {
        val track = current.activeTrack ?: return

        // case 1: editing an existing repeat item -> replace
        val cursorItem = current.cursorItem
        if (cursorItem is TrackItem.Repeat && !cursorItem.isInfinite) {
            replaceRepeatInfinite()
            return
        }

        // case 2: inserting new inf after ]
        val closeIdx = findRelevantClosePublic(track) ?: return
        val afterClose = track.items.drop(closeIdx + 1)
        if (afterClose.any {
                it !is TrackItem.Repeat && it !is TrackItem.Modulation && it !is TrackItem.SetBpm
            }) return
        val openIdx = track.matchingOpenIndex(closeIdx) ?: return
        if ((openIdx + 1 until closeIdx).any { track.items[it] is TrackItem.Modulation }) return
        commitRepeat(TrackItem.Repeat.INFINITE)
    }

    // replaces repeat item at cursor
    fun replaceRepeat(count: Int) {
        val idx   = current.cursorIndex ?: return
        val track = current.activeTrack ?: return
        if (track.items.getOrNull(idx) !is TrackItem.Repeat) return
        val newItems = track.items.toMutableList()
        newItems[idx] = TrackItem.Repeat(count)
        updateActiveTrack { it.copy(items = newItems) }
        emit(current.copy(inputMode = InputMode.Normal))
    }

    fun replaceRepeatInfinite() {
        val idx   = current.cursorIndex ?: return
        val track = current.activeTrack ?: return
        if (track.items.getOrNull(idx) !is TrackItem.Repeat) return
        // still do struct check like insert; find corresponding ] it's attached to
        val closeIdx = idx - 1
        if (closeIdx < 0 || track.items[closeIdx] !is TrackItem.BracketClose) return
        // no mm inside the bracket
        val openIdx = track.matchingOpenIndex(closeIdx) ?: return
        if ((openIdx + 1 until closeIdx).any { track.items[it] is TrackItem.Modulation }) return
        // no items after this repeat (except modifiers?)
        val afterRepeat = track.items.drop(idx + 1)
        if (afterRepeat.any { it !is TrackItem.Modulation && it !is TrackItem.SetBpm }) return

        val newItems = track.items.toMutableList()
        newItems[idx] = TrackItem.Repeat(TrackItem.Repeat.INFINITE)
        updateActiveTrack { it.copy(items = newItems) }
        emit(current.copy(inputMode = InputMode.Normal))
    }

    // replaces beat value at cursor
    fun replaceBeat(numerator: Int) {
        if (numerator <= 0) return
        val idx   = current.cursorIndex ?: return
        val track = current.activeTrack ?: return
        val item  = track.items.getOrNull(idx) as? TrackItem.Beat ?: return
        val newItems = track.items.toMutableList()
        newItems[idx] = item.copy(displayNum = numerator, displayDenom = track.denom)
        updateActiveTrack { it.copy(items = newItems) }
        emit(current.copy(inputMode = InputMode.Normal))
    }

    private fun findRelevantCloseForInfinite(track: TrackDraft): Int? {
        // walk back from end of track, skipping modifiers, find ]
        var searchIdx = track.items.lastIndex
        while (searchIdx >= 0) {
            when (track.items[searchIdx]) {
                is TrackItem.BracketClose -> return searchIdx
                is TrackItem.Repeat, is TrackItem.Modulation, is TrackItem.SetBpm -> searchIdx--
                else -> return null
            }
        }
        return null
    }

    private fun findRelevantClosePublic(track: TrackDraft): Int? {
        var searchIdx = track.items.lastIndex
        while (searchIdx >= 0) {
            when (track.items[searchIdx]) {
                is TrackItem.BracketClose -> return searchIdx
                is TrackItem.Repeat, is TrackItem.Modulation, is TrackItem.SetBpm -> searchIdx--
                else -> return null
            }
        }
        return null
    }

    private fun commitRepeat(count: Int) {
        val track = current.activeTrack ?: return
        val idx   = insertionPoint(track)
        val newItems = track.items.toMutableList().also { it.add(idx, TrackItem.Repeat(count)) }
        val newTracks = current.tracks.toMutableList()
        newTracks[current.activeTrackIndex] = track.copy(items = newItems)
        emit(current.copy(tracks = newTracks, cursorIndex = idx, inputMode = InputMode.Normal))
    }

    // tempo stuff
    fun commitModulation(p: Int, q: Int) {
        if (!current.canInsertTempo || p <= 0 || q <= 0) return
        insertItem(TrackItem.Modulation(p, q))
    }

    fun commitSetBpm(bpm: Double) {
        if (!current.canInsertTempo || bpm <= 0) return
        insertItem(TrackItem.SetBpm(bpm.coerceIn(1.0, 999.0)))
    }

    // edit mode

    /**
     * Toggle edit mode.
     * Beat       -> enters Edit, numpad replaces value
     * Repeat     -> enters Edit, numpad accumulates count (first digit clears)
     * SetBpm     -> enters Edit, numpad accumulates bpm (first digit clears)
     * Modulation -> does NOT enter Edit; caller should show mm popup instead
     */
    fun toggleEditMode() {
        if (!current.canEdit) return
        when (current.inputMode) {
            is InputMode.Edit -> emit(current.copy(inputMode = InputMode.Normal))
            is InputMode.Normal -> {
                if (current.cursorItem is TrackItem.Modulation || current.cursorItem is TrackItem.SetBpm) return
                beginEdit()
            }
            else -> Unit
        }
    }

    /**
     * when cursor is on a Modulation or SetBpm item
     * UI shows appropriate popup when edit instead of entering edit mode
     */
    val cursorIsModulation: Boolean get() =
        current.cursorItem is TrackItem.Modulation || current.cursorItem is TrackItem.SetBpm

    private fun beginEdit() {
        emit(current.copy(inputMode = InputMode.Edit(firstDigit = true)))
    }

    /**
     * Process a numpad digit in edit mode
     * Beat:   replace displayNum with n
     * Repeat: replace directly or??? accumulate — newCount = current * 10 + n
     * SetBpm: accumulate — newBpm  = current * 10 + n
     */
    fun editDigit(n: Int) {
        val mode = current.inputMode as? InputMode.Edit ?: return
        val idx   = current.cursorIndex ?: return
        val track = current.activeTrack ?: return
        val newItems = track.items.toMutableList()
        when (val item = track.items.getOrNull(idx)) {
            is TrackItem.Beat -> {
                // replaces directly, adopt current denom
                newItems[idx] = item.copy(displayNum = n, displayDenom = track.denom)
                updateActiveTrack { it.copy(items = newItems) }
                emit(current.copy(inputMode = InputMode.Normal))   // auto-exit
            }
            is TrackItem.Repeat -> {
                // same as beat, custom if larger than 9
                if (n < 1 || n > 9) return
                newItems[idx] = item.copy(count = n)
                updateActiveTrack { it.copy(items = newItems) }
                emit(current.copy(inputMode = InputMode.Normal))
                // old accumulation
                // val newCount = if (mode.firstDigit) n
                //                else (item.count * 10 + n).coerceAtMost(9999)
                // newItems[idx] = item.copy(count = newCount)
                // updateActiveTrack { it.copy(items = newItems) }
                // emit(current.copy(inputMode = InputMode.Edit(firstDigit = false)))
            }
            is TrackItem.SetBpm -> {
                val newBpm = if (mode.firstDigit) n.toDouble()
                else (item.bpm.toInt() * 10 + n).toDouble().coerceAtMost(999.0)
                newItems[idx] = item.copy(bpm = newBpm)
                updateActiveTrack { it.copy(items = newItems) }
                emit(current.copy(inputMode = InputMode.Edit(firstDigit = false)))
            }
            else -> Unit
        }
    }

    fun exitEditMode() {
        if (current.isEditMode) emit(current.copy(inputMode = InputMode.Normal))
    }

    fun replaceModulation(index: Int, p: Int, q: Int) {
        if (p <= 0 || q <= 0) return
        val track = current.activeTrack ?: return
        if (track.items.getOrNull(index) !is TrackItem.Modulation) return
        val newItems = track.items.toMutableList()
        newItems[index] = TrackItem.Modulation(p, q)
        updateActiveTrack { it.copy(items = newItems) }
        emit(current.copy(inputMode = InputMode.Normal))
    }

    fun replaceSetBpm(index: Int, bpm: Double) {
        if (bpm <= 0) return
        val track = current.activeTrack ?: return
        if (track.items.getOrNull(index) !is TrackItem.SetBpm) return
        val newItems = track.items.toMutableList()
        newItems[index] = TrackItem.SetBpm(bpm.coerceIn(1.0, 999.0))
        updateActiveTrack { it.copy(items = newItems) }
        emit(current.copy(inputMode = InputMode.Normal))
    }

    // deletion

    fun deleteAt(index: Int) {
        val track = current.activeTrack ?: return
        if (index !in track.items.indices) return
        val toRemove = mutableSetOf(index)
        if (pairedBracketDelete) {
            when (track.items[index]) {
                is TrackItem.BracketOpen -> {
                    val closeIdx = track.matchingCloseIndex(index)
                    if (closeIdx != null) {
                        toRemove += closeIdx
                        var i = closeIdx + 1
                        while (i < track.items.size && track.items[i].let {
                                it is TrackItem.Repeat || it is TrackItem.Modulation || it is TrackItem.SetBpm
                            }) { toRemove += i; i++ }
                    }
                }
                is TrackItem.BracketClose -> {
                    track.matchingOpenIndex(index)?.let { toRemove += it }
                    var i = index + 1
                    while (i < track.items.size && track.items[i].let {
                            it is TrackItem.Repeat || it is TrackItem.Modulation || it is TrackItem.SetBpm
                        }) { toRemove += i; i++ }
                }
                else -> Unit
            }
        }
        val newItems   = track.items.filterIndexed { i, _ -> i !in toRemove }
        val maxRemoved = toRemove.max()
        val newCursor  = when {
            newItems.isEmpty()         -> null
            maxRemoved < newItems.size -> maxRemoved
            else                       -> newItems.lastIndex
        }
        val newTracks = current.tracks.toMutableList()
        newTracks[current.activeTrackIndex] = track.copy(items = newItems)
        emit(current.copy(tracks = newTracks, cursorIndex = newCursor,
            inputMode = InputMode.Normal))
    }

    fun deleteLast() {
        val track = current.activeTrack ?: return
        if (track.items.isEmpty()) return
        deleteAt(track.items.lastIndex)
    }

    fun deleteSelected() {
        val idx = current.cursorIndex ?: return
        deleteAt(idx)
    }

    fun updateBeatAt(index: Int, update: (TrackItem.Beat) -> TrackItem.Beat) {
        val track = current.activeTrack ?: return
        val item  = track.items.getOrNull(index) as? TrackItem.Beat ?: return
        val newItems = track.items.toMutableList().also { it[index] = update(item) }
        updateActiveTrack { it.copy(items = newItems) }
    }

    // playback

    fun play() {
        if (current.isPlaying) return
        val errors = current.buildErrors()
        if (errors.isNotEmpty()) {
            _lastBuildErrors.value = errors
            return
        }
        _lastBuildErrors.value = emptyList()
        val t  = transport ?: run {
            emit(current.copy(playbackState = PlaybackState.Playing, inputMode = InputMode.Normal))
            return
        }
        val sc = scope ?: return
        val results = current.tracks.map { draft ->
            interpretTrackDraft(draft, current.bpm, _defaultSoundId)
                .copy(muted = draft.muted, soloed = draft.soloed)
        }
        _lastBuildResults.value = results
        t.updateTracks(results)
        t.start(sc)
        emit(current.copy(
            playbackState = PlaybackState.Playing,
            inputMode     = InputMode.Normal,
            cursorIndex   = null,
            runningBpm    = null,
        ))
    }

    fun stop() {
        if (current.isStopped) return
        transport?.stop()
        emit(current.copy(playbackState = PlaybackState.Stopped, runningBpm = null))
    }

    fun togglePlayStop() { if (current.isPlaying) stop() else play() }

    fun restoreState(s: TrackBuilderState) {
        emit(s.copy(playbackState = PlaybackState.Stopped, inputMode = InputMode.Normal,
            cursorIndex = null, runningBpm = null))
    }

    fun clearBuildErrors() { _lastBuildErrors.value = emptyList() }

    fun updateRunningBpm(bpm: Double) { /* no-op: pre-computed */ }

    private fun updateActiveTrack(update: (TrackDraft) -> TrackDraft) =
        updateTrack(current.activeTrackIndex, update)

    private fun updateTrack(index: Int, update: (TrackDraft) -> TrackDraft) {
        if (index !in current.tracks.indices) return
        val newTracks = current.tracks.toMutableList()
        newTracks[index] = update(newTracks[index])
        emit(current.copy(tracks = newTracks))
    }

    companion object {
        private var counter = 0
        fun newTrackDraft(index: Int) = TrackDraft(
            id = "track_${index}_${counter++}", label = "Track ${index + 1}")
    }
}