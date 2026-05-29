package com.jayc180.rhythmengine.core

/**
 * Encapsulates all tempo-related state for a single stream
 *
 * Key designs:
 *
 * 1. pulseUnit decouples "what 1 pulse means" from BPM
 *
 * 2. modulation accumulates multiplicatively
 *    Each metric modulation is a ratio applied to the effective pulse length
 *
 * 3. toNanos() is the ONLY place floats appear in the engine
 *    All other arithmetic stays in Rational
 *
 * Immutable — all mutations return a new TempoContext
 */
data class TempoContext(
    // Pulses per minute. Pulse defined by pulseUnit
    val bpm: Double,
    val pulseUnit: Rational = Rational.ONE,

    /**
     * Accumulated metric modulation ratio
     * Starts at 1/1 (no modulation). Each call to withModulation() multiplies
     *
     * ex chain:
     *   start:           modulation = 1/1  (bpm=120, pulse=500ms)
     *   withMod(2/3):    modulation = 2/3  (pulse now 333ms, effectively bpm=180)
     *   withMod(3/4):    modulation = 1/2  (pulse now 250ms, effectively bpm=240)
     */
    val modulation: Rational = Rational.ONE,
) {

    // derived state

    // effective pulse length in ns after all modulation applied
    val pulseNanos: Long get() {
        val basePulseNanos = (60.0 / bpm) * 1_000_000_000.0
        // scale by pulseUnit x modulation — the ONLY float math
        val effectiveRatio = (pulseUnit * modulation).toDouble()
        return (basePulseNanos * effectiveRatio).toLong()
    }

    // BPM for UI display
    val effectiveBpm: Double get() =
        bpm / (pulseUnit * modulation).toDouble()

    // duration conversion

    /**
     * Convert a rational duration (in pulse units) to ns
     *
     * Called ONCE per event at schedule time, never repeatedly
     *
     * @param duration  Rational(2,5) for a half-note quintuplet slot
     * @return nanoseconds that duration lasts at current tempo
     */
    fun toNanos(duration: Rational): Long {
        val base = pulseNanos   // already accounts for pulseUnit + modulation
        return (base * duration.num / duration.den)
    }

    /**
     * Convert a tick offset from stream start to absolute nanoseconds
     * Used by the scheduler to compute event fire times
     */
    fun tickOffsetToNanos(ticks: Long): Long {
        // ticks / TICKS_PER_PULSE gives the duration in pulse units
        // then x pulseNanos to get wall clock
        return (pulseNanos.toDouble() * ticks / TICKS_PER_PULSE).toLong()
    }

    // new context

    /**
     * The ratio describes what happens to the pulse LENGTH (not BPM).
     * ratio < 1 -> shorter pulse
     * ratio > 1 -> longer pulse
     */
    fun withModulation(ratio: Rational): TempoContext =
        copy(modulation = (modulation * ratio).reduced)

    /**
     * Hard BPM override — resets modulation accumulator
     * Use for abrupt tempo changes, not smooth modulations
     */
    fun withBpm(newBpm: Double): TempoContext =
        copy(bpm = newBpm, modulation = Rational.ONE)

    // change pulse unit
    fun withPulseUnit(unit: Rational): TempoContext =
        copy(pulseUnit = unit)

    // debug
    override fun toString(): String =
        "TempoContext(bpm=$bpm, pulseUnit=$pulseUnit, mod=$modulation, " +
                "effectiveBpm=$effectiveBpm, pulseNanos=$pulseNanos)"
}

// presets; prob not used

object PulseUnit {
    /** Standard western: pulse = quarter note */
    // western quarter note = pulse
    val QUARTER    = Rational.ONE
    val DOTTED_QUARTER = Rational(3, 2)
    val HALF       = Rational(2, 1)
    val EIGHTH     = Rational(1, 2)

    // carnatic
    // pulse = 1 matra at chatusra nadai (4 / beat)
    val MATRA_CHATUSRA = Rational(1, 4)
    val MATRA_TISRA    = Rational(1, 3)
    val MATRA_KHANDA   = Rational(1, 5)
    val MATRA_MISRA    = Rational(1, 7)
    val MATRA_SANKIRNA = Rational(1, 9)
}