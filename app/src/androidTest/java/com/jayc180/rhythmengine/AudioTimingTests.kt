package com.jayc180.rhythmengine.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jayc180.rhythmengine.core.*
import com.jayc180.rhythmengine.model.*
import com.jayc180.rhythmengine.scheduler.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * On-device audio timing tests.
 * Run via: ./gradlew connectedAndroidTest --tests "*.AudioTimingTests"
 *
 * JUnit4 requires @Test methods to return void — never use `fun test() = runBlocking {}`.
 * Always use `fun test() { runBlocking {} }` so the return type is Unit/void.
 */
@RunWith(AndroidJUnit4::class)
class AudioTimingTests {

    private val context  = InstrumentationRegistry.getInstrumentation().targetContext
    private val audio    = AudioEngine()
    private val scope    = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val soundMap = SoundMap(listOf(
        SoundConfig("kick",  "perc_chair_lo"),
        SoundConfig("snare", "perc_castanet_hi"),
        SoundConfig("hat",   "perc_tamb_a_hi"),
        SoundConfig("riff",  "synth_block_c_lo"),
    ))

    @Before fun setUp() {
        assertTrue("Audio stream failed to open", audio.open())
        val failed = audio.loadSoundsFromRaw(context, soundMap)
        if (failed.isNotEmpty()) log("WARNING: sounds not loaded: $failed")
    }

    @After fun tearDown() {
        audio.stopAll()
        audio.close()
        scope.cancel()
    }

    // ── Test 1: Single track timing accuracy ──────────────────────────────────

    @Test fun test1_singleTrack_timingAccuracy() {
        runBlocking {
            log("=== TEST 1: Single track timing accuracy ===")

            val bpm = 120.0
            val expectedIntervalNanos = TempoContext(bpm).toNanos(Rational.ONE)
            val track = buildTrack("kick_track",
                notes = listOf("kick", "kick", "kick", "kick"),
                denom = 1, repeatCount = 1
            )
            val transport = buildTransport(listOf(track), bpm)
            val eventNanos = mutableListOf<Long>()

            transport.onAudioEvent = { event, config ->
                eventNanos += event.absoluteNanos
                config?.let { audio.trigger(event.soundId!!, it.volume, event.absoluteNanos) }
            }

            transport.start(scope)
            delay(3000)
            transport.stop()

            assertEquals("Expected 4 events", 4, eventNanos.size)
            val maxDriftMs = eventNanos.zipWithNext { a, b -> b - a }
                .maxOf { abs(it - expectedIntervalNanos) } / 1_000_000L
            log("Max interval drift: ${maxDriftMs}ms")
            assertTrue("Drift ${maxDriftMs}ms > 5ms", maxDriftMs <= 5)
            log("TEST 1: PASS")
        }
    }

    // ── Test 2: Polymeter — 3 + 4 beat tracks, no drops ─────────────────────

    @Test fun test2_twoTracks_polymeter_noDrops() {
        runBlocking {
            log("=== TEST 2: Polymeter — 3 + 4 beat tracks ===")

            val trackA = buildTrack("A", listOf("kick","kick","kick"), denom = 1, repeatCount = 4)
            val trackB = buildTrack("B", listOf("snare","snare","snare","snare"), denom = 1, repeatCount = 3)
            val transport = buildTransport(listOf(trackA, trackB), bpm = 120.0)
            val counts = mutableMapOf("A" to 0, "B" to 0)

            transport.onAudioEvent = { event, config ->
                counts[event.trackId] = (counts[event.trackId] ?: 0) + 1
                config?.let { audio.trigger(event.soundId!!, it.volume, event.absoluteNanos) }
            }

            transport.start(scope)
            delay(7000)
            transport.stop()

            log("Counts: $counts")
            assertEquals("Track A", 12, counts["A"])
            assertEquals("Track B", 12, counts["B"])
            log("TEST 2: PASS")
        }
    }

    // ── Test 3: Dense subdivision — 16th notes at 200 BPM ────────────────────

