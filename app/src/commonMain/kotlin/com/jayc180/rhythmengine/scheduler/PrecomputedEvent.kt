package com.jayc180.rhythmengine.scheduler

/**
 * A single schedulable event with its fire time pre-computed in nanoseconds
 * from track start. No tempo context needed at runtime — everything is baked in.
 *
 * [offsetNanos]    absolute offset from track origin (System.nanoTime() at play())
 * [soundId]        null = rest/silence, still tracked for playhead
 * [trackItemIndex] which TrackItem in the flat list this event corresponds to
 *                  (for UI highlight — maps directly, no conversion needed)
 * [firedCount]     monotonic counter — for infinite groups, use firedCount % passSize
 */
data class PrecomputedEvent(
    val offsetNanos:    Long,
    val soundId:        String?,
    val trackItemIndex: Int,
    val firedCount:     Int,      // index within one loop pass
    val volume:         Float = 1.0f,
) {
    val isRest:   Boolean get() = soundId == null
    val isAudible: Boolean get() = soundId != null
}

/**
 * Complete result of interpreting one TrackDraft
 *
 * [trackId]          matches TrackDraft.id
 * [events]           all audible+rest events for ONE full loop, in order
 * [loopNanos]        total duration of one loop in nanoseconds
 * [infinitePassEvents] if the track ends with inf, this is the event list for
 *                    one pass of the infinite group. Empty otherwise
 * [infinitePassNanos] duration of one infinite pass. 0 if no infinite group
 * [tempoMapNanos]    list of (offsetNanos, bpm) pairs — for display during playback
 */
data class InterpretResult(
    val trackId:            String,
    val events:             List<PrecomputedEvent>,
    val loopNanos:          Long,
    val infinitePassEvents: List<PrecomputedEvent> = emptyList(),
    val infinitePassNanos:  Long                   = 0L,
    val tempoMapNanos:      List<Pair<Long, Double>> = emptyList(),
    val muted:              Boolean                = false,
    val soloed:             Boolean                = false,
)