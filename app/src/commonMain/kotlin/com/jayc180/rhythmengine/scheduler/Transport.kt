package com.jayc180.rhythmengine.scheduler

import com.jayc180.rhythmengine.model.*
import com.jayc180.rhythmengine.platform.nanoNow
import com.jayc180.rhythmengine.platform.nanosleep
import com.jayc180.rhythmengine.platform.startHighPriorityThread
import com.jayc180.rhythmengine.util.MinHeap
import com.jayc180.rhythmengine.util.SpinLock
import com.jayc180.rhythmengine.util.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.concurrent.Volatile

/**
 * All timing pre-computed by the interpreter
 * This class just coordinates StreamClocks and fires events at their pre-computed times
 */
class Transport(
    @Volatile private var soundMap: SoundMap = SoundMap(),
) {
    companion object {
        const val LOOKAHEAD_NANOS       = 150_000_000L
        const val SCHEDULER_INTERVAL_MS = 50L
        const val DISPATCHER_SPIN_NANOS = 500_000L
    }

    private var _clocks: List<StreamClock> = emptyList()

    private val eventQueue = MinHeap<ScheduledEvent>()
    private val queueLock  = SpinLock()

    private var originNanos:        Long = 0L
    private var scheduledUpToNanos: Long = 0L

    @Volatile private var isPlaying = false
    private var schedulerJob: Job? = null

    private val _firedEvents = MutableSharedFlow<ScheduledEvent>(extraBufferCapacity = 256)
    val firedEvents: SharedFlow<ScheduledEvent> = _firedEvents.asSharedFlow()

    var onAudioEvent: ((ScheduledEvent) -> Unit)? = null

    // control

    fun start(scope: CoroutineScope) {
        if (isPlaying) return
        originNanos        = nanoNow()
        scheduledUpToNanos = originNanos
        isPlaying          = true
        _clocks.forEach { it.reset() }

        schedulerJob = scope.launch(Dispatchers.Default) {
            while (isPlaying) {
                val horizon = nanoNow() + LOOKAHEAD_NANOS
                if (scheduledUpToNanos < horizon) {
                    fillQueueUntil(horizon)
                    scheduledUpToNanos = horizon
                }
                delay(SCHEDULER_INTERVAL_MS)
            }
        }
        startDispatcher()
    }

    fun stop() {
        isPlaying = false
        schedulerJob?.cancel()
        schedulerJob = null
        queueLock.withLock { eventQueue.clear() }
        _clocks.forEach { it.reset() }
    }

    fun updateTracks(results: List<InterpretResult>) {
        val wasPlaying = isPlaying
        if (wasPlaying) stop()
        _results = results
        _clocks  = results.map { StreamClock(it) }
    }

    fun updateSoundMap(newMap: SoundMap) { soundMap = newMap }

    // scheduler

    private fun fillQueueUntil(horizon: Long) {
        queueLock.withLock {
            _clocks.filter { it.isRunning }
                .forEach { it.fillUntil(horizon, originNanos, eventQueue) }
        }
    }

    private fun resultFor(trackId: String) = _results.firstOrNull { it.trackId == trackId }
    private var _results: List<InterpretResult> = emptyList()

    // dispatcher

    private fun startDispatcher() {
        startHighPriorityThread("rhythm-dispatcher") {
            while (isPlaying) {
                val now    = nanoNow()
                val toFire = mutableListOf<ScheduledEvent>()

                queueLock.withLock {
                    while (eventQueue.isNotEmpty() && eventQueue.peek()!!.absoluteNanos <= now) {
                        toFire += eventQueue.poll()!!
                    }
                }

                val anySoloed = _results.any { it.soloed }
                toFire.forEach { event ->
                    val result  = _results.firstOrNull { it.trackId == event.trackId }
                    val audible = when {
                        anySoloed -> result?.soloed == true
                        else      -> result?.muted != true
                    }
                    if (event.soundId != null && audible) {
                        onAudioEvent?.invoke(event)
                    }
                    _firedEvents.tryEmit(event)
                }
                nanosleep(DISPATCHER_SPIN_NANOS)
            }
        }
    }

    fun debugState(): String = buildString {
        appendLine("Transport(playing=$isPlaying, queued=${eventQueue.size})")
        _clocks.forEach { appendLine("  ${it.debugState()}") }
    }
}
