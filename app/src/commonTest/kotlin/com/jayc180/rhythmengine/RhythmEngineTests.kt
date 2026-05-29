package com.jayc180.rhythmengine

import com.jayc180.rhythmengine.core.*
import com.jayc180.rhythmengine.model.*
import com.jayc180.rhythmengine.scheduler.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class RhythmEngineTests {

    // ═══════════════════════════════════════════════════════════════════════
    // RATIONAL
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `rational reduces`() {
        assertEquals(Rational(3, 2), Rational(6, 4).reduced)
    }

    @Test fun `rational - three slots of denom 3 sum to 1`() {
        val slot = 1 over 3
        assertEquals(Rational.ONE, slot + slot + slot)
    }

    @Test fun `rational - five slots of denom 5 over 2 pulses`() {
        // user picks denom=5, types 2 → each slot = 2/5; five of them = 2
        val slot = 2 over 5
        var sum = Rational.ZERO
        repeat(5) { sum += slot }
        assertEquals(Rational(2, 1), sum)
    }

    @Test fun `rational - 7 against 4 sums correctly`() {
        val slot = 4 over 7
        var sum = Rational.ZERO
        repeat(7) { sum += slot }
        assertEquals(Rational(4, 1), sum)
    }

    @Test fun `rational - Meshuggah pattern 4+5+6+6+6+3 sums to 30`() {
        val pattern = listOf(4, 5, 6, 6, 6, 3).map { it over 1 }
        assertEquals(Rational(30, 1), pattern.fold(Rational.ZERO, Rational::plus))
    }

    @Test fun `rational - 8 reps of 30 plus leftover 16 equals 256`() {
        // User's pre-calculation: 8×30 + 16 = 256 = 16 bars of 16 sixteenth-notes
        val riff        = Rational(30, 1)
        val leftover    = Rational(16, 1)
        val total       = riff * Rational(8, 1) + leftover
        assertEquals(Rational(256, 1), total)
    }

    @Test fun `rational - denominator always positive after reduction`() {
        val r = Rational(3, -4).reduced
        assertTrue(r.den > 0)
        assertTrue(r.num < 0)
    }

    @Test fun `rational - tick conversion exact for common denominators`() {
        listOf(1 over 1, 1 over 2, 1 over 3, 1 over 4, 1 over 5,
            1 over 6, 1 over 7, 2 over 3, 4 over 5, 7 over 8).forEach { r ->
            val ticks = r.toTicks()
            assertEquals("Lost precision for $r", r.reduced, Rational(ticks, TICKS_PER_PULSE).reduced)
        }
    }

    @Test fun `rational - metric modulation accumulates`() {
        // Two successive 3/2 mods → 9/4
        val result = (3 over 2) * (3 over 2)
        assertEquals(Rational(9, 4), result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEMPO CONTEXT
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `tempo - 120 bpm pulse is 500ms`() {
        val ctx = TempoContext(120.0)
        assertTrue(abs(ctx.pulseNanos - 500_000_000L) < 1000)
    }

    @Test fun `tempo - modulation 2 over 3 from 120 gives effective 180`() {
        val ctx = TempoContext(120.0).withModulation(2 over 3)
        assertTrue(abs(ctx.effectiveBpm - 180.0) < 0.01)
    }

    @Test fun `tempo - slot of denom 5 at 120 bpm is 100ms`() {
        // user picks denom=5, slot = 1/5 pulse → 100ms at 120 BPM
        assertTrue(abs(TempoContext(120.0).toNanos(1 over 5) - 100_000_000L) < 1000)
    }

    @Test fun `tempo - dotted quarter pulse unit`() {
        val ctx = TempoContext(120.0, pulseUnit = PulseUnit.DOTTED_QUARTER)
        assertTrue(abs(ctx.pulseNanos - 750_000_000L) < 1000)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BEATNODE — no accent/velocity anywhere
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `leaf active and rest distinction`() {
        val active = BeatNode.Leaf.active(1 over 1, "kick")
        val rest   = BeatNode.Leaf.rest(1 over 2)
        assertTrue(active.isActive)
        assertFalse(active.isRest)
        assertTrue(rest.isRest)
        assertNull(rest.soundId)
    }

    @Test fun `sequence duration is sum of children`() {
        val seq = BeatNode.Sequence(listOf(
            BeatNode.Leaf(1 over 1, "kick"),
            BeatNode.Leaf(1 over 2, "snare"),
            BeatNode.Leaf(1 over 4, "hat"),
        ))
        assertEquals(Rational(7, 4), seq.duration)
    }

    @Test fun `flatten - simple sequence offsets`() {
        val seq = BeatNode.Sequence(listOf(
            BeatNode.Leaf(1 over 1, "kick"),
            BeatNode.Leaf(1 over 2, "snare"),
            BeatNode.Leaf(1 over 4, "hat"),
        ))
        val events = seq.flatten()
        assertEquals(3, events.size)
        assertEquals(Rational.ZERO,  events[0].offset)
        assertEquals(Rational(1, 1), events[1].offset)
        assertEquals(Rational(3, 2), events[2].offset)
    }

    @Test fun `flatten - rest included with null soundId`() {
        val seq = BeatNode.Sequence(listOf(
            BeatNode.Leaf(1 over 1, "kick"),
            BeatNode.Leaf.rest(1 over 2),
            BeatNode.Leaf(1 over 2, "snare"),
        ))
        val events = seq.flatten()
        assertEquals(3, events.size)
        assertNull(events[1].soundId)
        assertTrue(events[1].isRest)
        assertEquals(Rational(3, 2), events[2].offset)
    }

    @Test fun `flatten - user input 5+5+3+5+5+4+5 in denom 4`() {
        // User: pick denom=4, type 5,5,3,5,5,4,5 → each value n becomes n/4 pulse
        // Total = 32/4 = 8 pulses
        val values = listOf(5, 5, 3, 5, 5, 4, 5)
        val nodes  = values.map { BeatNode.Leaf(Rational(it.toLong(), 4L), "hit") }
        val seq    = BeatNode.Sequence(nodes)

        assertEquals(Rational(32, 4).reduced, seq.duration)   // = 8 pulses
        val events = seq.flatten()
        assertEquals(7, events.size)
        assertEquals(Rational.ZERO,           events[0].offset)
        assertEquals(Rational(5, 4),          events[1].offset)
        assertEquals(Rational(10, 4).reduced, events[2].offset)
    }

    @Test fun `flatten - partial quintuplet (4 of 5 slots)`() {
        val seq = BeatNode.Sequence(listOf(
            BeatNode.Leaf(1 over 1, "kick"),
            BeatNode.Leaf(1 over 1, "snare"),
            BeatNode.Group(
                duration  = 2 over 1,
                divisions = 5,
                children  = listOf(
                    BeatNode.Leaf(2 over 5, "hat"),
                    BeatNode.Leaf(2 over 5, "hat"),
                    BeatNode.Leaf(2 over 5, "hat"),
                    BeatNode.Leaf(2 over 5, "hat"),
                    // slot 5 absent — implicit silence
                )
            )
        ))
        val events = seq.flatten()
        assertEquals(6, events.size)                   // kick + snare + 4 hats
        assertEquals("hat", events[2].soundId)
        assertEquals(Rational(2, 1),  events[2].offset)
        assertEquals(Rational(12, 5), events[3].offset)
        assertEquals(Rational(4, 1),  seq.duration)   // 1+1+2 = 4 pulses total
    }

    @Test fun `flatten - nested group (Carnatic style 3 inside 5)`() {
        val nested = BeatNode.Group(
            duration  = 1 over 1,
            divisions = 5,
            children  = listOf(
                BeatNode.Leaf(1 over 5, "a"),
                BeatNode.Leaf(1 over 5, "b"),
                BeatNode.Group(
                    duration  = 1 over 5,
                    divisions = 3,
                    children  = listOf(
                        BeatNode.Leaf(1 over 15, "c1"),
                        BeatNode.Leaf(1 over 15, "c2"),
                        BeatNode.Leaf(1 over 15, "c3"),
                    )
                ),
                BeatNode.Leaf(1 over 5, "d"),
                BeatNode.Leaf(1 over 5, "e"),
            )
        )
        val events = nested.flatten()
        assertEquals(7, events.size)
        assertEquals("c1", events[2].soundId)
        assertEquals(Rational(2, 5),  events[2].offset)
        assertEquals(Rational(7, 15), events[3].offset)
        assertEquals(Rational(8, 15), events[4].offset)
    }

    @Test fun `validate - complete group no warnings`() {
        val group = BeatNode.Group(1 over 1, 3, listOf(
            BeatNode.Leaf(1 over 3, "a"),
            BeatNode.Leaf(1 over 3, "b"),
            BeatNode.Leaf(1 over 3, "c"),
        ))
        assertTrue(group.validate().isEmpty())
    }

    @Test fun `validate - partial group emits warning`() {
        val group = BeatNode.Group(1 over 1, 5, listOf(
            BeatNode.Leaf(1 over 5, "a"),
            BeatNode.Leaf(1 over 5, "b"),
        ))
        val w = group.validate()
        assertEquals(1, w.size)
        assertEquals(Severity.WARNING, w[0].severity)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TRACK / TRACKGROUP
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `trackgroup totalDuration includes repeats`() {
        val group = TrackGroup(
            root        = BeatNode.Sequence(listOf(BeatNode.Leaf(1 over 1, "x"))),
            repeatCount = 4
        )
        assertEquals(Rational(4, 1), group.totalDuration)
    }

    @Test fun `track totalDuration sums all groups`() {
        val track = Track(
            id     = "t",
            groups = listOf(
                TrackGroup(BeatNode.Sequence(listOf(BeatNode.Leaf(4 over 1, "a"))), repeatCount = 4),
                TrackGroup(BeatNode.Sequence(listOf(BeatNode.Leaf(1 over 1, "b"))), repeatCount = 1),
            )
        )
        // 4×4 + 1×1 = 17
        assertEquals(Rational(17, 1), track.totalDuration)
    }

    @Test fun `soundconfig lookup - found and missing`() {
        val map = SoundMap(listOf(
            SoundConfig("kick", "assets/kick.wav", volume = 0.9f),
            SoundConfig("snare", "assets/snare.wav"),
        ))
        assertNotNull(map.get("kick"))
        assertEquals("assets/kick.wav", map.get("kick")!!.resourceUri)
        assertNull(map.get("hat"))
        assertTrue("kick" in map)
        assertFalse("hat" in map)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POLYMETER — engine-level correctness
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun `polymeter - 3 and 4 pulse tracks realign at 12`() {
        val track3 = loopedOffsets(
            BeatNode.Sequence((1..3).map { BeatNode.Leaf(1 over 1, "a") }), cycles = 4)
        val track4 = loopedOffsets(
            BeatNode.Sequence((1..4).map { BeatNode.Leaf(1 over 1, "b") }), cycles = 3)

        assertTrue(track3.contains(Rational(12, 1)))
        assertTrue(track4.contains(Rational(12, 1)))
    }

    @Test fun `polymeter - Meshuggah 256 sixteenth-note structure`() {
        // Track 1: 16 sixteenth-notes × 16 bars = 256 sixteenth-notes
        // Track 2: [4+5+6+6+6+3] × 8 = 240, + leftover [4+5+6+1] = 16 → total 256
        // User pre-calculates this. Engine just plays both tracks. They end together.

        val t1Duration = Rational(256, 1)

        val riffNotes   = listOf(4, 5, 6, 6, 6, 3).map { BeatNode.Leaf(it over 1, "riff") }
        val riff        = BeatNode.Sequence(riffNotes)
        val leftover    = BeatNode.Sequence(listOf(4, 5, 6, 1).map { BeatNode.Leaf(it over 1, "riff") })

        val t2 = Track(
            id     = "guitar",
            groups = listOf(
                TrackGroup(riff, repeatCount = 8),
                TrackGroup(leftover, repeatCount = 1)
            )
        )

        assertEquals(t1Duration, t2.totalDuration)
    }

    @Test fun `polymeter - 5 against 7 realign at 35`() {
        val t5 = loopedOffsets(BeatNode.Sequence((1..5).map { BeatNode.Leaf(1 over 1, "x") }), cycles = 7)
        val t7 = loopedOffsets(BeatNode.Sequence((1..7).map { BeatNode.Leaf(1 over 1, "y") }), cycles = 5)
        assertTrue(t5.contains(Rational(35, 1)))
        assertTrue(t7.contains(Rational(35, 1)))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private fun loopedOffsets(pattern: BeatNode, cycles: Int): Set<Rational> {
        val flat   = pattern.flatten()
        val cycLen = pattern.duration
        val result = mutableSetOf<Rational>()
        for (c in 0 until cycles) {
            val base = cycLen * Rational(c.toLong(), 1L)
            flat.forEach { result += it.offset + base }
        }
        result += cycLen * Rational(cycles.toLong(), 1L)   // final boundary
        return result
    }
}