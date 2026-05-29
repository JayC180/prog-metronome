package com.jayc180.rhythmengine.scheduler

/**
 * One fire-able event — produced by StreamClock, consumed by AudioDispatcher and UI.
 *
 * soundId is the opaque key into SoundMap. null = silent slot (UI tracking only).
 * The audio dispatcher resolves soundId → SoundConfig → play. This type knows nothing
 * about audio resources, volume, or anything else in SoundConfig.
 */
data class ScheduledEvent(
    val absoluteNanos: Long,    // wall-clock fire time (System.nanoTime())
    val soundId: String?,       // null = rest — no audio, but emitted for UI cursor
    val trackId: String,        // which Track this belongs to (was streamId)
    val trackItemIndex: Int,     // direct index into TrackDraft.items — no mapping needed
    val firedTotal:     Long,    // monotonic across all loops
    val passIndex:      Int,     // position within current loop pass
    val passSize:       Int,     // total events in current loop pass
    val volume:         Float = 1.0f,
) : Comparable<ScheduledEvent> {

    val isRest: Boolean get() = soundId == null
    val isActive: Boolean get() = soundId != null
    val isAudible: Boolean get() = soundId != null

    override fun compareTo(other: ScheduledEvent): Int =
        absoluteNanos.compareTo(other.absoluteNanos)
}