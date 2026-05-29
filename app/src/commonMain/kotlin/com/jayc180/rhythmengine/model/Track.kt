package com.jayc180.rhythmengine.model

import com.jayc180.rhythmengine.core.*

/**
 * A Track is one independent rhythmic voice
 *
 * Tracks share a BPM (set on Transport) but otherwise fully independent
 * User deal with all the hypermeter stuff and forms
 *
 * Structure:
 *   Track
 *    -> List<TrackGroup>   ([ ] that user places)
 *        -> BeatNode tree  (Sequence/Group/Leaf)
 *            -> FlatEvent  (pre-computed at group load time, used by scheduler)
 */
data class Track(
    val id: String,
    val groups: List<TrackGroup>,
    val endBehavior: TrackEndBehavior = TrackEndBehavior.Stop,
    val label: String = id
) {
    init {
        require(groups.isNotEmpty()) { "Track '$id' must have at least one group" }
    }

    val totalDuration: Rational get() = groups.map { it.totalDuration }.rationalSum()

    override fun toString() = "Track($id, groups=${groups.size})"
}

/**
 * A TrackGroup is the [ ] bracket - groupings
 *
 * Engine: play [root] exactly [repeatCount] times, continue
 *
 * [root] is BeatNode tree — rhythmic content inside the bracket
 * [id] is optional, used only for human-readable reference
 */
data class TrackGroup(
    val root: BeatNode,
    val repeatCount: Int = 1,
    val id: String? = null
) {
    init {
        require(repeatCount == -1 || repeatCount >= 1) { "repeatCount must be >= 1 or -1 (infinite)" }
    }

    val isInfinite: Boolean get() = repeatCount == -1

    // total duration of one pass thru root
    val singlePassDuration: Rational get() = root.duration

    // total duration including all repeats
    val totalDuration: Rational get() = if (isInfinite)
        singlePassDuration
    else
        root.duration * Rational(repeatCount.toLong(), 1L)

    /**
     * Pre-flattened events for one pass. Computed once lazily.
     * The scheduler uses this — it never walks the BeatNode tree at runtime.
     */
    val flatEvents: List<FlatEvent> by lazy { root.flatten() }
    val warnings: List<ValidationWarning> by lazy { root.validate() }
    val hasWarnings: Boolean get() = warnings.isNotEmpty()

    override fun toString() = "TrackGroup(dur=${singlePassDuration}×${if (isInfinite) "∞" else "$repeatCount"}, id=$id)"
}

/**
 * What a track does when all its groups are exhausted.
 * The engine only knows these three options
 */
sealed class TrackEndBehavior {
    // track stop
    object Stop : TrackEndBehavior() { override fun toString() = "Stop" }

    // restart from group 0; entire track loop
    object Loop : TrackEndBehavior() { override fun toString() = "Loop" }

    // jmp -> specific group index
    data class JumpTo(val groupIndex: Int) : TrackEndBehavior()
}

/**
 * The complete audio configuration for one sound.
 *
 * Entirely separate from the rhythm tree. BeatNode.Leaf holds a soundId (String key).
 * At audio dispatch time, the engine looks up that key in the active SoundMap to find
 * this config and know how to play it.
 *
 * Volume and velocity live here, not in the tree. The user configures these
 * per-sound, not per-beat. (Per-beat overrides can be added later as an optional
 * layer without touching the tree at all.)
 *
 * [resourceUri] is intentionally flexible
 */
data class SoundConfig(
    val soundId: String,
    val resourceUri: String,
    val volume: Float = 1.0f,       // 0.0 – 1.0
    val pitch: Float = 1.0f,        // playback speed multiplier (1.0 = original)
    val label: String = soundId     // display name in UI
)

/**
 * The active sound map: soundId -> SoundConfig
 * One per session / project. The audio dispatcher holds a reference to this
 */
class SoundMap(configs: List<SoundConfig> = emptyList()) {
    private val map: MutableMap<String, SoundConfig> = configs.associateBy { it.soundId }.toMutableMap()

    fun get(soundId: String): SoundConfig? = map[soundId]
    fun set(config: SoundConfig) { map[config.soundId] = config }
    fun remove(soundId: String) { map.remove(soundId) }
    fun all(): List<SoundConfig> = map.values.toList()
    operator fun contains(soundId: String) = soundId in map
}

private fun List<Rational>.rationalSum(): Rational = fold(Rational.ZERO, Rational::plus)