package com.jayc180.rhythmengine.builder

import com.jayc180.rhythmengine.scheduler.InterpretResult
import com.jayc180.rhythmengine.scheduler.PrecomputedEvent

// parse nodes
sealed class PNode {
    data class Beat(val item: TrackItem.Beat, val idx: Int) : PNode()
    data class Tempo(val item: TrackItem, val idx: Int)     : PNode()
    data class Group(
        val children:    List<PNode>,
        val repeatCount: Int,
        val modifiers:   List<TrackItem>,
        val openIdx:     Int,
    ) : PNode()
}

/**
 * Interpreter — flat TrackItem list → pre-computed nanosecond events
 *
 * Tempo semantics:
 *
 *   Inline tempo (mm/=bpm inside []):
 *     LOCAL to each pass of that bracket. Restored before each repeat pass,
 *     and after the group closes. Does not leak out.
 *
 *   Modifier tempo (mm/=bpm on ], e.g. ]×4 mm(3/2)):
 *     Applied ONCE after all passes of that group complete.
 *     PERSISTS outward — affects everything that follows.
 *     If the group repeats N times, the modifier applies N times cumulatively
 *     because each pass of an outer group re-runs this inner group (which
 *     applies its modifier each time it runs)
 *
 *   =bpm modifier: sets absolute tempo, so each pass resets to the same value —
 *   no compounding. This is the distinction between mm and =bpm.
 *
 *   Inline =bpm inside []: still local (restored each pass).
 *   Inline mm inside []: local (restored each pass). Use as modifier on ] for compound.
 */
fun interpretTrackDraft(
    draft:        TrackDraft,
    baseBpm:      Double,
    defaultSound: String = "default",
): InterpretResult {
    // parse
    data class Frame(val children: MutableList<PNode>, val openIdx: Int)
    val stack     = ArrayDeque<Frame>()
    val rootNodes = mutableListOf<PNode>()

    val muted:  Boolean = false
    val soloed: Boolean = false

    fun addNode(n: PNode) { if (stack.isEmpty()) rootNodes += n else stack.last().children += n }

    var i = 0
    while (i < draft.items.size) {
        when (val item = draft.items[i]) {
            is TrackItem.Beat ->
                addNode(PNode.Beat(item, i))
            is TrackItem.Modulation, is TrackItem.SetBpm ->
                addNode(PNode.Tempo(item, i))
            is TrackItem.BracketOpen ->
                stack.addLast(Frame(mutableListOf(), i))
            is TrackItem.BracketClose -> {
                if (stack.isEmpty()) { i++; continue }
                val frame = stack.removeLast()
                var repeatCount = 1
                val modifiers   = mutableListOf<TrackItem>()
                var j = i + 1
                if (j < draft.items.size && draft.items[j] is TrackItem.Repeat) {
                    repeatCount = (draft.items[j] as TrackItem.Repeat).count; j++
                }
                while (j < draft.items.size && (draft.items[j] is TrackItem.Modulation || draft.items[j] is TrackItem.SetBpm)) {
                    modifiers += draft.items[j]; j++
                }
                i = j - 1
                addNode(PNode.Group(frame.children.toList(), repeatCount, modifiers, frame.openIdx))
            }
            is TrackItem.Repeat, is TrackItem.Modulation, is TrackItem.SetBpm -> Unit
        }
        i++
    }
    while (stack.isNotEmpty()) {
        val frame = stack.removeLast()
        addNode(PNode.Group(frame.children.toList(), 1, emptyList(), frame.openIdx))
    }

    // compute
    // ComputeState
    class CS(var npp: Double, var cursor: Long, var fired: Int)

    val eventList = mutableListOf<PrecomputedEvent>()
    val tempoMap  = mutableListOf<Pair<Long, Double>>()

    fun bpmToNpp(bpm: Double) = 60_000_000_000.0 / bpm

    fun applyTempo(item: TrackItem, s: CS) {
        val newNpp = when (item) {
            is TrackItem.Modulation -> s.npp * item.q.toDouble() / item.p.toDouble()
            is TrackItem.SetBpm     -> bpmToNpp(item.bpm)
            else -> return
        }
        tempoMap += s.cursor to 60_000_000_000.0 / newNpp
        s.npp = newNpp
    }

    fun compute(node: PNode, s: CS): List<PrecomputedEvent> {
        val out = mutableListOf<PrecomputedEvent>()
        when (node) {
            is PNode.Beat -> {
                val dur = (s.npp * node.item.displayNum / node.item.displayDenom).toLong()
                out += PrecomputedEvent(
                    offsetNanos    = s.cursor,
                    soundId        = if (node.item.active) node.item.soundId ?: defaultSound else null,
                    trackItemIndex = node.idx,
                    firedCount     = s.fired,
                    volume         = node.item.volume,
                )
                s.cursor += dur
                s.fired++
            }

            is PNode.Tempo -> applyTempo(node.item, s)

            is PNode.Group -> {
                val passes = if (node.repeatCount == TrackItem.Repeat.INFINITE) 1
                else node.repeatCount.coerceAtLeast(1)

                // NO per-pass restore. Tempo threads forward continuously through
                // all passes. A modifier inside a child group will compound with
                // each subsequent pass of this outer group.
                repeat(passes) {
                    node.children.forEach { child -> out += compute(child, s) }
                }

                // Apply group's own modifiers after all passes; persist outward after
                node.modifiers.forEach { applyTempo(it, s) }
            }
        }
        return out
    }

    // prefix + infinite

    val hasInfLoop = rootNodes.lastOrNull()
        ?.let { it is PNode.Group && it.repeatCount == TrackItem.Repeat.INFINITE } == true

    val s = CS(bpmToNpp(baseBpm), 0L, 0)
    val prefixNodes = if (hasInfLoop) rootNodes.dropLast(1) else rootNodes
    prefixNodes.forEach { eventList += compute(it, s) }

    val loopNanos     = s.cursor
    val infPassEvents = mutableListOf<PrecomputedEvent>()
    var infPassNanos   = 0L

    if (hasInfLoop) {
        val infGroup = rootNodes.last() as PNode.Group
        val is2 = CS(s.npp, 0L, 0)
        infGroup.children.forEach { infPassEvents += compute(it, is2) }
        infGroup.modifiers.forEach { applyTempo(it, is2) }
        infPassNanos = is2.cursor
    }

    return InterpretResult(draft.id, eventList, loopNanos, infPassEvents, infPassNanos, tempoMap)
}