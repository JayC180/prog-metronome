package com.jayc180.rhythmengine.scheduler

import com.jayc180.rhythmengine.util.MinHeap

/**
 * All timing is pre-computed. This class just iter a List<PrecomputedEvent>
 * and schedules them at (originNanos + event.offsetNanos), looping by advancing
 * originNanos by loopNanos on each loop
 *
 * For tracks with an infinite group, the non-infinite prefix plays once,
 * then the infinite pass loops forever
 */
class StreamClock(private val result: InterpretResult) {

    private var phase:          Phase  = Phase.PREFIX
    private var eventIndex:     Int    = 0
    private var loopOriginNanos: Long  = 0L   // advances by loopNanos or infPassNanos each iteration
    private var firedTotal:     Long   = 0L   // monotonic across all loops

    val trackId: String get() = result.trackId
    val isRunning: Boolean get() = phase != Phase.STOPPED

    fun fillUntil(horizonNanos: Long, originNanos: Long, queue: MinHeap<ScheduledEvent>) {
        if (!isRunning) return
        var guard = 500_000

        while (guard-- > 0) {
            val (currentEvents, loopSize) = when (phase) {
                Phase.PREFIX   -> result.events to result.loopNanos
                Phase.INFINITE -> result.infinitePassEvents to result.infinitePassNanos
                Phase.STOPPED  -> return
            }

            if (currentEvents.isEmpty()) {
                advancePhase(currentEvents.size)
                continue
            }

            val event = currentEvents.getOrNull(eventIndex) ?: run {
                advancePhase(currentEvents.size)
                continue
            }

            val fireNanos = originNanos + loopOriginNanos + event.offsetNanos

            if (fireNanos >= horizonNanos) return

            queue.offer(ScheduledEvent(
                absoluteNanos  = fireNanos,
                soundId        = event.soundId,
                trackId        = result.trackId,
                trackItemIndex = event.trackItemIndex,
                firedTotal     = firedTotal,
                passIndex      = eventIndex,
                passSize       = currentEvents.size,
                volume         = event.volume,
            ))

            firedTotal++
            eventIndex++
        }
    }

    private fun advancePhase(passSize: Int) {
        eventIndex = 0
        when (phase) {
            Phase.PREFIX -> {
                loopOriginNanos += result.loopNanos
                phase = if (result.infinitePassEvents.isNotEmpty())
                    Phase.INFINITE
                else {
                    // No infinite group — loop the whole prefix forever
                    Phase.PREFIX
                }
            }
            Phase.INFINITE -> {
                // Loop the infinite pass forever
                loopOriginNanos += result.infinitePassNanos
            }
            Phase.STOPPED -> Unit
        }
    }

    fun reset() {
        phase            = Phase.PREFIX
        eventIndex       = 0
        loopOriginNanos  = 0L
        firedTotal       = 0L
    }

    fun debugState(): String =
        "StreamClock(${result.trackId} phase=$phase idx=$eventIndex " +
                "loopOrigin=${loopOriginNanos}ns fired=$firedTotal)"

    private enum class Phase { PREFIX, INFINITE, STOPPED }
}
