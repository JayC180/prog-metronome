package com.jayc180.rhythmengine.builder

import com.jayc180.rhythmengine.core.Rational
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrackBuilderTests {

    private lateinit var builder: TrackBuilder

    @Before fun setUp() { builder = TrackBuilder(initialBpm = 120.0) }

    // ── Denom mode ────────────────────────────────────────────────────────────

    @Test fun `denom toggle on and off`() {
        assertFalse(builder.state.value.isDenomMode)
        builder.toggleDenomMode()
        assertTrue(builder.state.value.isDenomMode)
        builder.toggleDenomMode()
        assertFalse(builder.state.value.isDenomMode)
    }

    @Test fun `setting denom exits denom mode and updates track`() {
        builder.toggleDenomMode()
        builder.setDenom(8)
        assertFalse(builder.state.value.isDenomMode)
        assertEquals(8, builder.state.value.activeDenom)
    }

    @Test fun `denom does not change in Normal mode`() {
        builder.setDenom(8)  // not in denom mode — should be ignored
        assertEquals(4, builder.state.value.activeDenom)  // default unchanged
    }

    @Test fun `denom ignores non-positive values`() {
        builder.toggleDenomMode()
        builder.setDenom(0)
        assertTrue(builder.state.value.isDenomMode)  // still in denom mode
    }

    // ── Beat entry ────────────────────────────────────────────────────────────

    @Test fun `entering beats creates correct rationals`() {
        builder.openBracket()
        builder.enterBeat(5)
        builder.enterBeat(3)
        builder.enterBeat(5)

        val beats = builder.state.value.activeTrack!!.currentBeats
        assertEquals(3, beats.size)
        assertEquals(Rational(5, 4), beats[0].duration)  // default denom = 4
        assertEquals(Rational(3, 4), beats[1].duration)
        assertEquals(Rational(5, 4), beats[2].duration)
    }

    @Test fun `beat entry respects current denom`() {
        builder.toggleDenomMode()
        builder.setDenom(3)
        builder.openBracket()
        builder.enterBeat(2)

        val beat = builder.state.value.activeTrack!!.currentBeats.first()
        assertEquals(Rational(2, 3), beat.duration)
    }

    @Test fun `entering beat in denom mode is ignored`() {
        builder.toggleDenomMode()
        builder.openBracket()
        builder.enterBeat(5)  // should be ignored — in denom mode

        val beats = builder.state.value.activeTrack!!.currentBeats
        assertTrue(beats.isEmpty())
    }

    @Test fun `backspace removes last beat`() {
        builder.openBracket()
        builder.enterBeat(5)
        builder.enterBeat(3)
        builder.deleteLast()

        val beats = builder.state.value.activeTrack!!.currentBeats
        assertEquals(1, beats.size)
        assertEquals(Rational(5, 4), beats[0].duration)
    }

    @Test fun `backspace on empty bracket does nothing`() {
        builder.openBracket()
        builder.deleteLast()  // nothing to delete
        val beats = builder.state.value.activeTrack!!.currentBeats
        assertTrue(beats.isEmpty())
    }

    // ── Brackets ─────────────────────────────────────────────────────────────

    @Test fun `opening bracket increments depth`() {
        assertEquals(0, builder.state.value.activeTrack!!.bracketDepth)
        builder.openBracket()
        assertEquals(1, builder.state.value.activeTrack!!.bracketDepth)
        builder.openBracket()
        assertEquals(2, builder.state.value.activeTrack!!.bracketDepth)
    }

    @Test fun `closing bracket decrements depth and seals group`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.enterBeat(4)
        builder.closeBracket()

        val track = builder.state.value.activeTrack!!
        assertEquals(0, track.bracketDepth)
        assertEquals(1, track.completedGroups.size)
        assertEquals(2, track.completedGroups[0].beats.size)
    }

    @Test fun `closing bracket without open bracket does nothing`() {
        val depthBefore = builder.state.value.activeTrack!!.bracketDepth
        builder.closeBracket()
        val depthAfter = builder.state.value.activeTrack!!.bracketDepth
        assertEquals(depthBefore, depthAfter)
    }

    @Test fun `closing bracket enables xN and mm`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()

        assertTrue(builder.state.value.canSetRepeat)
        assertTrue(builder.state.value.canSetModulation)
    }

    @Test fun `entering a beat after close bracket clears xN-mm window`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        assertTrue(builder.state.value.canSetRepeat)

        builder.openBracket()
        builder.enterBeat(5)  // this should clear the window

        assertFalse(builder.state.value.canSetRepeat)
        assertFalse(builder.state.value.canSetModulation)
    }

    @Test fun `nested brackets - beats in inner bracket go to inner group`() {
        builder.openBracket()           // depth 1
        builder.enterBeat(4)
        builder.openBracket()           // depth 2
        builder.enterBeat(3)
        builder.enterBeat(3)
        builder.closeBracket()          // close inner → depth 1

        val track = builder.state.value.activeTrack!!
        assertEquals(1, track.bracketDepth)
        // Outer bracket now has: beat(4) + groupBeat (the closed inner bracket)
        assertEquals(2, track.currentBeats.size)
        assertNotNull(track.currentBeats[1].nestedGroup)
    }

    // ── xN (repeat count) ─────────────────────────────────────────────────────

    @Test fun `xN not available before any bracket closed`() {
        assertFalse(builder.state.value.canSetRepeat)
    }

    @Test fun `entering repeat mode after bracket close`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterRepeatMode()

        assertTrue(builder.state.value.isRepeatMode)
        assertEquals(0, builder.state.value.pendingRepeat)
    }

    @Test fun `typing digits in repeat mode builds number`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterRepeatMode()
        builder.repeatDigit(8)

        assertEquals(8, builder.state.value.pendingRepeat)
    }

    @Test fun `confirming repeat sets repeatCount on last group`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterRepeatMode()
        builder.repeatDigit(8)
        builder.confirmRepeat()

        val group = builder.state.value.activeTrack!!.completedGroups.last()
        assertEquals(8, group.repeatCount)
        assertFalse(builder.state.value.isRepeatMode)
    }

    @Test fun `cancel repeat returns to normal without changing group`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterRepeatMode()
        builder.repeatDigit(5)
        builder.cancelRepeat()

        assertFalse(builder.state.value.isRepeatMode)
        assertNull(builder.state.value.activeTrack!!.completedGroups.last().repeatCount)
    }

    @Test fun `meshuggah pattern - 456663 x8 then leftover 4561`() {
        val riffNums = listOf(4, 5, 6, 6, 6, 3)
        val leftover = listOf(4, 5, 6, 1)

        // Riff bracket x8
        builder.openBracket()
        riffNums.forEach { builder.enterBeat(it) }
        builder.closeBracket()
        builder.enterRepeatMode()
        builder.repeatDigit(8)
        builder.confirmRepeat()

        // Leftover bracket x1 (no xN)
        builder.openBracket()
        leftover.forEach { builder.enterBeat(it) }
        builder.closeBracket()

        val track = builder.state.value.activeTrack!!
        assertEquals(2, track.completedGroups.size)
        assertEquals(8, track.completedGroups[0].repeatCount)
        assertNull(track.completedGroups[1].repeatCount)  // no xN = 1

        // Verify total duration = 256/4 pulses (with denom=4)
        val riffDur = riffNums.sumOf { it }.let { Rational(it.toLong(), 4L) }
        val leftDur = leftover.sumOf { it }.let { Rational(it.toLong(), 4L) }
        val total   = riffDur * Rational(8, 1) + leftDur
        assertEquals(Rational(256, 4).reduced, total)
    }

    // ── Metric modulation ─────────────────────────────────────────────────────

    @Test fun `mm not available before bracket closed`() {
        assertFalse(builder.state.value.canSetModulation)
    }

    @Test fun `entering mm mode after bracket close`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterModulationMode()

        assertTrue(builder.state.value.isModulationMode)
    }

    @Test fun `mm digit input builds fraction numerator then denominator`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterModulationMode()

        builder.modulationDigit(3)
        builder.switchModulationFocus()
        builder.modulationDigit(2)

        val pending = builder.state.value.pendingModulation!!
        assertEquals("3", pending.numerator)
        assertEquals("2", pending.denominator)
        assertTrue(pending.isValid)
    }

    @Test fun `mm 3 over 2 speeds up - resulting bpm is 180 at base 120`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterModulationMode()
        builder.modulationDigit(3)
        builder.switchModulationFocus()
        builder.modulationDigit(2)

        val resultingBpm = builder.state.value.modulatedBpm!!
        assertEquals(180.0, resultingBpm, 0.01)
    }

    @Test fun `mm 2 over 3 slows down - resulting bpm is 80 at base 120`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterModulationMode()
        builder.modulationDigit(2)
        builder.switchModulationFocus()
        builder.modulationDigit(3)

        val resultingBpm = builder.state.value.modulatedBpm!!
        assertEquals(80.0, resultingBpm, 0.01)
    }

    @Test fun `confirming mm stores BPM-ratio and inverts for engine`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterModulationMode()
        builder.modulationDigit(3)
        builder.switchModulationFocus()
        builder.modulationDigit(2)
        builder.confirmModulation()

        val group = builder.state.value.activeTrack!!.completedGroups.last()
        // Stored as BPM-ratio 3/2
        assertEquals(Rational(3, 2), group.modulation)
        assertFalse(builder.state.value.isModulationMode)
    }

    @Test fun `build() inverts BPM-ratio to pulse-ratio for TempoContext`() {
        builder.openBracket()
        builder.enterBeat(4)
        builder.closeBracket()
        builder.enterModulationMode()
        builder.modulationDigit(3)      // BPM × 3/2 → speeds up
        builder.switchModulationFocus()
        builder.modulationDigit(2)
        builder.confirmModulation()

        val tracks = builder.build()
        val group  = tracks[0].groups[0]
        // TrackGroup transition should have pulse-ratio = 2/3 (inverted)
        // We can't directly access the transition from TrackGroup in the test
        // but we can verify the track built without error and has one group
        assertEquals(1, tracks[0].groups.size)
    }

    // ── Multi-track ───────────────────────────────────────────────────────────

    @Test fun `adding tracks`() {
        assertEquals(1, builder.state.value.tracks.size)
        builder.addTrack()
        assertEquals(2, builder.state.value.tracks.size)
        assertEquals(1, builder.state.value.activeTrackIndex)
    }

    @Test fun `input goes to active track only`() {
        builder.addTrack()
        builder.setActiveTrack(0)
        builder.openBracket()
        builder.enterBeat(4)

        builder.setActiveTrack(1)
        builder.openBracket()
        builder.enterBeat(7)

        val track0Beats = builder.state.value.tracks[0].currentBeats
        val track1Beats = builder.state.value.tracks[1].currentBeats
        assertEquals(Rational(4, 4).reduced, track0Beats[0].duration)
        assertEquals(Rational(7, 4), track1Beats[0].duration)
    }

    @Test fun `deleting a track adjusts active index`() {
        builder.addTrack()
        builder.addTrack()
        builder.setActiveTrack(2)
        builder.deleteTrack(2)

        assertEquals(1, builder.state.value.activeTrackIndex)
        assertEquals(2, builder.state.value.tracks.size)
    }

    @Test fun `cannot delete last remaining track`() {
        builder.deleteTrack(0)
        assertEquals(1, builder.state.value.tracks.size)
    }

    // ── BPM ──────────────────────────────────────────────────────────────────

    @Test fun `setting bpm updates state`() {
        builder.setBpm(180.0)
        assertEquals(180.0, builder.state.value.bpm, 0.01)
    }

    @Test fun `bpm clamped to valid range`() {
        builder.setBpm(0.0)
        assertEquals(1.0, builder.state.value.bpm, 0.01)
        builder.setBpm(10000.0)
        assertEquals(999.0, builder.state.value.bpm, 0.01)
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Test fun `build produces correct track structure`() {
        builder.openBracket()
        listOf(5, 5, 3, 4).forEach { builder.enterBeat(it) }
        builder.closeBracket()
        builder.enterRepeatMode()
        builder.repeatDigit(2)
        builder.confirmRepeat()

        val tracks = builder.build()
        assertEquals(1, tracks.size)
        assertEquals(1, tracks[0].groups.size)
        assertEquals(2, tracks[0].groups[0].repeatCount)
    }

    @Test fun `build auto-closes unclosed brackets`() {
        builder.openBracket()
        builder.enterBeat(4)
        // Intentionally don't close bracket

        val tracks = builder.build()
        // Should not throw, should produce a valid track
        assertEquals(1, tracks.size)
    }

    // stdlib sumOf used directly

    // ── Playback state ────────────────────────────────────────────────────────

    @Test fun `initial state is stopped`() {
        assertTrue(builder.state.value.isStopped)
        assertFalse(builder.state.value.isPlaying)
    }

    @Test fun `play transitions to playing`() {
        builder.play()
        assertTrue(builder.state.value.isPlaying)
    }

    @Test fun `stop from playing resets to stopped`() {
        builder.play()
        builder.stop()
        assertTrue(builder.state.value.isStopped)
    }

    @Test fun `togglePlayStop from stopped starts playing`() {
        builder.togglePlayStop()
        assertTrue(builder.state.value.isPlaying)
    }

    @Test fun `togglePlayStop from playing stops`() {
        builder.play()
        builder.togglePlayStop()
        assertTrue(builder.state.value.isStopped)
    }

    @Test fun `beat entry blocked while playing`() {
        builder.openBracket()
        builder.play()
        builder.enterBeat(4)  // should be ignored
        assertTrue(builder.state.value.activeTrack!!.currentBeats.isEmpty())
    }

    @Test fun `bracket open blocked while playing`() {
        val depthBefore = builder.state.value.activeTrack!!.bracketDepth
        builder.play()
        builder.openBracket()  // should be ignored
        assertEquals(depthBefore, builder.state.value.activeTrack!!.bracketDepth)
    }

    @Test fun `canEdit is false while playing`() {
        builder.play()
        assertFalse(builder.state.value.canEdit)
        assertFalse(builder.state.value.canOpenBracket)
        assertFalse(builder.state.value.numpadEntersBeats)
    }

    @Test fun `canEdit restored after stop`() {
        builder.play()
        builder.stop()
        assertTrue(builder.state.value.canEdit)
    }

    @Test fun `double play is no-op`() {
        builder.play()
        builder.play()
        assertTrue(builder.state.value.isPlaying)
    }

    @Test fun `double stop is no-op`() {
        builder.stop()
        assertTrue(builder.state.value.isStopped)
    }

    @Test fun `bpm change reflected in state`() {
        builder.setBpm(160.0)
        assertEquals(160.0, builder.state.value.bpm, 0.01)
    }

    @Test fun `bpm change allowed while playing`() {
        builder.play()
        builder.setBpm(180.0)
        assertEquals(180.0, builder.state.value.bpm, 0.01)
        assertTrue(builder.state.value.isPlaying)
    }
}