    @Test fun test3_denseSubdivision_noDrops() {
        runBlocking {
            log("=== TEST 3: Dense subdivision (1/4 pulse slots, bpm=200) ===")

            val bpm = 200.0; val denom = 4; val count = 32
            val notes = List(count) { if (it % denom == 0) "kick" else "hat" }
            val track = buildTrack("dense", notes, denom = denom, repeatCount = 1)
            val transport = buildTransport(listOf(track), bpm)
            var fired = 0
            val driftsUs = mutableListOf<Long>()

            transport.onAudioEvent = { event, config ->
                fired++
                // Skip first 2 events — cold-start JIT/thread-scheduling spike is
                // expected on Android and not representative of steady-state accuracy.
                if (fired > 2) {
                    driftsUs += abs(System.nanoTime() - event.absoluteNanos) / 1000L
                }
                config?.let { audio.trigger(event.soundId!!, it.volume, event.absoluteNanos) }
            }

            transport.start(scope)
            val waitMs = (count * TempoContext(bpm).toNanos(1 over denom) / 1_000_000L) + 500L
            delay(waitMs)
            transport.stop()

            val avgDrift = if (driftsUs.isEmpty()) 0L else driftsUs.average().toLong()
            val maxDrift = driftsUs.maxOrNull() ?: 0L
            log("Fired: $fired/$count, avgDrift: ${avgDrift}µs, max: ${maxDrift}µs (first 2 excluded)")
            assertEquals("All events fired", count, fired)
            assertTrue("Steady-state max drift ${maxDrift}µs > 10ms", maxDrift < 10_000)
            log("TEST 3: PASS")
        }
    }

    // ── Test 4: Simultaneous triggers — voice stacking ────────────────────────

    @Test fun test4_simultaneousTriggers_voiceStacking() {
        runBlocking {
            log("=== TEST 4: Simultaneous triggers ===")

            val trackA = buildTrack("A", listOf("kick"),  denom = 1, repeatCount = 1)
            val trackB = buildTrack("B", listOf("snare"), denom = 1, repeatCount = 1)
            val transport = buildTransport(listOf(trackA, trackB), bpm = 120.0)
            val fired = mutableListOf<Pair<String, Long>>()

            transport.onAudioEvent = { event, config ->
                fired += event.trackId to event.absoluteNanos
                config?.let { audio.trigger(event.soundId!!, it.volume, event.absoluteNanos) }
            }

            transport.start(scope)
            delay(1000)
            transport.stop()

            assertEquals("Both tracks fire", 2, fired.size)
            val deltaMs = abs(fired[0].second - fired[1].second) / 1_000_000L
            log("Simultaneous delta: ${deltaMs}ms")
            assertTrue("Events >2ms apart: ${deltaMs}ms", deltaMs <= 2)
            log("TEST 4: PASS")
        }
    }

    // ── Test 5: Meshuggah — both tracks end together ──────────────────────────

    @Test fun test5_meshuggah_tracksEndTogether() {
        runBlocking {
            log("=== TEST 5: Meshuggah structure ===")

            val track1 = Track(
                id = "drums",
                groups = listOf(TrackGroup(
                    BeatNode.Sequence(List(16) { BeatNode.Leaf(1 over 16, "kick") }),
                    repeatCount = 16
                )),
                endBehavior = TrackEndBehavior.Stop
            )
            val track2 = Track(
                id = "guitar",
                groups = listOf(
                    TrackGroup(
                        BeatNode.Sequence(listOf(4,5,6,6,6,3).map { BeatNode.Leaf(it over 16, "riff") }),
                        repeatCount = 8
                    ),
                    TrackGroup(
                        BeatNode.Sequence(listOf(4,5,6,1).map { BeatNode.Leaf(it over 16, "riff") }),
                        repeatCount = 1
                    )
                ),
                endBehavior = TrackEndBehavior.Stop
            )

            assertEquals(track1.totalDuration, track2.totalDuration)

            val transport = buildTransport(listOf(track1, track2), bpm = 240.0)
            var lastA = 0L; var lastB = 0L
            var countA = 0; var countB = 0

            transport.onAudioEvent = { event, config ->
                when (event.trackId) {
                    "drums"  -> { lastA = event.absoluteNanos; countA++ }
                    "guitar" -> { lastB = event.absoluteNanos; countB++ }
                }
                config?.let { audio.trigger(event.soundId!!, it.volume, event.absoluteNanos) }
            }

            transport.start(scope)
            delay(18_000)
            transport.stop()

            log("drums=$countA guitar=$countB")
            assertEquals("Drum count",   256, countA)
            assertEquals("Guitar count", 52,  countB)   // 8×6 + 4 = 52

            val endDeltaMs = abs(lastA - lastB) / 1_000_000L
            log("Track end delta: ${endDeltaMs}ms")
            assertTrue("Tracks ended ${endDeltaMs}ms apart (>10ms)", endDeltaMs <= 10)
            log("TEST 5: PASS")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildTrack(id: String, notes: List<String>, denom: Int, repeatCount: Int) =
        Track(
            id = id,
            groups = listOf(TrackGroup(
                BeatNode.Sequence(notes.map { BeatNode.Leaf(Rational(1L, denom.toLong()), it) }),
                repeatCount = repeatCount
            )),
            endBehavior = TrackEndBehavior.Stop
        )

    private fun buildTransport(tracks: List<Track>, bpm: Double) =
        Transport(tracks, TempoContext(bpm), soundMap)

    private fun log(msg: String) = android.util.Log.i("AudioTimingTests", msg)
}