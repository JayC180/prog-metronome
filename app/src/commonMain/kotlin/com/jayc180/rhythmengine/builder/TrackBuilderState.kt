package com.jayc180.rhythmengine.builder

import com.jayc180.rhythmengine.core.Rational

sealed class TrackItem {

    data class Beat(
        val displayNum:   Int,
        val displayDenom: Int,
        val active:       Boolean = true,
        val soundId:      String? = null,
        val volume:       Float   = 1.0f,
    ) : TrackItem() {
        val duration: Rational get() = Rational(displayNum.toLong(), displayDenom.toLong())
        val label: String get() = if (displayDenom == 1) "$displayNum" else "$displayNum/$displayDenom"
        val isRest: Boolean get() = !active
    }

    object BracketOpen  : TrackItem() { override fun toString() = "[" }
    object BracketClose : TrackItem() { override fun toString() = "]" }

    data class Repeat(val count: Int) : TrackItem() {
        val isInfinite: Boolean get() = count == INFINITE
        override fun toString() = if (isInfinite) "×∞" else "×$count"
        companion object { const val INFINITE = -1 }
    }

    data class Modulation(val p: Int, val q: Int) : TrackItem() {
        val ratio: Rational get() = Rational(p.toLong(), q.toLong())
        override fun toString() = "×$p/$q"
    }

    data class SetBpm(val bpm: Double) : TrackItem() {
        override fun toString() = "=${bpm.toInt()}"
    }
}

data class TrackDraft(
    val id:     String,
    val label:  String,
    val denom:  Int             = 4,
    val items:  List<TrackItem> = emptyList(),
    val muted:  Boolean         = false,
    val soloed: Boolean         = false,
    val defaultSoundId: String?        = null,   // null = use global default
) {
    val bracketDepth: Int get() {
        var d = 0
        items.forEach { when (it) { is TrackItem.BracketOpen -> d++; is TrackItem.BracketClose -> d--; else -> Unit } }
        return d
    }

    fun bracketDepthAt(index: Int): Int {
        var d = 0
        items.take(index + 1).forEach { when (it) { is TrackItem.BracketOpen -> d++; is TrackItem.BracketClose -> d--; else -> Unit } }
        return d
    }

    fun matchingOpenIndex(closeIndex: Int): Int? {
        var depth = 0
        for (i in closeIndex downTo 0) {
            when (items[i]) {
                is TrackItem.BracketClose -> depth++
                is TrackItem.BracketOpen  -> { depth--; if (depth == 0) return i }
                else -> Unit
            }
        }
        return null
    }

    fun matchingCloseIndex(openIndex: Int): Int? {
        var depth = 0
        for (i in openIndex until items.size) {
            when (items[i]) {
                is TrackItem.BracketOpen  -> depth++
                is TrackItem.BracketClose -> { depth--; if (depth == 0) return i }
                else -> Unit
            }
        }
        return null
    }

    // if already has inf item; can't add anymore
    val hasInfiniteRepeat: Boolean get() =
        items.any { it is TrackItem.Repeat && (it as TrackItem.Repeat).isInfinite }

    val isEmpty: Boolean get() = items.isEmpty()
    val size: Int get() = items.size
}

sealed class PlaybackState {
    object Stopped : PlaybackState()
    object Playing : PlaybackState()
}

sealed class InputMode {
    object Normal : InputMode()
    object Denom  : InputMode()
    data class RepeatCount(val pending: Int? = null) : InputMode()
    data class Edit(val firstDigit: Boolean = true) : InputMode()
}

