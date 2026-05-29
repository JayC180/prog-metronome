package com.jayc180.rhythmengine.builder

import com.jayc180.rhythmengine.platform.nanoNow

/**
 * Records tap timestamps and computes BPM from the mean interval
 * between all consecutive taps in the current sequence
 *
 * Resets automatically if the gap since the last tap exceeds [resetAfterMs].
 * Minimum 2 taps required before a BPM is returned.
 * Maximum [maxTaps] taps kept — oldest are dropped
 */
class TapTempoCalculator(
    private val resetAfterMs: Long = 3000L,
    private val maxTaps:      Int  = 8,
) {
    private val taps = ArrayDeque<Long>()  // timestamps in ms

    /**
     * Record a tap at the current time
     * Returns the new BPM, or null if fewer than 2 taps recorded yet.
     */
    fun tap(nowMs: Long = nanoNow() / 1_000_000L): Double? {
        // Reset if too long since last tap
        val last = taps.lastOrNull()
        if (last != null && nowMs - last > resetAfterMs) {
            taps.clear()
        }

        taps.addLast(nowMs)

        while (taps.size > maxTaps) taps.removeFirst()

        if (taps.size < 2) return null

        // get mean
        val intervals = mutableListOf<Long>()
        for (i in 1 until taps.size) {
            intervals += taps[i] - taps[i - 1]
        }
        val meanMs = intervals.sum().toDouble() / intervals.size
        return 60_000.0 / meanMs
    }

    fun reset() = taps.clear()

    val tapCount: Int get() = taps.size
}