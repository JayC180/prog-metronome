package com.jayc180.rhythmengine.core

/**
 * The rhythm tree. Every rhythmic structure in the engine is a BeatNode
 *
 * Three node types:
 *   Leaf     → a single indivisible slot
 *   Group    → N slots in the space of the group's duration (tuplet, polyrhythm, etc.)
 *   Sequence → an ordered list of nodes played end-to-end
 *
 * Design invariants:
 *
 * 1. Duration is always a Rational multiple of the pulse unit. No note names.
 *    No Western rhythmic vocabulary at this layer (or prob any layer).
 *
 * 2. A Leaf is either active (soundId != null) or silent (soundId == null).
 *    That is the only distinction the tree makes. Volume, sound, etc.,
 *    are all in SoundConfig, looked up by soundId at audio dispatch time.
 *    Irrelevant to tree.
 *
 * 3. Groups do NOT enforce that children sum to duration. Incomplete groups
 *    emit a ValidationWarning, never an error.
 *
 * 4. The tree is immutable. Edits produce new trees (do copy on write).
 *    The scheduler can safely read a tree while the UI builds a new draft.
 */
sealed class BeatNode {

    abstract val duration: Rational

    // leaf

    /**
     * A single indivisible slot.
     *
     * soundId = null  → silent (rest). Still occupies time
     * soundId = "x"   → trigger whatever mapped to "x" in SoundConfig
     *
     * The tree carries only the key, don't care about audio layer
     */
    data class Leaf(
        override val duration: Rational,
        val soundId: String? = null,
    ) : BeatNode() {
        val isRest: Boolean get() = soundId == null
        val isActive: Boolean get() = soundId != null

        companion object {
            fun rest(duration: Rational) = Leaf(duration)
            fun active(duration: Rational, soundId: String) = Leaf(duration, soundId)
        }

        override fun toString() = "Leaf(${soundId ?: "rest"}, $duration)"
    }

    // group

    /**
     * A tuplet / polyrhythm group: some slots inside a fixed total duration.
     *
     * [divisions] = intended slot count (for UI bracket label, slotDuration calc).
     * Does NOT constrain children count or their individual durations
     */
    data class Group(
        override val duration: Rational,
        val divisions: Int,
        val children: List<BeatNode>,
        val label: String? = null
    ) : BeatNode() {
        val slotDuration: Rational get() = duration / Rational(divisions.toLong(), 1L)
        val childrenDuration: Rational get() = children.map { it.duration }.rationalSum()
        val isComplete: Boolean get() = childrenDuration == duration
        val gap: Rational get() = duration - childrenDuration
        val displayLabel: String get() = label ?: "$divisions"

        override fun toString() = "Group(dur=$duration, div=$divisions, n=${children.size})"
    }

    // sequence

    /**
     * Ordered list of nodes played end-to-end
     * Total duration = sum of children
     */
    data class Sequence(val nodes: List<BeatNode>) : BeatNode() {
        override val duration: Rational get() = nodes.map { it.duration }.rationalSum()
        val isEmpty: Boolean get() = nodes.isEmpty()
        override fun toString() = "Sequence(dur=$duration, n=${nodes.size})"
    }

    // flatten

    /**
     * Flatten tree -> ordered List<FlatEvent> by time offset
     * Called once at group-load time. The scheduler only get FlatEvents
     * Silent slots are included but skipped by audio dispatcher
     */
    fun flatten(
        parentOffset: Rational = Rational.ZERO,
        into: MutableList<FlatEvent> = mutableListOf()
    ): List<FlatEvent> {
        when (this) {
            is Leaf -> into.add(FlatEvent(parentOffset, duration, soundId))
            is Group -> {
                var cursor = parentOffset
                children.forEach { child -> child.flatten(cursor, into); cursor += child.duration }
            }
            is Sequence -> {
                var cursor = parentOffset
                nodes.forEach { node -> node.flatten(cursor, into); cursor += node.duration }
            }
        }
        return into
    }

    fun validate(): List<ValidationWarning> = mutableListOf<ValidationWarning>().also { validateInto(it) }

    private fun validateInto(into: MutableList<ValidationWarning>) {
        when (this) {
            is Leaf -> Unit
            is Group -> {
                if (!isComplete) {
                    val verb = if (gap.isPositive()) "underfills" else "overfills"
                    into += ValidationWarning(this, "Group(div=$divisions) $verb by ${gap.reduced}",
                        if (gap.isNegative()) Severity.ERROR else Severity.WARNING)
                }
                children.forEach { it.validateInto(into) }
            }
            is Sequence -> {
                if (isEmpty) into += ValidationWarning(this, "Empty sequence", Severity.WARNING)
                nodes.forEach { it.validateInto(into) }
            }
        }
    }
}

/**
 * One schedulable slot — what the scheduler works with at runtime
 * soundId: null = silent, non-null = key -> SoundConfig
 */
data class FlatEvent(
    val offset: Rational,
    val duration: Rational,
    val soundId: String?,
) {
    val isRest: Boolean get() = soundId == null
    val isActive: Boolean get() = soundId != null
    val offsetTicks: Long get() = offset.toTicks()
    val durationTicks: Long get() = duration.toTicks()
}

enum class Severity { WARNING, ERROR }

data class ValidationWarning(
    val node: BeatNode,
    val message: String,
    val severity: Severity = Severity.WARNING
)

internal fun List<Rational>.rationalSum(): Rational = fold(Rational.ZERO, Rational::plus)