data class TrackBuilderState(
    val tracks:           List<TrackDraft>,
    val activeTrackIndex: Int,
    val bpm:              Double,
    val inputMode:        InputMode     = InputMode.Normal,
    val playbackState:    PlaybackState = PlaybackState.Stopped,
    val cursorIndex:      Int?          = null,
    val runningBpm:       Double?       = null,
) {
    val activeTrack: TrackDraft? get() = tracks.getOrNull(activeTrackIndex)
    val isPlaying: Boolean get() = playbackState == PlaybackState.Playing
    val isStopped: Boolean get() = playbackState == PlaybackState.Stopped
    val canEdit:   Boolean get() = !isPlaying
    val isDenomMode:   Boolean get() = inputMode is InputMode.Denom
    val isRepeatMode:  Boolean get() = inputMode is InputMode.RepeatCount
    val isEditMode:    Boolean get() = inputMode is InputMode.Edit
    val pendingRepeat: Int?    get() = (inputMode as? InputMode.RepeatCount)?.pending
    val activeDenom: Int get() = activeTrack?.denom ?: 4
    val cursorItem: TrackItem? get() = cursorIndex?.let { activeTrack?.items?.getOrNull(it) }
    val insertionIndex: Int get() = (cursorIndex?.plus(1)) ?: (activeTrack?.items?.size ?: 0)
    val displayBpm: Double get() = if (isPlaying) runningBpm ?: bpm else bpm

    private val trackHasInfiniteRepeat: Boolean get() =
        activeTrack?.hasInfiniteRepeat == true

    val canOpenBracket: Boolean get() =
        canEdit && activeTrack != null &&
                inputMode is InputMode.Normal && !insertionBlockedByInfinite

    val canCloseBracket: Boolean get() {
        if (!canEdit || activeTrack == null || inputMode !is InputMode.Normal) return false
        if (insertionBlockedByInfinite) return false
        val track = activeTrack!!
        if (track.items.isEmpty()) return false
        // no []
        if (track.items.getOrNull(insertionIndex - 1) is TrackItem.BracketOpen) return false
        val upTo = (insertionIndex - 1).coerceAtLeast(0).coerceAtMost(track.items.lastIndex)
        return track.bracketDepthAt(upTo) > 0
    }

    val canSetRepeat: Boolean get() {
        if (!canEdit || activeTrack == null || inputMode !is InputMode.Normal) return false
        if (insertionBlockedByInfinite) return false
        val track    = activeTrack!!
        val closeIdx = findRelevantClose(track) ?: return false
        return !hasRepeatAfterClose(track, closeIdx)
    }

    val canSetInfiniteRepeat: Boolean get() {
        if (!canSetRepeat) return false
        val track    = activeTrack!!
        val closeIdx = findRelevantClose(track) ?: return false
        // only modifiers allowed
        val afterClose = track.items.drop(closeIdx + 1)
        val onlyModifiers = afterClose.all {
            it is TrackItem.Repeat || it is TrackItem.Modulation || it is TrackItem.SetBpm
        }
        if (!onlyModifiers) return false
        // no mm inside the bracket
        val openIdx = track.matchingOpenIndex(closeIdx) ?: return false
        return (openIdx + 1 until closeIdx).none { track.items[it] is TrackItem.Modulation }
    }

    val canInsertTempo: Boolean get() {
        if (!canEdit || activeTrack == null || inputMode !is InputMode.Normal) return false
        if (insertionBlockedByInfinite) return false
        val track = activeTrack!!
        if (track.items.isEmpty()) return true
        val prev = track.items.getOrNull(insertionIndex - 1)
        return prev !is TrackItem.Modulation && prev !is TrackItem.SetBpm
    }

    val canEnterBeats: Boolean get() =
        canEdit && activeTrack != null &&
                inputMode is InputMode.Normal &&
                !insertionBlockedByInfinite

    private val insertionBlockedByInfinite: Boolean get() {
        val track = activeTrack ?: return false
        val infIdx = track.items.indexOfFirst {
            it is TrackItem.Repeat && (it as TrackItem.Repeat).isInfinite
        }
        if (infIdx < 0) return false
        // find the ] that inf is attached to
        var closeIdx = infIdx - 1
        while (closeIdx >= 0 && track.items[closeIdx].let {
                it is TrackItem.Modulation || it is TrackItem.SetBpm
            }) closeIdx--
        if (closeIdx < 0 || track.items[closeIdx] !is TrackItem.BracketClose) return false
        // block insertion at or after ]
        return insertionIndex > closeIdx
    }

    // available for beat, repeat, mod, setbpm
    val canEdit_item: Boolean get() =
        canEdit && !isPlaying && cursorItem.let {
            it is TrackItem.Beat ||
                    (it is TrackItem.Repeat && !(it as TrackItem.Repeat).isInfinite) ||
                    it is TrackItem.Modulation ||
                    it is TrackItem.SetBpm
        }

    fun buildErrors(): List<String> {
        val errors = mutableListOf<String>()
        tracks.forEachIndexed { i, track ->
            val depth = track.bracketDepth
            if (depth > 0) errors += "Track ${i+1}: $depth unclosed bracket(s)"
            if (depth < 0) errors += "Track ${i+1}: unmatched ']'"
            track.items.forEachIndexed { idx, item ->
                if (item is TrackItem.BracketOpen) {
                    val closeIdx = track.matchingCloseIndex(idx)
                    if (closeIdx != null && closeIdx == idx + 1)
                        errors += "Track ${i+1}: empty bracket at position $idx"
                }
            }
            val infRepeatIdx = track.items.indexOfFirst {
                it is TrackItem.Repeat && (it as TrackItem.Repeat).isInfinite
            }
            if (infRepeatIdx >= 0) {
                if (infRepeatIdx != track.items.lastIndex)
                    errors += "Track ${i+1}: ×∞ must be the last item"
                val closeIdx = infRepeatIdx - 1
                val openIdx  = if (closeIdx >= 0) track.matchingOpenIndex(closeIdx) else null
                if (openIdx != null) {
                    val hasMm = (openIdx+1 until closeIdx).any { track.items[it] is TrackItem.Modulation }
                    if (hasMm) errors += "Track ${i+1}: mm not allowed inside ×∞ (use =bpm)"
                }
            }
        }
        return errors
    }

    private fun findRelevantClose(track: TrackDraft): Int? {
        val idx = cursorIndex
        // cursor directly on ]
        if (idx != null && track.items.getOrNull(idx) is TrackItem.BracketClose) return idx

        // walk back from insertion point, skip all modifiers, find ]
        var searchIdx = (insertionIndex - 1)
        while (searchIdx >= 0) {
            when (track.items.getOrNull(searchIdx)) {
                is TrackItem.BracketClose -> return searchIdx
                is TrackItem.Repeat, is TrackItem.Modulation, is TrackItem.SetBpm -> searchIdx--
                else -> break
            }
        }
        return null
    }

    private fun hasRepeatAfterClose(track: TrackDraft, closeIndex: Int): Boolean {
        var i = closeIndex + 1
        while (i < track.items.size && (track.items[i] is TrackItem.Repeat ||
                    track.items[i] is TrackItem.Modulation || track.items[i] is TrackItem.SetBpm)) {
            if (track.items[i] is TrackItem.Repeat) return true
            i++
        }
        return false
    }
}
